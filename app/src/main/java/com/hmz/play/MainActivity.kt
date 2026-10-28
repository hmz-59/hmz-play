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
        Shell.hmzVisible = false
        SplitService.instance?.refresh()
    }

    override fun onResume() {
        super.onResume()
        refreshKey++ // reload app list (new installs)
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
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startLink() {
        if (IphoneLink.hasPermissions(this)) {
            runCatching { ContextCompat.startForegroundService(this, Intent(this, LinkService::class.java)) }
        }
        IphoneLink.start(this)
    }
}
