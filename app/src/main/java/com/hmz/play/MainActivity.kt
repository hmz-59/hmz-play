package com.hmz.play

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.LinkService
import com.hmz.play.data.Shell
import com.hmz.play.data.SplitService
import com.hmz.play.ui.CarRoot
import com.hmz.play.ui.HmzTheme
import org.osmdroid.config.Configuration
import java.io.File

open class MainActivity : ComponentActivity() {

    private var locationGranted by mutableStateOf(false)
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.hmz.play.data.CrashLog.install(this)
        com.hmz.play.data.Backup.restoreIfFresh(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        Configuration.getInstance().apply {
            userAgentValue = packageName
            val base = File(cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
        }
        locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        com.hmz.play.data.Prefs.load(this)
        handleExtras(intent)

        // Reload the app grid only when an app is installed or removed (not on every resume: faster).
        ContextCompat.registerReceiver(this, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                refreshKey++
                // Waze just installed: put it in place right away (left half), no restart needed.
                if (i.action == Intent.ACTION_PACKAGE_ADDED && i.data?.schemeSpecificPart == "com.waze" &&
                    com.hmz.play.data.Split.autoOpen(this@MainActivity) && !isInMultiWindowMode
                ) {
                    com.hmz.play.data.Split.autoOpenedThisBoot = true
                    window.decorView.postDelayed({ com.hmz.play.data.Split.openNavigation(this@MainActivity) }, 1500)
                }
            }
        }, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            HmzTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                    // storage is now readable: restore a saved backup after a reinstall
                    if (!getSharedPreferences("ui", MODE_PRIVATE).getBoolean("restored_once", false)) {
                        com.hmz.play.data.Backup.restoreIfFresh(this@MainActivity)
                        com.hmz.play.data.Prefs.reload(this@MainActivity)
                    }
                    startLink()
                }
                LaunchedEffect(Unit) {
                    val missing = requiredPermissions().filterNot { granted(it) }
                    if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray()) else startLink()
                }
                CarRoot(
                    locationGranted = locationGranted,
                    refreshKey = refreshKey,
                    onRequestPermissions = { launcher.launch(requiredPermissions().toTypedArray()) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Shell.hmzVisible = true
        // Back on HMZ Play (home button…): if the sound mode runs, show the layer over the Bluetooth app.
        if (javaClass == MainActivity::class.java && requiredPermissions().all { granted(it) }) {
            window.decorView.post { com.hmz.play.data.SoundMode.enter(this) }
        }
        SplitService.instance?.refresh()
    }

    override fun onStop() {
        super.onStop()
        com.hmz.play.data.Backup.save(this)
        com.hmz.play.data.BlackBox.flush()
        Shell.hmzVisible = com.hmz.play.data.SoundMode.active()
        SplitService.instance?.refresh()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        com.hmz.play.data.Shell.inSplit = isInMultiWindowMode
    }

    override fun onResume() {
        super.onResume()
        com.hmz.play.data.Shell.inSplit = isInMultiWindowMode
        com.hmz.play.data.MediaBridge.start(this)
        com.hmz.play.data.Updater.check(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing "home" while already here: nothing to do, the UI stays on its current screen.
        handleExtras(intent)
    }

    /** "hmz_screen" opens a screen, "hmz_demo" shows a demo overlay (used for the automatic checks). */
    private fun handleExtras(i: Intent?) {
        i ?: return
        i.getStringExtra("hmz_screen")?.let { s -> com.hmz.play.ui.Screen.entries.firstOrNull { it.name == s }?.let { com.hmz.play.ui.NavRequest.screen = it } }
        when (i.getStringExtra("hmz_demo")) {
            "alert" -> com.hmz.play.data.LiveAlerts.demo()
            "doors" -> com.hmz.play.data.Doors.demo()
            "nav" -> com.hmz.play.data.Split.navigateTo(this, "Gare Lille Europe")
            "sign" -> com.hmz.play.ui.VtcState.signName = "M. Dupont"
            "endofday" -> com.hmz.play.ui.VtcState.endOfDayOpen = true
            "soundapp" -> getSharedPreferences("ui", MODE_PRIVATE).edit().putString("bt_music_pkg", i.getStringExtra("hmz_pkg")).apply()
            "close" -> { com.hmz.play.ui.VtcState.signName = null; com.hmz.play.ui.VtcState.endOfDayOpen = false; com.hmz.play.data.LiveAlerts.dismiss() }
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE) // find the downloaded Waze file
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE) // settings backup
        add(Manifest.permission.READ_CONTACTS) // caller name on the call screen
        add(Manifest.permission.RECEIVE_SMS) // SMS/iMessage relayed by the iPhone over car Bluetooth (MAP)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startLink() {
        if (IphoneLink.hasPermissions(this)) {
            runCatching { ContextCompat.startForegroundService(this, Intent(this, LinkService::class.java)) }
        }
        IphoneLink.start(this)
        com.hmz.play.ble.CarBluetooth.start(this)
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) com.hmz.play.data.Drive.start(this)
        com.hmz.play.data.WeatherSource.start()
        com.hmz.play.data.Doors.start(this)
        com.hmz.play.data.LiveInfo.start(this)
        com.hmz.play.data.GpsInfo.start(this)
    }

    /** Steering-wheel / head-unit media keys while HMZ Play is on screen. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val cmd = when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> com.hmz.play.ble.Cmd.NEXT
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> com.hmz.play.ble.Cmd.PREVIOUS
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_HEADSETHOOK -> com.hmz.play.ble.Cmd.TOGGLE
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> com.hmz.play.ble.Cmd.PLAY
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> com.hmz.play.ble.Cmd.PAUSE
            else -> return super.onKeyDown(keyCode, event)
        }
        IphoneLink.command(cmd)
        return true
    }
}
