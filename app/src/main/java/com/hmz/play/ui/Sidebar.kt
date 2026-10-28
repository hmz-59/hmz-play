package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.LinkStatus
import com.hmz.play.data.Apps
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Sidebar(screen: Screen, onNavigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val status by IphoneLink.status.collectAsStateWithLifecycle()
    val battery by IphoneLink.battery.collectAsStateWithLifecycle()
    val notifs by IphoneLink.notifications.collectAsStateWithLifecycle()
    val track by IphoneLink.track.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxHeight().width(104.dp).background(C.Bg).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Clock()
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val connected = status == LinkStatus.CONNECTED
            Icon(
                if (connected) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                null,
                tint = if (connected) C.Blue else C.Sub,
                modifier = Modifier.size(15.dp),
            )
            if (connected && battery != null) {
                Spacer(Modifier.width(3.dp))
                Text("${battery}%", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DockButton(Icons.Filled.Navigation, C.Blue) { Apps.openNavigation(ctx) }
            DockButton(Icons.Filled.MusicNote, C.Pink, active = track.playing) { onNavigate(Screen.MUSIC) }
            DockButton(Icons.AutoMirrored.Filled.Chat, C.Green, badge = notifs.size) { onNavigate(Screen.NOTIFS) }
        }

        Spacer(Modifier.weight(1f))

        // The "home" button: dashboard <-> app grid, like the CarPlay one.
        val onHome = screen == Screen.HOME
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(C.Panel)
                .tap { onNavigate(if (onHome) Screen.APPS else Screen.HOME) },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(onHome, label = "home") { home ->
                Icon(
                    if (home) Icons.Filled.Apps else Icons.Filled.Dashboard,
                    null,
                    tint = C.Text,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    color: Color,
    badge: Int = 0,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(Modifier.tap(onClick = onClick)) {
        GlyphTile(icon, color, 64.dp)
        AnimatedVisibility(
            visible = badge > 0,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(C.Red),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (badge > 9) "9+" else "$badge", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 9.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(C.Text),
            )
        }
    }
}

@Composable
private fun Clock() {
    val fmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
    val time by produceState(fmt.format(Date())) {
        while (true) {
            value = fmt.format(Date())
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    Text(time, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
}
