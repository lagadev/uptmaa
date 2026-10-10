package app.paylink.sms

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Retries with backoff until the server confirms, so an SMS received offline still arrives later.
class UploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val token = Prefs.token(applicationContext)
        if (token.isEmpty()) return Result.failure()

        val payload = JSONObject()
            .put("sender", inputData.getString("sender"))
            .put("body", inputData.getString("body"))
            .put("receivedAt", inputData.getLong("receivedAt", System.currentTimeMillis()))
            .toString()

        return try {
            val c = URL("${Config.SERVER_URL}/api/sms/ingest").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.outputStream.use { it.write(payload.toByteArray()) }
            when (c.responseCode) {
                200 -> Result.success()
                401 -> Result.failure()   // wrong/rotated key: retrying won't help
                else -> Result.retry()
            }
        } catch (e: Exception) { Result.retry() }
    }
}
