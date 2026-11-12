package my.autotainment.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

data class Weather(
    val tempC: Double,
    val code: Int,
    val humidity: Int,
    val isDay: Boolean,
    val fetchedAt: Long,
) {
    val tempText: String get() = "${tempC.roundToInt()}°C"
    val description: String get() = WeatherRepository.describe(code)
}

/** Cuaca semasa dari Open-Meteo (percuma, tiada API key). Cache 15 minit. */
object WeatherRepository {
    private const val TAG = "WeatherRepo"
    private const val TTL_MS = 15 * 60_000L

    fun cached(ctx: Context): Weather? =
        Prefs.weatherCache(ctx)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                Weather(
                    o.getDouble("t"), o.getInt("c"), o.getInt("h"),
                    o.getBoolean("d"), o.getLong("at"),
                )
            }.getOrNull()
        }

    suspend fun load(ctx: Context, lat: Double, lon: Double, force: Boolean): Weather? =
        withContext(Dispatchers.IO) {
            val cache = cached(ctx)
            if (!force && cache != null && System.currentTimeMillis() - cache.fetchedAt < TTL_MS) {
                return@withContext cache
            }
            runCatching {
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,is_day&timezone=auto",
                    lat, lon,
                )
                val cur = JSONObject(Http.get(url)).getJSONObject("current")
                val w = Weather(
                    tempC = cur.getDouble("temperature_2m"),
                    code = cur.getInt("weather_code"),
                    humidity = cur.optInt("relative_humidity_2m", -1),
                    isDay = cur.optInt("is_day", 1) == 1,
                    fetchedAt = System.currentTimeMillis(),
                )
                Prefs.setWeatherCache(
                    ctx,
                    JSONObject().put("t", w.tempC).put("c", w.code).put("h", w.humidity)
                        .put("d", w.isDay).put("at", w.fetchedAt).toString(),
                )
                w
            }.onFailure { Log.w(TAG, "Cuaca gagal: ${it.message}") }.getOrNull() ?: cache
        }

    /** Kod cuaca WMO -> Bahasa Melayu */
    fun describe(code: Int): String = when (code) {
        0 -> "Cerah"
        1 -> "Kebanyakannya cerah"
        2 -> "Separa berawan"
        3 -> "Mendung"
        45, 48 -> "Berkabus"
        51, 53, 55 -> "Gerimis"
        56, 57 -> "Gerimis sejuk"
        61 -> "Hujan renyai"
        63 -> "Hujan"
        65 -> "Hujan lebat"
        66, 67 -> "Hujan sejuk"
        71, 73, 75, 77 -> "Salji"
        80 -> "Hujan sekejap-sekejap"
        81 -> "Hujan lebat sekejap"
        82 -> "Hujan sangat lebat"
        85, 86 -> "Salji lebat"
        95 -> "Ribut petir"
        96, 99 -> "Ribut petir & hujan batu"
        else -> "Kod cuaca $code"
    }
}
