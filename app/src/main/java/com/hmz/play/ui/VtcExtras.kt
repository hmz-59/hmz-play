package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.data.Drive
import com.hmz.play.data.Vtc
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private val GOLD2 = Color(0xFFD4B26A)
private val INK2 = Color(0xFF0B0B0D)

object VtcState {
    var signName by mutableStateOf<String?>(null)
    var signEditOpen by mutableStateOf(false)
    var endOfDayOpen by mutableStateOf(false)
}

/** Net of the day: revenue − fuel − URSSAF, empty km, waiting time. */
@Composable
fun NetCard(refresh: Any?) {
    val ctx = LocalContext.current
    val r = remember(refresh) { Vtc.dayReport(ctx) }
    var l100 by remember { mutableStateOf(Vtc.consumption(ctx)) }
    var rate by remember { mutableStateOf(Vtc.urssafRate(ctx)) }
    Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
        Text("CE QUE TU GAGNES VRAIMENT AUJOURD'HUI", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Big("Chiffre", "%.2f €".format(r.stats.revenue))
            Big("Carburant", "−%.2f €".format(r.fuelCost))
            Big("URSSAF (${"%.1f".format(rate)} %)", "−%.2f €".format(r.urssaf))
            Big("Net", "%.2f €".format(r.net), GOLD2)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${"%.0f".format(r.totalKm)} km dont ${"%.0f".format(r.emptyKm)} km à vide · ${r.idleMin / 60} h ${"%02d".format(r.idleMin % 60)} d'attente entre les courses",
            color = C.Text, fontSize = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conso ${"%.1f".format(l100)} L/100", color = C.Sub, fontSize = 14.sp)
            Step("−") { l100 = (l100 - 0.1).coerceAtLeast(2.0); Vtc.setConsumption(ctx, l100) }
            Step("+") { l100 += 0.1; Vtc.setConsumption(ctx, l100) }
            Spacer(Modifier.width(16.dp))
            Text("Taux URSSAF", color = C.Sub, fontSize = 14.sp)
            Step("−") { rate = (rate - 0.1).coerceAtLeast(0.0); Vtc.setUrssafRate(ctx, rate) }
            Step("+") { rate += 0.1; Vtc.setUrssafRate(ctx, rate) }
        }
        Text("Vérifie le taux sur ton espace URSSAF : il dépend de ton activité et des options choisies.", color = C.Sub, fontSize = 12.sp)
    }
}

@Composable
private fun Big(label: String, value: String, color: Color = C.Text) {
    Column {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.Sub, fontSize = 13.sp)
    }
}

@Composable
private fun Step(t: String, onClick: () -> Unit) {
    Text(t, color = C.Blue, fontSize = 22.sp, modifier = Modifier.tap(onClick = onClick).padding(horizontal = 8.dp))
}

/** Best (day, hour) slots from the ride log. */
@Composable
fun BestSlotsCard(refresh: Any?) {
    val ctx = LocalContext.current
    val slots = remember(refresh) { Vtc.bestSlots(ctx) }
    Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
        Text("TES CRÉNEAUX LES PLUS RENTABLES", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        if (slots.isEmpty()) Text("Il faut quelques courses enregistrées (au moins 2 par créneau) pour voir apparaître tes meilleurs créneaux.", color = C.Sub, fontSize = 15.sp)
        slots.forEach { (d, h, eph) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    "${d.getDisplayName(DayStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() }} · ${h}h–${h + 1}h",
                    color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f),
                )
                Text("%.0f €/h".format(eph), color = GOLD2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Big name sign for airport / station pickups. */
@Composable
fun PickupSign() {
    val name = VtcState.signName
    AnimatedVisibility(name != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, null) { VtcState.signName = null },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HMZ DRIVE", color = GOLD2, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 8.sp)
                Spacer(Modifier.height(24.dp))
                Text(name.orEmpty(), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 100.sp)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.width(80.dp).height(3.dp).background(GOLD2))
                Spacer(Modifier.height(16.dp))
                Text("Touchez l'écran pour fermer", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PickupSignEditor() {
    AnimatedVisibility(VtcState.signEditOpen, enter = fadeIn(), exit = fadeOut()) {
        var text by remember { mutableStateOf("") }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
                .clickable(remember { MutableInteractionSource() }, null) { VtcState.signEditOpen = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.Panel)
                    .clickable(remember { MutableInteractionSource() }, null) {}.padding(24.dp),
            ) {
                Text("Panneau client", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Le nom s'affiche en très grand, à montrer à la sortie de l'aéroport ou de la gare.", color = C.Sub, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel2).padding(14.dp)) {
                    if (text.isEmpty()) Text("M. Dupont", color = C.Sub, fontSize = 22.sp)
                    BasicTextField(text, { text = it.take(30) }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 22.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GOLD2)
                        .tap(0.97f) { if (text.isNotBlank()) { VtcState.signName = text.trim(); VtcState.signEditOpen = false } }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Afficher", color = INK2, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** End of the work day: summary + cash to put aside. */
@Composable
fun EndOfDaySheet() {
    val ctx = LocalContext.current
    AnimatedVisibility(VtcState.endOfDayOpen, enter = fadeIn(), exit = fadeOut()) {
        val r = remember { Vtc.dayReport(ctx) }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f))
                .clickable(remember { MutableInteractionSource() }, null) { VtcState.endOfDayOpen = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(INK2).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Fin de journée", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(4.dp))
                Text("HMZ DRIVE", color = GOLD2, fontSize = 15.sp, letterSpacing = 5.sp)
                Spacer(Modifier.height(20.dp))
                Text("%.2f €".format(r.stats.revenue), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("net estimé %.2f €".format(r.net), color = GOLD2, fontSize = 18.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Mini("${r.stats.rides}", "courses")
                    Mini("%.0f".format(r.totalKm), "km")
                    Mini("${r.stats.minutes / 60}h${"%02d".format(r.stats.minutes % 60)}", "en course")
                    Mini("%.2f".format(r.stats.perKm), "€/km")
                }
                Spacer(Modifier.height(18.dp))
                Text("Espèces à mettre de côté : %.2f €".format(r.stats.cash), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("À prévoir pour l'URSSAF : %.2f €".format(r.urssaf), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(Modifier.height(20.dp))
                Text("Bonne soirée.", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Mini(v: String, l: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(l, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

/** After 4 h of driving without a 20-minute stop. */
@Composable
fun BreakReminder() {
    val since by Drive.sinceBreak.collectAsStateWithLifecycle()
    var dismissedAt by remember { mutableStateOf(0L) }
    val show = since > 4 * 3600 && since - dismissedAt > 3600
    AnimatedVisibility(show, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Box(Modifier.fillMaxSize().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(C.Orange).tap(0.96f) { dismissedAt = since }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("☕  ${since / 3600} h de conduite : une pause de 20 min est conseillée", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
