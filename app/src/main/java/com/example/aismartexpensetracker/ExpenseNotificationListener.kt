package com.example.aismartexpensetracker

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseNotificationListener : NotificationListenerService() {

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    // Add the package names of the bank/UPI apps
    // that you want your application to process.
    private val allowedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                        // PhonePe
        "net.one97.paytm",                        // Paytm
        "com.google.android.apps.walletnfcrel"    // Google Wallet
    )

    override fun onNotificationPosted(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return

        val packageName = sbn.packageName

        // 1. Filter notification source
        if (packageName !in allowedPackages) {
            Log.d(
                "ExpenseListener",
                "Ignored notification from: $packageName"
            )
            return
        }

        val extras = sbn.notification?.extras ?: return

        val title =
            extras.getString("android.title") ?: ""

        val text =
            extras.getCharSequence("android.text")
                ?.toString() ?: ""

        val fullText = "$title $text"

        Log.d(
            "ExpenseListener",
            "Notification received: $fullText"
        )

        // 2. Parse transaction notification
        val parsedExpense =
            ExpenseParser.parse(fullText)

        if (parsedExpense == null) {
            Log.d(
                "ExpenseListener",
                "Not a valid transaction notification"
            )
            return
        }

        // 3. Check transaction type
        if (parsedExpense.type != "debit") {
            Log.d(
                "ExpenseListener",
                "Ignored non-expense transaction: ${parsedExpense.type}"
            )
            return
        }

        Log.d(
            "ExpenseListener",
            "Expense detected"
        )

        Log.d(
            "ExpenseListener",
            "Amount: ${parsedExpense.amount}"
        )

        Log.d(
            "ExpenseListener",
            "Merchant: ${parsedExpense.merchant}"
        )

        Log.d(
            "ExpenseListener",
            "Type: ${parsedExpense.type}"
        )

        // 4. Current notification time
        val currentTime =
            System.currentTimeMillis()

        // 5. Save transaction to Room
        val database =
            AppDatabase.getDatabase(applicationContext)

        val roomExpense = Expense(
            amount = parsedExpense.amount.toString(),
            merchant = parsedExpense.merchant,
            date = currentTime
        )

        CoroutineScope(Dispatchers.IO).launch {

            try {

                database.expenseDao()
                    .insertExpense(roomExpense)

                Log.d(
                    "ExpenseListener",
                    "Saved to Room Database"
                )

            } catch (e: Exception) {

                Log.e(
                    "ExpenseListener",
                    "Room database error",
                    e
                )
            }
        }

        // 6. Save to Firestore if user is logged in
        val currentUser =
            auth.currentUser

        if (currentUser != null) {

            val firestoreData =
                hashMapOf(
                    "userId" to currentUser.uid,
                    "amount" to parsedExpense.amount,
                    "merchant" to parsedExpense.merchant,
                    "type" to parsedExpense.type,
                    "date" to currentTime,
                    "rawText" to parsedExpense.rawText
                )

            firestore
                .collection("users")
                .document(currentUser.uid)
                .collection("expenses")
                .add(firestoreData)
                .addOnSuccessListener {

                    Log.d(
                        "ExpenseListener",
                        "Transaction synced to Firestore"
                    )
                }
                .addOnFailureListener { e ->

                    Log.e(
                        "ExpenseListener",
                        "Firestore error",
                        e
                    )
                }

        } else {

            Log.d(
                "ExpenseListener",
                "User not logged in. Saved locally."
            )
        }
    }
}