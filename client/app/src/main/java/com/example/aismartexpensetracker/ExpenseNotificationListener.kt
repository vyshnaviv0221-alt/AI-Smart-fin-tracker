package com.example.aismartexpensetracker

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExpenseNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpenseListener"

        /**
         * Only notifications from these apps are parsed.
         *
         * Without this filter every notification on the device is scanned, so a
         * chat message containing "₹500" becomes a logged expense. Section 4.1
         * of the implementation plan requires this allowlist.
         *
         * Messaging apps are included deliberately: in India most bank alerts
         * arrive as SMS and surface through the default Messages app rather
         * than a bank app. ExpenseParser requires a transaction verb, which is
         * what keeps ordinary SMS out.
         */
        val PAYMENT_APP_PACKAGES = setOf(
            // UPI apps
            "com.google.android.apps.nbu.paisa.user",  // Google Pay (India)
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",                      // BHIM
            "in.amazon.mShop.android.shopping",        // Amazon Pay
            "com.dreamplug.androidapp",                // CRED
            "com.mobikwik_new",
            "com.freecharge.android",
            // Messaging apps (bank SMS alerts). Every OEM ships its own, and a
            // missing one means the phone captures nothing at all while looking
            // perfectly healthy -- the failure is invisible, so the list is
            // deliberately generous.
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",                         // AOSP, and MIUI
            "com.miui.smsextra",                       // MIUI smart-SMS layer
            "com.xiaomi.smsextra",
            "com.oneplus.mms",
            "com.oppo.mms",
            "com.coloros.mms",                         // ColorOS (Oppo/Realme)
            "com.vivo.mms",
            "com.android.messaging",
            "com.truecaller",                          // used as an SMS app by many
            // Bank apps
            "com.snapwork.hdfc",                       // HDFC
            "com.csam.icici.bank.imobile",             // ICICI iMobile
            "com.sbi.lotusintouch",                    // SBI YONO
            "com.sbi.SBIFreedomPlus",
            "com.msf.kbank.mobile",                    // Kotak
            "com.axis.mobile",                         // Axis
            "com.bankofbaroda.mconnect",
            "com.pnb.one",                             // Punjab National Bank
            "com.canarabank.mobility",
            "com.unionbank.ebanking",
            "com.idfcfirstbank.optimus",
            "com.indusind.indie",
            "com.fss.jsbpsp",                          // Federal / others
            "com.yesbank",
            "com.rblbank.mobank",
            "com.aubank.aumobile",
            "com.bankofindia.boiapp",
            "com.infrasofttech.CentralBank",
            "com.iexceed.ipsp.idbi"
        )
    }

    /**
     * Scoped to the service rather than GlobalScope, so in-flight work is
     * cancelled when Android tears the listener down.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName ?: return
        if (packageName !in PAYMENT_APP_PACKAGES) {
            // Logged, not silent. When capture "does nothing" on a device the
            // first question is always which app actually posted the alert, and
            // without this line there is no way to answer it except guessing.
            Log.v(TAG, "Ignoring notification from $packageName (not in allowlist)")
            return
        }

        val extras = sbn.notification?.extras ?: return

        // getCharSequence, NOT getString.
        //
        // EXTRA_TITLE and EXTRA_TEXT are declared as CharSequence, and many
        // apps store a SpannableString to carry styling. Bundle.getString()
        // returns null for those rather than converting, so on any device
        // whose messaging app styles its notifications the title and body both
        // came back empty, the parser saw whitespace, and the transaction was
        // dropped without a trace. It worked on one phone and not another for
        // no reason the user could see.
        fun text(key: String): String = extras.getCharSequence(key)?.toString().orEmpty()

        val title = text(Notification.EXTRA_TITLE)
        // Long bank SMS get truncated in EXTRA_TEXT; EXTRA_BIG_TEXT has it all.
        // Inbox-style notifications put each message in EXTRA_TEXT_LINES and may
        // leave the others empty.
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }
            .orEmpty()
        val body = listOf(text(Notification.EXTRA_BIG_TEXT), text(Notification.EXTRA_TEXT), lines)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val fullText = "$title $body"
        if (fullText.isBlank()) {
            Log.w(TAG, "Empty notification text from $packageName; nothing to parse")
            return
        }

        // 1. Text extraction & parsing (regex / rule-based)
        val parsed = ExpenseParser.parse(fullText)
        if (parsed == null) {
            // A payment app posts plenty that is not a transaction. Logged at
            // debug so a genuine miss can be told apart from a filtered one.
            Log.d(TAG, "No transaction found in notification from $packageName")
            return
        }

        // This is an expense tracker: money going out. Credits (salary, refunds)
        // are recognised by the parser but not stored as expenses, otherwise a
        // salary credit would inflate the spending total.
        if (parsed.type != "debit") {
            Log.d(TAG, "Ignoring ${parsed.type} of ${parsed.amount} from $packageName")
            return
        }

        Log.d(TAG, "Detected expense: ${parsed.amount} at ${parsed.merchant} (from $packageName)")

        // 2. Save locally and enrich, exactly as the manual "+" button does.
        val dao = AppDatabase.getDatabase(applicationContext).expenseDao()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        serviceScope.launch {
            // Deduplication is ON here: banks and UPI apps genuinely re-post
            // the same alert, and Android re-delivers notifications on update.
            ExpenseRepository.captureExpense(
                dao = dao,
                merchant = parsed.merchant,
                amount = parsed.amount,
                deduplicate = true,
                userId = userId
            )
        }
    }
}
