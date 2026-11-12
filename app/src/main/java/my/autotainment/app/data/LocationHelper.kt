package my.autotainment.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {
    private const val FRESH_MS = 20 * 60_000L

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Lokasi terkini. Guna last-known kalau masih segar, kalau tak minta satu bacaan baru (max 15s). */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())

        var best: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || l.time > best.time) best = l
        }
        if (best != null && System.currentTimeMillis() - best.time < FRESH_MS) return best

        val provider = when {
            LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return best
        }
        val fresh = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                runCatching {
                    lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: best
    }

    /** Nama tempat ringkas (contoh "Kangar") dari koordinat. */
    @Suppress("DEPRECATION")
    suspend fun placeName(ctx: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val a = Geocoder(ctx, Locale("ms", "MY")).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull()
    }
}
