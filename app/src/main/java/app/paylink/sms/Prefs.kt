package app.paylink.sms

import android.content.Context

object Prefs {
    private const val FILE = "uglypay_sms"
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun token(c: Context): String = p(c).getString("token", "") ?: ""
    fun loggedIn(c: Context): Boolean = token(c).isNotEmpty()
    fun save(c: Context, token: String) { p(c).edit().putString("token", token.trim()).apply() }
    fun clear(c: Context) { p(c).edit().remove("token").putBoolean("active", false).apply() }

    // "active" = the user has forwarding switched on (Start). Stop turns it off.
    fun active(c: Context): Boolean = p(c).getBoolean("active", false)
    fun setActive(c: Context, on: Boolean) { p(c).edit().putBoolean("active", on).apply() }
}
