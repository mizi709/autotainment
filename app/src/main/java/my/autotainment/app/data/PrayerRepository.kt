package my.autotainment.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class PrayerTime(val key: String, val label: String, val epochMs: Long)
data class PrayerDay(val date: String /* yyyy-MM-dd */, val hijri: String, val times: List<PrayerTime>)
data class PrayerData(val zone: String, val ym: String /* yyyy-MM */, val source: String, val days: List<PrayerDay>)

/**
 * Waktu solat rasmi JAKIM.
 * Sumber utama : api.waktusolat.app (mirror data e-Solat JAKIM, ada auto zon ikut GPS)
 * Sumber backup: www.e-solat.gov.my (API JAKIM terus)
 */
object PrayerRepository {
    private const val TAG = "PrayerRepo"
    private const val DEFAULT_ZONE = "WLY01"
    val KL: TimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")

    // Turutan paparan. Syuruk dipapar tapi bukan waktu solat (tiada azan).
    val LABELS = linkedMapOf(
        "fajr" to "Subuh",
        "syuruk" to "Syuruk",
        "dhuhr" to "Zohor",
        "asr" to "Asar",
        "maghrib" to "Maghrib",
        "isha" to "Isyak",
    )

    private val MONTHS_EN = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val HIJRI_MS = listOf(
        "Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
        "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah",
    )

    private fun ymNow(): String {
        val c = Calendar.getInstance(KL)
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    fun todayKey(nowMs: Long = System.currentTimeMillis()): String = dayKey(nowMs, 0)

    fun dayKey(nowMs: Long, offsetDays: Int): String {
        val c = Calendar.getInstance(KL)
        c.timeInMillis = nowMs
        c.add(Calendar.DAY_OF_MONTH, offsetDays)
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun cached(ctx: Context): PrayerData? =
        Prefs.prayerCache(ctx)?.let { runCatching { fromCacheJson(JSONObject(it)) }.getOrNull() }

    /**
     * Muat waktu solat bulan semasa. Guna cache kalau masih sah.
     * Urutan zon: manual override > GPS > zon cache > default.
     */
    suspend fun load(ctx: Context, lat: Double?, lon: Double?, force: Boolean): PrayerData? =
        withContext(Dispatchers.IO) {
            val override = Prefs.zoneOverride(ctx)
            val cache = cached(ctx)
            val ym = ymNow()
            val cacheValid = cache != null && cache.ym == ym &&
                (override == null || override == cache.zone) &&
                cache.days.any { it.date == todayKey() }
            if (!force && cacheValid) return@withContext cache

            val fallbackZone = override ?: cache?.zone ?: DEFAULT_ZONE
            val result: PrayerData? =
                runCatching {
                    val url = when {
                        override != null -> "https://api.waktusolat.app/v2/solat/$override"
                        lat != null && lon != null -> "https://api.waktusolat.app/v2/solat/gps/$lat/$lon"
                        else -> "https://api.waktusolat.app/v2/solat/$fallbackZone"
                    }
                    parseWaktuSolatApp(JSONObject(Http.get(url)))
                }.onFailure { Log.w(TAG, "waktusolat.app gagal: ${it.message}") }.getOrNull()
                    ?: runCatching {
                        val url = "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=month&zone=$fallbackZone"
                        parseEsolat(JSONObject(Http.get(url)), fallbackZone)
                    }.onFailure { Log.w(TAG, "e-solat gagal: ${it.message}") }.getOrNull()

            if (result != null && result.days.isNotEmpty()) {
                Prefs.setPrayerCache(ctx, toCacheJson(result).toString())
                result
            } else {
                cache
            }
        }

    // ---- Parser: api.waktusolat.app v2 (masa dalam epoch saat) ----
    private fun parseWaktuSolatApp(root: JSONObject): PrayerData {
        val zone = root.getString("zone").uppercase()
        val year = root.getInt("year")
        val monthIdx = MONTHS_EN.indexOf(root.getString("month").uppercase().take(3))
        require(monthIdx >= 0) { "Bulan tak dikenali" }
        val arr = root.getJSONArray("prayers")
        val days = ArrayList<PrayerDay>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val day = o.getInt("day")
            val date = String.format(Locale.US, "%04d-%02d-%02d", year, monthIdx + 1, day)
            val times = LABELS.mapNotNull { (k, label) ->
                if (o.has(k)) PrayerTime(k, label, o.getLong(k) * 1000L) else null
            }
            days.add(PrayerDay(date, formatHijri(o.optString("hijri", "")), times))
        }
        val ym = String.format(Locale.US, "%04d-%02d", year, monthIdx + 1)
        return PrayerData(zone, ym, "waktusolat.app", days)
    }

    // ---- Parser: e-solat.gov.my (date "12-Nov-2026", masa "HH:mm:ss") ----
    private fun parseEsolat(root: JSONObject, zone: String): PrayerData {
        val arr = root.getJSONArray("prayerTime")
        val inFmt = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH).apply { timeZone = KL }
        val days = ArrayList<PrayerDay>()
        var ym = ymNow()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rawDate = o.getString("date")
            val dayStart = inFmt.parse("$rawDate 00:00:00") ?: continue
            val date = dayKey(dayStart.time, 0)
            ym = date.take(7)
            val times = LABELS.mapNotNull { (k, label) ->
                val t = o.optString(k, "")
                if (t.isBlank()) null
                else inFmt.parse("$rawDate $t")?.let { PrayerTime(k, label, it.time) }
            }
            days.add(PrayerDay(date, formatHijri(o.optString("hijri", "")), times))
        }
        return PrayerData(root.optString("zone", zone).uppercase(), ym, "e-solat.gov.my", days)
    }

    /** "1448-05-22" -> "22 Jamadilawal 1448". Format lain dipapar seadanya. */
    private fun formatHijri(raw: String): String {
        val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(raw.trim()) ?: return raw
        val (y, mo, d) = m.destructured
        val name = HIJRI_MS.getOrNull(mo.toInt() - 1) ?: return raw
        return "${d.toInt()} $name $y"
    }

    // ---- Cache ----
    private fun toCacheJson(p: PrayerData): JSONObject {
        val days = JSONArray()
        p.days.forEach { d ->
            val t = JSONObject()
            d.times.forEach { t.put(it.key, it.epochMs) }
            days.put(JSONObject().put("date", d.date).put("hijri", d.hijri).put("t", t))
        }
        return JSONObject().put("zone", p.zone).put("ym", p.ym).put("source", p.source).put("days", days)
    }

    private fun fromCacheJson(o: JSONObject): PrayerData {
        val arr = o.getJSONArray("days")
        val days = (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val t = d.getJSONObject("t")
            PrayerDay(
                d.getString("date"),
                d.optString("hijri", ""),
                LABELS.mapNotNull { (k, label) -> if (t.has(k)) PrayerTime(k, label, t.getLong(k)) else null },
            )
        }
        return PrayerData(o.getString("zone"), o.getString("ym"), o.optString("source", ""), days)
    }

    // ---- Logik paparan ----
    fun today(p: PrayerData?, nowMs: Long = System.currentTimeMillis()): PrayerDay? =
        p?.days?.firstOrNull { it.date == dayKey(nowMs, 0) }

    /** Waktu solat seterusnya (bukan Syuruk). Lepas Isyak -> Subuh esok. */
    fun next(p: PrayerData?, nowMs: Long = System.currentTimeMillis()): PrayerTime? {
        if (p == null) return null
        val candidates = listOf(0, 1).flatMap { off ->
            p.days.firstOrNull { it.date == dayKey(nowMs, off) }?.times.orEmpty()
        }.filter { it.key != "syuruk" && it.epochMs > nowMs }
        candidates.minByOrNull { it.epochMs }?.let { return it }
        // Hujung bulan & data bulan depan belum ada: anggar Subuh esok = Subuh hari ini + 24 jam
        val fajr = today(p, nowMs)?.times?.firstOrNull { it.key == "fajr" } ?: return null
        return fajr.copy(epochMs = fajr.epochMs + 24 * 3600_000L)
    }

    /** Semua waktu hari ini & esok (untuk semakan azan). */
    fun upcomingWindow(p: PrayerData?, nowMs: Long): List<PrayerTime> {
        if (p == null) return emptyList()
        return listOf(-1, 0).flatMap { off ->
            p.days.firstOrNull { it.date == dayKey(nowMs, off) }?.times.orEmpty()
        }
    }
}
