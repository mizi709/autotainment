package my.autotainment.app.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Satu tempat simpan data terkini yang dikongsi dashboard kereta & app phone. */
object DataHub {
    @Volatile var prayer: PrayerData? = null
        private set
    @Volatile var weather: Weather? = null
        private set
    @Volatile var place: String? = null
        private set
    @Volatile var lastRefresh: Long = 0L
        private set

    private val mutex = Mutex()

    /** Muat cache dari storan supaya dashboard terus ada data walaupun tiada internet. */
    fun warmUp(ctx: Context) {
        val app = ctx.applicationContext
        if (prayer == null) prayer = PrayerRepository.cached(app)
        if (weather == null) weather = WeatherRepository.cached(app)
        if (place == null) place = Prefs.placeName(app)
    }

    suspend fun refresh(ctx: Context, force: Boolean) = mutex.withLock {
        val app = ctx.applicationContext
        warmUp(app)

        val loc = LocationHelper.current(app)
        if (loc != null) Prefs.setLastLocation(app, loc.latitude, loc.longitude)
        val coords: Pair<Double, Double>? = loc?.let { it.latitude to it.longitude } ?: Prefs.lastLocation(app)

        PrayerRepository.load(app, coords?.first, coords?.second, force)?.let { prayer = it }

        if (coords != null) {
            WeatherRepository.load(app, coords.first, coords.second, force)?.let { weather = it }
            if (loc != null || place == null) {
                LocationHelper.placeName(app, coords.first, coords.second)?.let {
                    place = it
                    Prefs.setPlaceName(app, it)
                }
            }
        }
        lastRefresh = System.currentTimeMillis()
    }
}
