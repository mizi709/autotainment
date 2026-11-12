package my.autotainment.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import my.autotainment.app.alert.AzanPlayer
import my.autotainment.app.data.DataHub
import my.autotainment.app.data.LocationHelper
import my.autotainment.app.data.PrayerRepository
import my.autotainment.app.data.Prefs
import my.autotainment.app.media.NowPlaying
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RsRed = Color(0xFFE4002B)
private val Bg = Color(0xFF0A0A0C)
private val CardBg = Color(0xFF17171C)
private val Grey = Color(0xFF9A9AA4)
private val Ok = Color(0xFF2ECC71)

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private var azan: AzanPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataHub.warmUp(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = RsRed,
                    background = Bg,
                    surface = CardBg,
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White,
                ),
            ) {
                SetupScreen(
                    tick = resumeTick.intValue,
                    onTestAzan = {
                        val a = azan ?: AzanPlayer(this).also { azan = it }
                        a.play("Zohor")
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onDestroy() {
        azan?.release()
        super.onDestroy()
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun SetupScreen(tick: Int, onTestAzan: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var refreshCount by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var zoneInput by remember { mutableStateOf(Prefs.zoneOverride(ctx) ?: "") }
    var azanOn by remember { mutableStateOf(Prefs.azanEnabled(ctx)) }
    var azanUri by remember { mutableStateOf(Prefs.azanUri(ctx)) }

    // Nilai dikira semula setiap kali app dibuka semula (tick) atau selepas refresh
    val key = tick + refreshCount
    val hasLocation = remember(key) { LocationHelper.hasPermission(ctx) }
    val hasMedia = remember(key) { NowPlaying.hasAccess(ctx) }
    val batteryOk = remember(key) {
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshCount++ }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Prefs.setAzanUri(ctx, uri.toString())
            azanUri = uri.toString()
        }
    }

    fun doRefresh() {
        status = "Memuat data…"
        scope.launch {
            runCatching { DataHub.refresh(ctx, force = true) }
                .onSuccess { status = if (DataHub.prayer != null) "Berjaya dikemas kini" else "Gagal muat waktu solat. Semak internet." }
                .onFailure { status = "Ralat: ${it.message}" }
            refreshCount++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("AutoTainment", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Dashboard Android Auto percuma & padu", color = Grey)

        // ---- 1. Kebenaran ----
        Section("1. Kebenaran") {
            PermRow(
                "Lokasi (zon solat & cuaca)", hasLocation,
                "Benarkan",
            ) {
                permLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
            PermRow("Akses notifikasi (paparan lagu)", hasMedia, "Buka") {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermRow("Tiada had bateri", batteryOk, "Buka") {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "HyperOS/MIUI: buka Tetapan app > Autostart = ON dan Penjimat bateri = Tiada sekatan.",
                color = Grey, fontSize = 13.sp,
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                )
            }) { Text("Buka tetapan app") }
        }

        // ---- 2. Android Auto ----
        Section("2. Aktifkan dalam Android Auto") {
            Text(
                "App ini bukan dari Play Store, jadi Android Auto perlu benarkan \"Unknown sources\":\n" +
                    "• Tetapan phone > cari \"Android Auto\"\n" +
                    "• Tekan \"Version\" 10 kali > OK\n" +
                    "• Menu ⋮ > Developer settings\n" +
                    "• Tick \"Unknown sources\"\n" +
                    "• Android Auto > Customise launcher > pastikan AutoTainment ditick\n" +
                    "• Sambung semula ke kereta",
                color = Color.White, fontSize = 14.sp,
            )
        }

        // ---- 3. Waktu solat ----
        Section("3. Waktu solat (JAKIM)") {
            val prayer = remember(key) { DataHub.prayer }
            val today = PrayerRepository.today(prayer)
            val fmt = remember { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = PrayerRepository.KL } }
            if (prayer != null && today != null) {
                Text(
                    "Zon ${prayer.zone}${DataHub.place?.let { " • $it" } ?: ""} • ${today.hijri}",
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    today.times.joinToString("   ") { "${it.label} ${fmt.format(Date(it.epochMs))}" },
                    color = Grey, fontSize = 13.sp,
                )
                Text("Sumber: ${prayer.source}", color = Grey, fontSize = 12.sp)
            } else {
                Text("Belum dimuatkan.", color = Grey)
            }
            OutlinedTextField(
                value = zoneInput,
                onValueChange = { zoneInput = it.uppercase().take(5) },
                label = { Text("Zon manual (kosong = auto GPS), cth PLS01") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    Prefs.setZoneOverride(ctx, zoneInput.ifBlank { null })
                    doRefresh()
                }) { Text("Simpan & muat") }
                OutlinedButton(onClick = { doRefresh() }) { Text("Muat semula") }
            }
            if (status.isNotBlank()) Text(status, color = Grey, fontSize = 13.sp)
        }

        // ---- 4. Azan ----
        Section("4. Azan / peringatan waktu") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktifkan azan bila masuk waktu", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = azanOn,
                    onCheckedChange = { azanOn = it; Prefs.setAzanEnabled(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = RsRed),
                )
            }
            Text(
                if (azanUri == null) "Bunyi: suara (\"Telah masuk waktu solat …\")" else "Bunyi: fail audio pilihan anda",
                color = Grey, fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("Pilih fail azan") }
                if (azanUri != null) {
                    OutlinedButton(onClick = { Prefs.setAzanUri(ctx, null); azanUri = null }) { Text("Guna suara") }
                }
            }
            Button(
                onClick = onTestAzan,
                colors = ButtonDefaults.buttonColors(containerColor = RsRed),
            ) { Text("Uji bunyi") }
        }

        Text(
            "Versi ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"} • Data waktu solat: JAKIM e-Solat. Cuaca: Open-Meteo.",
            color = Grey, fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = RsRed, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            content()
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (ok) "✅" else "❌", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        if (!ok) Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = RsRed)) { Text(action) }
    }
}
