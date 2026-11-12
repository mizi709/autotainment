package my.autotainment.app.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat

/** Baca & kawal media yang sedang dimainkan (Spotify, YouTube Music, Joox dll). */
object NowPlaying {
    data class Info(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val art: Bitmap?,
        val positionMs: Long,
        val durationMs: Long,
    )

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun controller(ctx: Context): MediaController? {
        if (!hasAccess(ctx)) return null
        return try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
            val sessions = msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }

    fun info(ctx: Context): Info? {
        val c = controller(ctx) ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        // Anggar posisi semasa berdasarkan masa kemas kini terakhir
        val pos = if (st == null) 0L else {
            val elapsed = if (playing) {
                ((android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            } else 0L
            (st.position + elapsed).coerceAtLeast(0L)
        }
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        return Info(title, artist, playing, art, pos, dur)
    }

    fun playPause(ctx: Context) {
        val c = controller(ctx) ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next(ctx: Context) {
        controller(ctx)?.transportControls?.skipToNext()
    }

    fun previous(ctx: Context) {
        controller(ctx)?.transportControls?.skipToPrevious()
    }
}
