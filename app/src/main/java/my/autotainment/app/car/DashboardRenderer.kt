package my.autotainment.app.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import my.autotainment.app.data.PrayerData
import my.autotainment.app.data.PrayerRepository
import my.autotainment.app.data.Weather
import my.autotainment.app.media.NowPlaying
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** Semua data yang perlu untuk satu frame dashboard. */
data class DashState(
    val nowMs: Long,
    val prayer: PrayerData?,
    val weather: Weather?,
    val place: String?,
    val media: NowPlaying.Info?,
    val mediaAccess: Boolean,
)

/**
 * Lukis dashboard tema RS (hitam + merah) terus pada Canvas.
 * Semua saiz relatif pada kawasan nampak, jadi auto-muat skrin 800x480 hingga 1920x720.
 */
class DashboardRenderer {
    private val bg = Color.parseColor("#0A0A0C")
    private val card = Color.parseColor("#17171C")
    private val cardHi = Color.parseColor("#22222A")
    private val red = Color.parseColor("#E4002B")
    private val redDim = Color.parseColor("#5A0011")
    private val white = Color.WHITE
    private val grey = Color.parseColor("#9A9AA4")
    private val greyDark = Color.parseColor("#55555E")

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tfBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfReg = Typeface.create("sans-serif", Typeface.NORMAL)

    private val days = arrayOf("Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu")
    private val months = arrayOf("Jan", "Feb", "Mac", "Apr", "Mei", "Jun", "Jul", "Ogos", "Sep", "Okt", "Nov", "Dis")

    fun draw(c: Canvas, area: Rect, s: DashState) {
        c.drawColor(bg)
        if (area.width() < 50 || area.height() < 50) return

        val w = area.width().toFloat()
        val h = area.height().toFloat()
        val pad = min(w, h) * 0.04f
        val gap = pad * 0.6f
        val l = area.left + pad
        val t = area.top + pad
        val r = area.right - pad
        val b = area.bottom - pad
        val innerW = r - l
        val innerH = b - t

        val topH = innerH * 0.55f
        val stripH = innerH * 0.22f
        val stripTop = t + topH + gap
        val npTop = stripTop + stripH + gap

        val leftW = innerW * 0.46f
        drawClock(c, RectF(l, t, l + leftW, t + topH), s)

        val rx = l + leftW + gap
        val cardH = (topH - gap) / 2f
        drawNextPrayer(c, RectF(rx, t, r, t + cardH), s)
        drawWeather(c, RectF(rx, t + cardH + gap, r, t + topH), s)
        drawPrayerStrip(c, RectF(l, stripTop, r, stripTop + stripH), s)
        if (b - npTop > 20f) drawNowPlaying(c, RectF(l, npTop, r, b), s)
    }

    // ---------- Jam & tarikh ----------
    private fun drawClock(c: Canvas, box: RectF, s: DashState) {
        val cal = Calendar.getInstance(PrayerRepository.KL)
        cal.timeInMillis = s.nowMs
        val hhmm = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val sec = String.format(":%02d", cal.get(Calendar.SECOND))

        // Jam besar
        p.typeface = tfBold
        p.color = white
        val secRatio = 0.32f
        var size = box.height() * 0.48f
        p.textSize = size
        val maxW = box.width()
        while (size > 10f) {
            p.textSize = size
            val total = p.measureText(hhmm) + measureAt(sec, size * secRatio, tfBold)
            if (total <= maxW) break
            size *= 0.94f
        }
        p.textSize = size
        val clockBase = box.top + size * 0.86f
        c.drawText(hhmm, box.left, clockBase, p)
        val hhmmW = p.measureText(hhmm)
        p.textSize = size * secRatio
        p.color = red
        c.drawText(sec, box.left + hhmmW + size * 0.04f, clockBase, p)

        // Tarikh
        val date = "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${cal.get(Calendar.DAY_OF_MONTH)} " +
            "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
        var y = clockBase + box.height() * 0.17f
        drawFit(c, date, box.left, y, maxW, box.height() * 0.12f, white, tfMed)

        // Hijri
        val hijri = PrayerRepository.today(s.prayer, s.nowMs)?.hijri.orEmpty()
        if (hijri.isNotBlank()) {
            y += box.height() * 0.14f
            drawFit(c, hijri, box.left, y, maxW, box.height() * 0.10f, red, tfMed)
        }

        // Zon & lokasi
        val zone = s.prayer?.zone
        val loc = listOfNotNull(s.place, zone?.let { "Zon $it" }).joinToString("  •  ")
        if (loc.isNotBlank()) {
            y += box.height() * 0.13f
            drawFit(c, loc, box.left, y, maxW, box.height() * 0.085f, grey, tfReg)
        }
    }

    // ---------- Kad waktu solat seterusnya ----------
    private fun drawNextPrayer(c: Canvas, box: RectF, s: DashState) {
        roundCard(c, box, card)
        val accentW = max(4f, box.height() * 0.05f)
        p.color = red
        c.drawRoundRect(RectF(box.left, box.top, box.left + accentW, box.bottom), accentW, accentW, p)

        val inL = box.left + accentW + box.height() * 0.14f
        val inW = box.right - inL - box.height() * 0.12f
        val next = PrayerRepository.next(s.prayer, s.nowMs)
        val label = box.height() * 0.15f

        drawFit(c, "WAKTU SETERUSNYA", inL, box.top + box.height() * 0.27f, inW, label, grey, tfMed)
        if (next == null) {
            drawFit(c, "Belum dimuatkan", inL, box.top + box.height() * 0.62f, inW, box.height() * 0.22f, white, tfBold)
            drawFit(c, "Semak internet / lokasi", inL, box.top + box.height() * 0.86f, inW, label, grey, tfReg)
            return
        }
        val t = Calendar.getInstance(PrayerRepository.KL).apply { timeInMillis = next.epochMs }
        val hm = String.format("%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
        drawFit(c, "${next.label}  $hm", inL, box.top + box.height() * 0.63f, inW, box.height() * 0.30f, white, tfBold)

        val diff = max(0L, next.epochMs - s.nowMs) / 1000
        val hh = diff / 3600
        val mm = (diff % 3600) / 60
        val ss = diff % 60
        val countdown = if (hh > 0) "dalam ${hh}j ${mm}m ${ss}s" else "dalam ${mm}m ${ss}s"
        drawFit(c, countdown, inL, box.top + box.height() * 0.88f, inW, box.height() * 0.17f, red, tfMed)
    }

    // ---------- Kad cuaca ----------
    private fun drawWeather(c: Canvas, box: RectF, s: DashState) {
        roundCard(c, box, card)
        val inL = box.left + box.height() * 0.16f
        val inW = box.width() - box.height() * 0.32f
        val w = s.weather
        if (w == null) {
            drawFit(c, "CUACA", inL, box.top + box.height() * 0.27f, inW, box.height() * 0.15f, grey, tfMed)
            drawFit(c, "Tiada data", inL, box.top + box.height() * 0.62f, inW, box.height() * 0.22f, white, tfBold)
            drawFit(c, "Benarkan lokasi dalam app", inL, box.top + box.height() * 0.86f, inW, box.height() * 0.15f, grey, tfReg)
            return
        }
        // Suhu besar di kiri, penerangan di kanan
        p.typeface = tfBold
        val tempSize = box.height() * 0.50f
        p.textSize = tempSize
        val tempW = min(p.measureText(w.tempText), inW * 0.45f)
        drawFit(c, w.tempText, inL, box.top + box.height() * 0.66f, inW * 0.45f, tempSize, white, tfBold)

        val dl = inL + tempW + box.height() * 0.18f
        val dw = box.right - dl - box.height() * 0.14f
        drawFit(c, w.description, dl, box.top + box.height() * 0.42f, dw, box.height() * 0.19f, white, tfMed)
        val sub = buildString {
            if (w.humidity >= 0) append("Lembap ${w.humidity}%")
            append(if (w.isDay) "  •  Siang" else "  •  Malam")
        }
        drawFit(c, sub, dl, box.top + box.height() * 0.68f, dw, box.height() * 0.14f, grey, tfReg)
    }

    // ---------- Jalur 6 waktu solat ----------
    private fun drawPrayerStrip(c: Canvas, box: RectF, s: DashState) {
        val today = PrayerRepository.today(s.prayer, s.nowMs)
        val keys = PrayerRepository.LABELS.keys.toList()
        val n = keys.size
        val gap = box.height() * 0.08f
        val cellW = (box.width() - gap * (n - 1)) / n
        val next = PrayerRepository.next(s.prayer, s.nowMs)

        keys.forEachIndexed { i, key ->
            val cl = box.left + i * (cellW + gap)
            val cell = RectF(cl, box.top, cl + cellW, box.bottom)
            val pt = today?.times?.firstOrNull { it.key == key }
            val isNext = next != null && pt != null && next.epochMs == pt.epochMs
            val passed = pt != null && pt.epochMs <= s.nowMs
            roundCard(c, cell, if (isNext) red else if (passed) card else cardHi)

            val labelColor = if (isNext) white else if (passed) greyDark else grey
            val timeColor = if (isNext) white else if (passed) grey else white
            val name = PrayerRepository.LABELS[key] ?: key
            val time = pt?.let {
                val t = Calendar.getInstance(PrayerRepository.KL).apply { timeInMillis = it.epochMs }
                String.format("%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
            } ?: "--:--"
            val padX = cellW * 0.1f
            drawFitCentered(c, name, cell.centerX(), cell.top + cell.height() * 0.38f, cellW - padX * 2, cell.height() * 0.24f, labelColor, tfMed)
            drawFitCentered(c, time, cell.centerX(), cell.top + cell.height() * 0.80f, cellW - padX * 2, cell.height() * 0.36f, timeColor, tfBold)
        }
    }

    // ---------- Lagu sedang dimainkan ----------
    private fun drawNowPlaying(c: Canvas, box: RectF, s: DashState) {
        roundCard(c, box, card)
        val ip = box.height() * 0.14f
        var textL = box.left + ip * 1.4f
        val m = s.media

        if (m?.art != null) {
            val side = box.height() - ip * 2
            val dst = RectF(box.left + ip, box.top + ip, box.left + ip + side, box.top + ip + side)
            drawBitmapCover(c, m.art, dst)
            textL = dst.right + ip * 1.2f
        }
        val textW = box.right - textL - box.height() * 0.9f

        when {
            !s.mediaAccess -> {
                drawFit(c, "Paparan lagu dimatikan", textL, box.top + box.height() * 0.45f, textW, box.height() * 0.24f, white, tfMed)
                drawFit(c, "Buka app AutoTainment di phone > Benarkan akses notifikasi", textL, box.top + box.height() * 0.78f, textW, box.height() * 0.18f, grey, tfReg)
            }
            m == null -> {
                drawFit(c, "Tiada media dimainkan", textL, box.top + box.height() * 0.6f, textW, box.height() * 0.26f, grey, tfMed)
            }
            else -> {
                drawFit(c, m.title, textL, box.top + box.height() * 0.45f, textW, box.height() * 0.28f, white, tfBold)
                if (m.artist.isNotBlank()) {
                    drawFit(c, m.artist, textL, box.top + box.height() * 0.76f, textW, box.height() * 0.20f, grey, tfReg)
                }
                // Bar kemajuan
                if (m.durationMs > 0) {
                    val frac = (m.positionMs.toFloat() / m.durationMs).coerceIn(0f, 1f)
                    val barH = max(3f, box.height() * 0.05f)
                    val by = box.bottom - barH - box.height() * 0.06f
                    p.color = redDim
                    c.drawRoundRect(RectF(textL, by, box.right - ip * 1.4f, by + barH), barH, barH, p)
                    p.color = red
                    c.drawRoundRect(RectF(textL, by, textL + (box.right - ip * 1.4f - textL) * frac, by + barH), barH, barH, p)
                }
                // Ikon status main/jeda
                val iconSize = box.height() * 0.32f
                val cx = box.right - ip * 1.4f - iconSize / 2f
                val cy = box.top + box.height() * 0.42f
                p.color = red
                if (m.playing) {
                    val bw = iconSize * 0.3f
                    c.drawRect(cx - iconSize / 2f, cy - iconSize / 2f, cx - iconSize / 2f + bw, cy + iconSize / 2f, p)
                    c.drawRect(cx + iconSize / 2f - bw, cy - iconSize / 2f, cx + iconSize / 2f, cy + iconSize / 2f, p)
                } else {
                    val path = android.graphics.Path().apply {
                        moveTo(cx - iconSize / 2f, cy - iconSize / 2f)
                        lineTo(cx + iconSize / 2f, cy)
                        lineTo(cx - iconSize / 2f, cy + iconSize / 2f)
                        close()
                    }
                    c.drawPath(path, p)
                }
            }
        }
    }

    // ---------- Helper ----------
    private fun roundCard(c: Canvas, box: RectF, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        val rad = min(box.width(), box.height()) * 0.14f
        c.drawRoundRect(box, rad, rad, p)
    }

    private fun measureAt(text: String, size: Float, tf: Typeface): Float {
        val old = p.textSize
        val oldTf = p.typeface
        p.textSize = size
        p.typeface = tf
        val w = p.measureText(text)
        p.textSize = old
        p.typeface = oldTf
        return w
    }

    /** Lukis teks kiri; kecilkan saiz kalau terlalu panjang, kemudian potong dengan "…". */
    private fun drawFit(c: Canvas, text: String, x: Float, baseline: Float, maxW: Float, size: Float, color: Int, tf: Typeface) {
        if (maxW <= 4f) return
        p.typeface = tf
        p.color = color
        p.textSize = size
        val minSize = size * 0.7f
        while (p.measureText(text) > maxW && p.textSize > minSize) p.textSize *= 0.94f
        c.drawText(ellipsize(text, maxW), x, baseline, p)
    }

    private fun drawFitCentered(c: Canvas, text: String, cx: Float, baseline: Float, maxW: Float, size: Float, color: Int, tf: Typeface) {
        if (maxW <= 4f) return
        p.typeface = tf
        p.color = color
        p.textSize = size
        while (p.measureText(text) > maxW && p.textSize > 6f) p.textSize *= 0.94f
        c.drawText(text, cx - p.measureText(text) / 2f, baseline, p)
    }

    private fun ellipsize(text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Lukis bitmap isi penuh kotak (crop tengah). */
    private fun drawBitmapCover(c: Canvas, bmp: Bitmap, dst: RectF) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val scale = max(dst.width() / bw, dst.height() / bh)
        val sw = dst.width() / scale
        val sh = dst.height() / scale
        val sx = (bw - sw) / 2f
        val sy = (bh - sh) / 2f
        val src = Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        c.save()
        val rad = dst.width() * 0.12f
        val clip = android.graphics.Path().apply { addRoundRect(dst, rad, rad, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        c.drawBitmap(bmp, src, dst, bmpPaint)
        c.restore()
    }
}
