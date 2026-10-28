package com.hmz.play.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.PhoneNotif

@Composable
fun NotificationsScreen() {
    val notifs by IphoneLink.notifications.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (notifs.isNotEmpty()) {
                Text(
                    "Tout effacer",
                    color = C.Blue, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.tap { IphoneLink.clearNotifications() }.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (notifs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Notifications, null, tint = C.Line, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Aucune notification", color = C.Sub, fontSize = 18.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(notifs, key = { it.uid }) { n ->
                    NotifRow(n, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun NotifRow(n: PhoneNotif, modifier: Modifier) {
    Row(modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.Top) {
        AppBadge(n.appName, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(n.appName, color = C.Sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(ago(n.time), color = C.Sub, fontSize = 14.sp)
            }
            Text(n.title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.message.isNotBlank()) {
                Text(n.message, color = C.Text.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AppBadge(appName: String, size: Dp) {
    val (icon, color) = when (appName) {
        "Messages" -> Icons.AutoMirrored.Filled.Chat to C.Green
        "WhatsApp" -> Icons.Filled.Call to Color(0xFF25D366)
        "Téléphone" -> Icons.Filled.Call to C.Green
        "Mail", "Gmail" -> Icons.Filled.Email to C.Blue
        "Messenger" -> Icons.AutoMirrored.Filled.Chat to Color(0xFF0084FF)
        "Instagram" -> Icons.Filled.PhotoCamera to Color(0xFFE1306C)
        "Snapchat" -> Icons.Filled.PhotoCamera to Color(0xFFFFC800)
        "Telegram" -> Icons.Filled.Send to Color(0xFF2AABEE)
        else -> Icons.Filled.Notifications to C.Orange
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(glossy(color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

fun ago(time: Long): String {
    val m = ((System.currentTimeMillis() - time) / 60_000).toInt()
    return when {
        m < 1 -> "maintenant"
        m < 60 -> "il y a $m min"
        else -> "il y a ${m / 60} h"
    }
}
