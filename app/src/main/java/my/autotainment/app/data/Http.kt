package my.autotainment.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP GET ringkas tanpa library luar. Mesti dipanggil dari thread IO. */
object Http {
    fun get(url: String, timeoutMs: Int = 12_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "AutoTainment/0.1 (Android)")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${body.take(160)}")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
