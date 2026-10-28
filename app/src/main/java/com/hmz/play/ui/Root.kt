package com.hmz.play.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.LinkStatus
import com.hmz.play.data.AppEntry
import com.hmz.play.data.Apps
import com.hmz.play.data.Split
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Screen { HOME, APPS, MUSIC, NOTIFS, SETTINGS }

@Composable
fun CarRoot(locationGranted: Boolean, refreshKey: Int, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val status by IphoneLink.status.collectAsStateWithLifecycle()

    // App list + icons are loaded once, off the main thread, so the grid opens instantly.
    val apps by produceState(emptyList<AppEntry>(), refreshKey) {
        value = withContext(Dispatchers.IO) { Apps.load(ctx) }
    }

    // When the iPhone connects, open Waze on the left half automatically (once per start).
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status == LinkStatus.CONNECTED && !autoOpened && Split.autoOpen(ctx)) {
            autoOpened = true
            val inSplit = (ctx as? Activity)?.isInMultiWindowMode == true
            if (!inSplit) {
                delay(900)
                Split.openNavigation(ctx)
            }
        }
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        val compact = maxWidth < 640.dp
        if (compact) {
            CompactLayout(screen, apps, onNavigate = { screen = it }, onRequestPermissions = onRequestPermissions)
        } else {
            Row(Modifier.fillMaxSize()) {
                Sidebar(screen = screen, onNavigate = { screen = it })
                Box(Modifier.fillMaxHeight().width(1.dp).background(C.Line))
                Screens(
                    screen, apps, locationGranted, compact = false,
                    onNavigate = { screen = it }, onRequestPermissions = onRequestPermissions,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        NotificationBanner(compact = compact, onOpen = { screen = Screen.NOTIFS })
        DestinationSheet()
        UpdateBanner()
        CallOverlay()
    }
}

@Composable
private fun Screens(
    screen: Screen,
    apps: List<AppEntry>,
    locationGranted: Boolean,
    compact: Boolean,
    onNavigate: (Screen) -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = screen,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(240), initialScale = 0.96f)) togetherWith fadeOut(tween(140))
        },
        label = "screen",
    ) { s ->
        when (s) {
            Screen.HOME -> if (compact) CompactHome(onNavigate) else Dashboard(locationGranted, onNavigate)
            Screen.APPS -> AppGrid(apps, onNavigate)
            Screen.MUSIC -> NowPlayingScreen()
            Screen.NOTIFS -> NotificationsScreen()
            Screen.SETTINGS -> SettingsScreen(onRequestPermissions)
        }
    }
}

// ------------------------------------------------------------------ split-screen panel (right half)

@Composable
private fun CompactLayout(
    screen: Screen,
    apps: List<AppEntry>,
    onNavigate: (Screen) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val status by IphoneLink.status.collectAsStateWithLifecycle()
    val battery by IphoneLink.battery.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock()
            Spacer(Modifier.width(10.dp))
            val connected = status == LinkStatus.CONNECTED
            Icon(
                if (connected) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, null,
                tint = if (connected) C.Blue else C.Sub, modifier = Modifier.size(16.dp),
            )
            if (connected && battery != null) {
                Text(" $battery%", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            val onHome = screen == Screen.HOME
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Panel)
                    .tap { onNavigate(if (onHome) Screen.APPS else Screen.HOME) },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(onHome, label = "home") { home ->
                    Icon(if (home) Icons.Filled.Apps else Icons.Filled.Dashboard, null, tint = C.Text, modifier = Modifier.size(26.dp))
                }
            }
        }
        Screens(
            screen, apps, locationGranted = false, compact = true,
            onNavigate = onNavigate, onRequestPermissions = onRequestPermissions,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactHome(onNavigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val notifs by IphoneLink.notifications.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.Panel)
                .tap(0.97f) { Ui.searchOpen = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = C.Sub, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Où allons-nous ?", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        NowPlayingCard(Modifier.weight(1f).fillMaxWidth()) { onNavigate(Screen.MUSIC) }
        InfoCard(Modifier.weight(0.8f).fillMaxWidth(), onNavigate)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.tap { Split.openNavigation(ctx) }) { GlyphTile(Icons.Filled.Navigation, C.Blue, 58.dp) }
            Box(Modifier.tap { onNavigate(Screen.MUSIC) }) { GlyphTile(Icons.Filled.MusicNote, C.Pink, 58.dp) }
            Box(Modifier.tap { onNavigate(Screen.NOTIFS) }) {
                GlyphTile(Icons.AutoMirrored.Filled.Chat, C.Green, 58.dp)
                if (notifs.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(20.dp).clip(RoundedCornerShape(50)).background(C.Red),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (notifs.size > 9) "9+" else "${notifs.size}", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
