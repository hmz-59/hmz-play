package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.IncomingCall
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.PhoneNotif
import kotlinx.coroutines.delay

/** Slide-down banner for new iPhone notifications. */
@Composable
fun NotificationBanner(onOpen: () -> Unit) {
    var current by remember { mutableStateOf<PhoneNotif?>(null) }
    var last by remember { mutableStateOf<PhoneNotif?>(null) }

    LaunchedEffect(Unit) {
        IphoneLink.banners.collect {
            current = it
            last = it
        }
    }
    LaunchedEffect(current) {
        if (current != null) {
            delay(5500)
            current = null
        }
    }

    Box(Modifier.fillMaxWidth().padding(top = 14.dp, start = 104.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut(tween(220)),
        ) {
            val n = current ?: last ?: return@AnimatedVisibility
            Row(
                Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.Panel2)
                    .tap(0.97f) {
                        current = null
                        onOpen()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(n.appName, 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(n.message, color = C.Text.copy(alpha = 0.8f), fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                Text(n.appName, color = C.Sub, fontSize = 13.sp)
            }
        }
    }
}

/** Full-screen incoming call card with answer / decline, sent to the iPhone via ANCS. */
@Composable
fun CallOverlay() {
    val call by IphoneLink.call.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<IncomingCall?>(null) }
    if (call != null) last = call

    AnimatedVisibility(
        visible = call != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(200)),
    ) {
        val c = call ?: last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(remember { MutableInteractionSource() }, null) {},
            contentAlignment = Alignment.Center,
        ) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val ring by pulse.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "ring")
            val ringAlpha by pulse.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "alpha")

            Column(
                Modifier
                    .animateEnterExit(enter = scaleIn(initialScale = 0.9f))
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(C.Panel)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(96.dp)
                            .graphicsLayer { scaleX = ring; scaleY = ring; alpha = ringAlpha }
                            .clip(CircleShape).background(C.Green),
                    )
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(C.Panel2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, null, tint = C.Sub, modifier = Modifier.size(58.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(c.caller, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Appel entrant · iPhone", color = C.Sub, fontSize = 16.sp)
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(70.dp)) {
                    CallButton(Icons.Filled.CallEnd, C.Red, "Refuser") { IphoneLink.answerCall(false) }
                    CallButton(Icons.Filled.Call, C.Green, "Accepter") { IphoneLink.answerCall(true) }
                }
            }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, color: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(color).tap(0.88f, onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = C.Text, fontSize = 15.sp)
    }
}
