package com.hmz.play.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.Cmd
import com.hmz.play.ble.IphoneLink
import com.hmz.play.data.Drive
import com.hmz.play.data.Split
import com.hmz.play.data.WeatherSource
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mic button: say "maison", "travail", "musique", "suivant", or any address for Waze. */
@Composable
fun VoiceButton(size: Dp = 46.dp) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim() ?: return@rememberLauncherForActivityResult
        val t = text.lowercase(Locale.FRANCE)
        when {
            "maison" in t -> Split.home(ctx)?.let { Split.navigateTo(ctx, it) } ?: toast(ctx, "Définis d'abord « Maison » dans Où allons-nous ?")
            "travail" in t || "boulot" in t -> Split.work(ctx)?.let { Split.navigateTo(ctx, it) } ?: toast(ctx, "Définis d'abord « Travail » dans Où allons-nous ?")
            t == "suivant" || "chanson suivante" in t -> IphoneLink.command(Cmd.NEXT)
            t == "précédent" -> IphoneLink.command(Cmd.PREVIOUS)
            t == "pause" || t == "stop" -> IphoneLink.command(Cmd.PAUSE)
            t == "musique" || t == "lecture" -> IphoneLink.command(Cmd.PLAY)
            else -> Split.navigateTo(ctx, text.removePrefix("aller à ").removePrefix("va à ").removePrefix("direction "))
        }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(C.Panel2).tap(0.9f) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Une adresse, « maison », « suivant »…")
            runCatching { launcher.launch(i) }.onFailure { toast(ctx, "Reconnaissance vocale indisponible sur cet autoradio (installe l'appli Google).") }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, null, tint = C.Text, modifier = Modifier.size(size * 0.5f))
    }
}

private fun toast(ctx: android.content.Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

/** "Maison" / "Travail" in one tap, when they're set. */
@Composable
fun FavoriteChips() {
    val ctx = LocalContext.current
    val home = remember { Split.home(ctx) }
    val work = remember { Split.work(ctx) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (home != null) Chip(Icons.Filled.Home, "Maison") { Split.navigateTo(ctx, home) }
        if (work != null) Chip(Icons.Filled.Work, "Travail") { Split.navigateTo(ctx, work) }
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(C.Panel.copy(alpha = 0.94f)).tap(0.94f, onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Blue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** "12° ☁️" for the side bar. */
@Composable
fun WeatherLabel(fontSize: Int = 14, color: Color = C.Text) {
    val w by WeatherSource.now.collectAsStateWithLifecycle()
    val ww = w ?: return
    Text(
        "${WeatherSource.emoji(ww.code)} ${ww.tempC}°" + if (ww.rainSoon) " 💧" else "",
        color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Medium,
    )
}

/** After 2 minutes parked: big clock, screen dimmed. Any touch brings everything back. */
@Composable
fun ParkedOverlay() {
    val ctx = LocalContext.current
    val d by Drive.state.collectAsStateWithLifecycle()
    val lastMoving by Drive.lastMoving.collectAsStateWithLifecycle()
    var dismissedAt by remember { mutableStateOf(0L) }
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) { value = SystemClock.elapsedRealtime(); delay(5000) }
    }
    val parked = d.hasFix && now - lastMoving > 120_000 && dismissedAt < lastMoving + 120_000
    DisposableEffect(parked) {
        val w = (ctx as? Activity)?.window
        w?.attributes = w?.attributes?.apply { screenBrightness = if (parked) 0.12f else -1f }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = -1f } }
    }
    AnimatedVisibility(parked, enter = fadeIn(), exit = fadeOut()) {
        val time by produceState(SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())) {
            while (true) { value = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()); delay(5000) }
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, null) { dismissedAt = SystemClock.elapsedRealtime() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                WeatherLabel(24, Color.White)
                Spacer(Modifier.height(10.dp))
                Text("%.1f km aujourd'hui".format(d.km), color = Color(0xFF8E8E93), fontSize = 18.sp)
            }
        }
    }
}
