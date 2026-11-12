package my.autotainment.app.data

import android.content.Context
import android.content.SharedPreferences

/** Semua tetapan & cache disimpan dalam phone sahaja. Tiada server. */
object Prefs {
    private const val NAME = "autotainment"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // Zon solat manual (contoh PLS01). Kosong = auto ikut GPS.
    fun zoneOverride(ctx: Context): String? =
        sp(ctx).getString("zone_override", null)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    fun setZoneOverride(ctx: Context, zone: String?) =
        sp(ctx).edit().putString("zone_override", zone?.trim()?.uppercase()).apply()

    fun prayerCache(ctx: Context): String? = sp(ctx).getString("prayer_cache", null)
    fun setPrayerCache(ctx: Context, json: String) = sp(ctx).edit().putString("prayer_cache", json).apply()

    fun weatherCache(ctx: Context): String? = sp(ctx).getString("weather_cache", null)
    fun setWeatherCache(ctx: Context, json: String) = sp(ctx).edit().putString("weather_cache", json).apply()

    fun placeName(ctx: Context): String? = sp(ctx).getString("place_name", null)
    fun setPlaceName(ctx: Context, name: String) = sp(ctx).edit().putString("place_name", name).apply()

    fun lastLocation(ctx: Context): Pair<Double, Double>? {
        val s = sp(ctx)
        if (!s.contains("last_lat") || !s.contains("last_lon")) return null
        return s.getFloat("last_lat", 0f).toDouble() to s.getFloat("last_lon", 0f).toDouble()
    }

    fun setLastLocation(ctx: Context, lat: Double, lon: Double) =
        sp(ctx).edit().putFloat("last_lat", lat.toFloat()).putFloat("last_lon", lon.toFloat()).apply()

    fun azanEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("azan_enabled", true)
    fun setAzanEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("azan_enabled", on).apply()

    // URI fail audio azan pilihan user (null = guna suara TTS)
    fun azanUri(ctx: Context): String? = sp(ctx).getString("azan_uri", null)
    fun setAzanUri(ctx: Context, uri: String?) = sp(ctx).edit().putString("azan_uri", uri).apply()

    fun lastAzanKey(ctx: Context): String? = sp(ctx).getString("last_azan_key", null)
    fun setLastAzanKey(ctx: Context, key: String) = sp(ctx).edit().putString("last_azan_key", key).apply()
}
