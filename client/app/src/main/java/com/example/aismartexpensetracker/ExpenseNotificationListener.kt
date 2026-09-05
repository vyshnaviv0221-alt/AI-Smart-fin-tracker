package com.example.aismartexpensetracker

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.aismartexpensetracker.cloud.CloudResult
import com.example.aismartexpensetracker.cloud.SessionStore
import com.example.aismartexpensetracker.cloud.SupabaseClient
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
            // Messaging apps (bank SMS alerts)
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            // Bank apps
            "com.snapwork.hdfc",                       // HDFC
            "com.csam.icici.bank.imobile",             // ICICI iMobile
            "com.sbi.lotusintouch",                    // SBI YONO
            "com.sbi.SBIFreedomPlus",
            "com.msf.kbank.mobile",                    // Kotak
            "com.axis.mobile",                         // Axis
            "com.bankofbaroda.mconnect"
        )
    }

    /**
     * Scoped to the service rather than GlobalScope, so in-flight work is
     * cancelled when Android tears the listener down.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionStore by lazy { SessionStore(applicationContext) }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName ?: return
        if (packageName !in PAYMENT_APP_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title").orEmpty()
        // Long bank SMS get truncated in android.text; bigText has the full body.
        val body = extras.getString("android.bigText")
            ?: extras.getString("android.text").orEmpty()
        val fullText = "$title $body"

        // 1. Text extraction & parsing (regex / rule-based)
        val parsed = ExpenseParser.parse(fullText) ?: return

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
        serviceScope.launch {
            // Deduplication is ON here: banks and UPI apps genuinely re-post
            // the same alert, and Android re-delivers notifications on update.
            val result = ExpenseRepository.captureExpense(
                dao = dao,
                merchant = parsed.merchant,
                amount = parsed.amount,
                deduplicate = true
            )
            if (result !is CaptureResult.Saved) return@launch

            // 3. Best-effort cloud sync. Never allowed to affect local capture.
            syncToCloud(dao, result.id)
        }
    }

    private suspend fun syncToCloud(dao: ExpenseDao, expenseId: Int) {
        if (!sessionStore.isSignedIn) {
            Log.i(TAG, "Not signed in; transaction saved locally only.")
            return
        }
        try {
            // Read back so the row carries the enriched category / anomaly flag.
            val saved = dao.findById(expenseId) ?: return
            when (val result = SupabaseClient.syncExpenses(sessionStore, listOf(saved))) {
                is CloudResult.Ok -> Log.d(TAG, "Synced to Supabase")
                is CloudResult.Failed -> Log.w(TAG, "Supabase sync failed: ${result.message}")
                CloudResult.NotConfigured -> Log.i(TAG, "Supabase not configured; local only.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync error; transaction is saved locally", e)
        }
    }
}
