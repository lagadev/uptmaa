package app.paylink.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Runs while forwarding is ON. Its only job is to show the persistent
 * notification (with a "বন্ধ করুন" action) and keep the app alive on phones
 * that aggressively kill background apps. SMS themselves arrive via SmsReceiver.
 */
class ForwardService : Service() {

    companion object {
        const val ACTION_STOP = "app.paylink.sms.STOP"
        const val ACTION_STATE_CHANGED = "app.paylink.sms.STATE_CHANGED"
        private const val CHANNEL_ID = "uglypay_running"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            Prefs.setActive(ctx, true)
            ContextCompat.startForegroundService(ctx, Intent(ctx, ForwardService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs.setActive(ctx, false)
            ctx.stopService(Intent(ctx, ForwardService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setActive(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
            return START_NOT_STICKY
        }
        if (!Prefs.loggedIn(this)) { stopSelf(); return START_NOT_STICKY }

        Prefs.setActive(this, true)
        ensureChannel()
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "চালু থাকার নোটিফিকেশন", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "SMS ফরওয়ার্ডিং চালু থাকলে দেখায়, এখান থেকেই বন্ধ করা যায়।"; setShowBadge(false) }
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ForwardService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF3F6BFF.toInt())
            .setContentTitle("UglyPay SMS চালু আছে")
            .setContentText("পেমেন্ট SMS শোনা হচ্ছে")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "বন্ধ করুন", stop)
            .build()
    }
}
