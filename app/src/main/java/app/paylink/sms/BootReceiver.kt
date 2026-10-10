package app.paylink.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// After a reboot, bring the notification back if forwarding was left ON.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.loggedIn(context) && Prefs.active(context)) {
            ForwardService.start(context)
        }
    }
}
