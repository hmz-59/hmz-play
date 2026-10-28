package com.hmz.play.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hmz.play.data.AppEntry
import com.hmz.play.data.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { HOME, APPS, MUSIC, NOTIFS, SETTINGS }

@Composable
fun CarRoot(locationGranted: Boolean, refreshKey: Int, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // App list + icons are loaded once, off the main thread, so the grid opens instantly.
    val apps by produceState(emptyList<AppEntry>(), refreshKey) {
        value = withContext(Dispatchers.IO) { Apps.load(ctx) }
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                screen = screen,
                onNavigate = { screen = it },
            )
            Box(Modifier.fillMaxHeight().width(1.dp).background(C.Line))
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(240), initialScale = 0.96f)) togetherWith
                        fadeOut(tween(140))
                },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.HOME -> Dashboard(locationGranted, onNavigate = { screen = it })
                    Screen.APPS -> AppGrid(apps, onNavigate = { screen = it })
                    Screen.MUSIC -> NowPlayingScreen()
                    Screen.NOTIFS -> NotificationsScreen()
                    Screen.SETTINGS -> SettingsScreen(onRequestPermissions)
                }
            }
        }
        NotificationBanner(onOpen = { screen = Screen.NOTIFS })
        CallOverlay()
    }
}
