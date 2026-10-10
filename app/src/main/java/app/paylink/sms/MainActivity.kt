package app.paylink.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var loginCard: View
    private lateinit var dashCard: View
    private lateinit var tokenInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var msg: TextView
    private lateinit var dot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var mainBtn: Button
    private lateinit var chipSms: TextView
    private lateinit var chipNotif: TextView
    private lateinit var logoutBtn: TextView

    // Refreshes the screen when the service is stopped from the notification.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loginCard = findViewById(R.id.loginCard)
        dashCard = findViewById(R.id.dashCard)
        tokenInput = findViewById(R.id.token)
        loginBtn = findViewById(R.id.loginBtn)
        msg = findViewById(R.id.msg)
        dot = findViewById(R.id.dot)
        statusTitle = findViewById(R.id.statusTitle)
        statusSub = findViewById(R.id.statusSub)
        mainBtn = findViewById(R.id.mainBtn)
        chipSms = findViewById(R.id.chipSms)
        chipNotif = findViewById(R.id.chipNotif)
        logoutBtn = findViewById(R.id.logoutBtn)

        loginBtn.setOnClickListener { login() }
        mainBtn.setOnClickListener { onMainButton() }
        logoutBtn.setOnClickListener {
            ForwardService.stop(this)
            Prefs.clear(this)
            tokenInput.setText("")
            msg.visibility = View.GONE
            render()
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, stateReceiver, IntentFilter(ForwardService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---- permissions ------------------------------------------------------
    private fun hasSms() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    private fun hasNotif() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val need = mutableListOf<String>()
        if (!hasSms()) need += Manifest.permission.RECEIVE_SMS
        if (!hasNotif()) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
        else startIfWanted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startIfWanted()
        render()
    }

    private fun startIfWanted() {
        if (Prefs.loggedIn(this) && Prefs.active(this) && hasSms()) ForwardService.start(this)
    }

    // ---- login: verify the key with the server, save only if valid ---------
    private fun login() {
        val key = tokenInput.text.toString().trim()
        if (key.isEmpty()) { showError("SMS Key দিন।"); return }
        loginBtn.isEnabled = false
        loginBtn.text = "যাচাই হচ্ছে…"
        msg.visibility = View.GONE
        thread {
            val result = try {
                val c = URL("${Config.SERVER_URL}/api/sms/ingest").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer $key")
                // Not a payment SMS, so the server ignores it — a 200 just proves the key is valid.
                c.outputStream.use { it.write("""{"sender":"test","body":"login check"}""".toByteArray()) }
                when (c.responseCode) {
                    200 -> "ok"
                    401 -> "SMS Key ভুল। Mini App থেকে আবার কপি করুন।"
                    else -> "সার্ভার সাড়া দিয়েছে: ${c.responseCode}"
                }
            } catch (e: Exception) { "কানেক্ট করা যায়নি। ইন্টারনেট চেক করুন।" }

            runOnUiThread {
                loginBtn.isEnabled = true
                loginBtn.text = "লগইন"
                if (result == "ok") {
                    Prefs.save(this, key)
                    Prefs.setActive(this, true)
                    askPermissions()
                    render()
                } else showError(result)
            }
        }
    }

    private fun showError(text: String) { msg.text = text; msg.visibility = View.VISIBLE }

    // ---- main Start / Stop button -------------------------------------------
    private fun onMainButton() {
        if (!hasSms()) { Prefs.setActive(this, true); askPermissions(); render(); return }
        if (Prefs.active(this)) ForwardService.stop(this) else ForwardService.start(this)
        render()
    }

    // ---- UI state -------------------------------------------------------------
    private fun render() {
        val loggedIn = Prefs.loggedIn(this)
        loginCard.visibility = if (loggedIn) View.GONE else View.VISIBLE
        dashCard.visibility = if (loggedIn) View.VISIBLE else View.GONE
        logoutBtn.visibility = if (loggedIn) View.VISIBLE else View.GONE
        if (!loggedIn) return

        val sms = hasSms(); val notif = hasNotif(); val active = Prefs.active(this)
        when {
            !sms -> {
                setDot(R.color.warn); statusTitle.text = "পারমিশন বাকি"
                statusSub.text = "পেমেন্ট SMS পড়ার অনুমতি দিন। Android ১৩+ হলে App info → ⋮ → Allow restricted settings দিতে হতে পারে।"
                mainBtn.text = "পারমিশন দিন"; mainBtn.setBackgroundResource(R.drawable.bg_btn_primary)
            }
            active -> {
                setDot(R.color.good); statusTitle.text = "চালু আছে"
                statusSub.text = "পেমেন্ট SMS আসলে অটো সার্ভারে পাঠানো হচ্ছে। স্ট্যাটাস বারের নোটিফিকেশন থেকেও বন্ধ করা যাবে।"
                mainBtn.text = "বন্ধ করুন"; mainBtn.setBackgroundResource(R.drawable.bg_btn_stop)
            }
            else -> {
                setDot(R.color.idle); statusTitle.text = "বন্ধ আছে"
                statusSub.text = "এখন কোনো SMS পাঠানো হচ্ছে না। চালু করলে আবার শুরু হবে।"
                mainBtn.text = "চালু করুন"; mainBtn.setBackgroundResource(R.drawable.bg_btn_primary)
            }
        }
        setChip(chipSms, "SMS পারমিশন", sms)
        setChip(chipNotif, "নোটিফিকেশন", notif)
    }

    private fun setDot(colorRes: Int) {
        (dot.background.mutate() as GradientDrawable).setColor(ContextCompat.getColor(this, colorRes))
    }

    private fun setChip(v: TextView, label: String, ok: Boolean) {
        v.text = (if (ok) "✓ " else "✕ ") + label
        v.setTextColor(ContextCompat.getColor(this, if (ok) R.color.good else R.color.bad))
    }
}
