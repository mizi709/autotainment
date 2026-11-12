package my.autotainment.app.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Pintu masuk Android Auto. */
class AutoTainmentCarAppService : CarAppService() {

    // App sideload: benarkan semua host (Android Auto). App Play Store sebenar patut hadkan senarai host.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = DashboardScreen(carContext)
    }
}
