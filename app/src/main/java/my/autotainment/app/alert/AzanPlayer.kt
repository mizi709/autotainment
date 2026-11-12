package my.autotainment.app.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import my.autotainment.app.data.PrayerData
import my.autotainment.app.data.PrayerRepository
import my.autotainment.app.data.Prefs
import java.util.Locale

/**
 * Azan / peringatan waktu solat.
 * - Kalau user pilih fail audio azan sendiri -> mainkan fail tu.
 * - Kalau tidak -> suara TTS "Telah masuk waktu solat Zohor".
 * Muzik dijeda sementara (audio focus) dan sambung semula selepas itu.
 */
class AzanPlayer(ctx: Context) {
    private val app = ctx.applicationContext
    private val am = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    val r = t.setLanguage(Locale("ms", "MY"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        t.setLanguage(Locale("id", "ID"))
                    }
                    t.setAudioAttributes(attrs)
                    ttsReady = true
                }
            }
        }
    }

    /** Panggil setiap saat. Main azan sekali sahaja dalam 2 minit pertama waktu masuk. */
    fun check(nowMs: Long, data: PrayerData?) {
        if (!Prefs.azanEnabled(app)) return
        val hit = PrayerRepository.upcomingWindow(data, nowMs).firstOrNull {
            it.key != "syuruk" && nowMs >= it.epochMs && nowMs - it.epochMs < 120_000L
        } ?: return
        val key = "${hit.key}-${hit.epochMs}"
        if (Prefs.lastAzanKey(app) == key) return
        Prefs.setLastAzanKey(app, key)
        play(hit.label)
    }

    fun play(label: String) {
        stop()
        am?.requestAudioFocus(focusRequest)
        val uri = Prefs.azanUri(app)
        if (uri != null && playUri(Uri.parse(uri))) return
        speak("Telah masuk waktu solat $label")
    }

    private fun playUri(uri: Uri): Boolean = try {
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(app, uri)
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ -> stop(); true }
            prepare()
            start()
        }
        true
    } catch (e: Exception) {
        Log.w("AzanPlayer", "Fail audio gagal: ${e.message}")
        player?.release()
        player = null
        false
    }

    private fun speak(text: String) {
        val t = tts
        if (t == null || !ttsReady) {
            // TTS belum siap: cuba lagi sekali selepas 1.5 saat
            handler.postDelayed({ if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "azan") }, 1500)
        } else {
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "azan")
        }
        // Lepaskan audio focus selepas suara habis (anggaran)
        handler.postDelayed({ am?.abandonAudioFocusRequest(focusRequest) }, 7000)
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        am?.abandonAudioFocusRequest(focusRequest)
    }

    fun release() {
        stop()
        handler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        tts = null
    }
}
