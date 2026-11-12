package my.autotainment.app.car

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import my.autotainment.app.R
import my.autotainment.app.alert.AzanPlayer
import my.autotainment.app.data.DataHub
import my.autotainment.app.data.PrayerRepository
import my.autotainment.app.media.NowPlaying

/**
 * Skrin utama dalam Android Auto.
 * Guna NavigationTemplate supaya kita dapat "surface" penuh untuk lukis dashboard sendiri.
 * Butang (prev / play-pause / next / refresh) duduk dalam action strip di tepi.
 */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {
    private val renderer = DashboardRenderer()
    private val handler = Handler(Looper.getMainLooper())
    private val azan = AzanPlayer(carContext)

    private var surface: SurfaceContainer? = null
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null
    private var lastDayKey: String = ""
    private var lastWeatherRefresh = 0L
    private var refreshing = false

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer
            render()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surface = null
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            this@DashboardScreen.visibleArea = Rect(visibleArea)
            render()
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            this@DashboardScreen.stableArea = Rect(stableArea)
            render()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            onTick()
            // Selaraskan dengan saat seterusnya supaya jam tak "lompat"
            val delay = 1000L - (System.currentTimeMillis() % 1000L) + 10L
            handler.postDelayed(this, delay)
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                DataHub.warmUp(carContext)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
                handler.post(ticker)
                refresh(force = false)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                handler.removeCallbacks(ticker)
                azan.release()
            }
        })
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        // Hari baru -> muat semula waktu solat
        val day = PrayerRepository.todayKey(now)
        if (day != lastDayKey) {
            lastDayKey = day
            if (DataHub.lastRefresh != 0L) refresh(force = false)
        }
        // Cuaca setiap 15 minit
        if (now - lastWeatherRefresh > 15 * 60_000L) {
            lastWeatherRefresh = now
            if (DataHub.lastRefresh != 0L) refresh(force = false)
        }
        azan.check(now, DataHub.prayer)
        render()
    }

    private fun refresh(force: Boolean) {
        if (refreshing) return
        refreshing = true
        lifecycleScope.launch {
            try {
                DataHub.refresh(carContext, force)
            } catch (e: Exception) {
                Log.w("Dashboard", "Refresh gagal: ${e.message}")
            } finally {
                refreshing = false
                render()
            }
        }
    }

    private fun render() {
        val container = surface ?: return
        val s = container.surface ?: return
        if (!s.isValid) return
        val area = visibleArea ?: stableArea ?: Rect(0, 0, container.width, container.height)
        val state = DashState(
            nowMs = System.currentTimeMillis(),
            prayer = DataHub.prayer,
            weather = DataHub.weather,
            place = DataHub.place,
            media = runCatching { NowPlaying.info(carContext) }.getOrNull(),
            mediaAccess = NowPlaying.hasAccess(carContext),
        )
        try {
            val canvas = s.lockHardwareCanvas()
            try {
                renderer.draw(canvas, area, state)
            } finally {
                s.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.w("Dashboard", "Lukis gagal: ${e.message}")
        }
    }

    private fun icon(res: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder().setIcon(icon(R.drawable.ic_prev))
                    .setOnClickListener { NowPlaying.previous(carContext); render() }.build(),
            )
            .addAction(
                Action.Builder().setIcon(icon(R.drawable.ic_play_pause))
                    .setOnClickListener { NowPlaying.playPause(carContext); render() }.build(),
            )
            .addAction(
                Action.Builder().setIcon(icon(R.drawable.ic_next))
                    .setOnClickListener { NowPlaying.next(carContext); render() }.build(),
            )
            .addAction(
                Action.Builder().setIcon(icon(R.drawable.ic_refresh))
                    .setOnClickListener {
                        CarToast.makeText(carContext, "Mengemas kini data…", CarToast.LENGTH_SHORT).show()
                        refresh(force = true)
                    }.build(),
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .build()
    }
}
