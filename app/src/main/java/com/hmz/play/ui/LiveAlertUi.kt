package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.data.LiveAlerts
import com.hmz.play.data.LiveItem
import com.hmz.play.data.Split
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val SHOW_MS = 10_000

/** Big, readable reminder: what, exact time, where, in how long. Stays 10 s, tap OK to close. */
@Composable
fun LiveAlertOverlay() {
    val ctx = LocalContext.current
    val a by LiveAlerts.current.collectAsStateWithLifecycle()
    AnimatedVisibility(a != null, enter = fadeIn(), exit = fadeOut()) {
        val al = a ?: return@AnimatedVisibility
        val color = when (al.kind) {
            LiveItem.Kind.TRAIN -> Color(0xFF0A84FF)
            LiveItem.Kind.FLIGHT -> Color(0xFFBF5AF2)
            LiveItem.Kind.EVENT -> Color(0xFFFF9F0A)
        }
        val icon = when (al.kind) { LiveItem.Kind.TRAIN -> "🚄"; LiveItem.Kind.FLIGHT -> "✈️"; LiveItem.Kind.EVENT -> "🎟️" }
        val bar = remember(al) { Animatable(1f) }
        LaunchedEffect(al) {
            // stale (shown while HMZ Play was hidden): skip it
            if (Duration.between(al.at, LocalDateTime.now()).toMinutes() > 2) { LiveAlerts.dismiss(); return@LaunchedEffect }
            launch { bar.animateTo(0f, tween(SHOW_MS, easing = LinearEasing)) }
            delay(SHOW_MS.toLong()) // real 10 s, whatever the system animation speed
            LiveAlerts.dismiss()
        }
        val left = Duration.between(LocalDateTime.now(), al.at).toMinutes().coerceAtLeast(0)
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                .clickable(remember { MutableInteractionSource() }, null) { LiveAlerts.dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.animateEnterExit(enter = scaleIn(initialScale = 0.92f))
                    .widthIn(max = 820.dp).fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(30.dp)).background(Color(0xFF111114)),
            ) {
                // coloured header with the countdown
                Row(
                    Modifier.fillMaxWidth().background(color).padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$icon  ${al.headline}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (left == 0L) "MAINTENANT" else "dans $left min", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%02d:%02d".format(al.at.hour, al.at.minute), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.padding(horizontal = 10.dp))
                        Text(al.place, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    al.lines.forEach {
                        Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(color)
                                .tap(0.96f) {
                                    if (al.lat != null && al.lon != null && !al.lat.isNaN()) Split.navigateToCoords(ctx, al.lat, al.lon, al.place)
                                    else Split.navigateTo(ctx, al.place)
                                    LiveAlerts.dismiss()
                                }.padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Y aller", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2C2C2E))
                                .tap(0.96f) { LiveAlerts.dismiss() }.padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                // time left on screen
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF2C2C2E))) {
                    Box(Modifier.fillMaxWidth(bar.value).fillMaxHeight().background(color))
                }
            }
        }
    }
}

/** Settings: reminders on/off, when, sound, test. */
@Composable
fun LiveAlertSettings() {
    val ctx = LocalContext.current
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LiveAlerts.enabled(ctx)) }
    var offs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LiveAlerts.offsets(ctx)) }
    var snd by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LiveAlerts.sound(ctx)) }
    Column {
        Text("Rappels plein écran (gros trains, avions, entrées et sorties d'événements)", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (on) "Rappels : oui" else "Rappels : non", if (on) C.Green else C.Panel2) { on = !on; LiveAlerts.setEnabled(ctx, on) }
            PillButton(if (snd) "Bip : oui" else "Bip : non", if (snd) C.Green else C.Panel2) { snd = !snd; LiveAlerts.setSound(ctx, snd) }
            PillButton("Tester", C.Blue) { LiveAlerts.demo() }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Me prévenir", color = C.Sub, fontSize = 14.sp)
            LiveAlerts.ALL_OFFSETS.forEach { m ->
                PillButton("$m min avant", if (m in offs) C.Blue else C.Panel2) {
                    offs = if (m in offs) offs - m else offs + m
                    if (offs.isEmpty()) offs = setOf(30)
                    LiveAlerts.setOffsets(ctx, offs)
                }
            }
        }
    }
}
