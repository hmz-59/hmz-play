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

class MainActivity : ComponentActivity() {

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

        // Reload the app grid only when an app is installed or removed (not on every resume: faster).
        ContextCompat.registerReceiver(this, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) { refreshKey++ }
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
        SplitService.instance?.refresh()
    }

    override fun onStop() {
        super.onStop()
        com.hmz.play.data.Backup.save(this)
        com.hmz.play.data.BlackBox.flush()
        Shell.hmzVisible = false
        SplitService.instance?.refresh()
    }

    override fun onResume() {
        super.onResume()
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
