package app.paylink.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    // Only mobile-money senders are forwarded; every other SMS stays on the phone.
    private val allowed = listOf("bkash", "nagad", "rocket", "upay", "16216")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.loggedIn(context) || !Prefs.active(context)) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts[0].originatingAddress ?: return
        if (allowed.none { sender.lowercase().contains(it) }) return

        val body = parts.joinToString("") { it.messageBody ?: "" }
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf("sender" to sender, "body" to body, "receivedAt" to System.currentTimeMillis()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }
}
