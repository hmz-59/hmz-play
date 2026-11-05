package com.hmz.play.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmz.play.data.Ride
import com.hmz.play.data.Vtc
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GOLD = Color(0xFFD4B26A)
private val INK = Color(0xFF0B0B0D)

fun platformColor(p: String) = when (p) {
    "Bolt" -> Color(0xFF34D186)
    "Heetch" -> Color(0xFFFF4F87)
    "Uber" -> Color(0xFFFFFFFF)
    else -> GOLD
}

object VtcUi { var startOpen by mutableStateOf(false) }

/** Gold "Course" button (opens the start sheet), or the running ride's end button. */
@Composable
fun RideButton(compact: Boolean = false) {
    val ctx = LocalContext.current
    val label = if (Vtc.active) "Terminer" else "Course"
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (Vtc.active) C.Red else GOLD)
            .tap(0.92f) { if (Vtc.active) Vtc.end(ctx) else VtcUi.startOpen = true }
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (Vtc.active) Color.White else INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ start

@Composable
fun RideStartSheet() {
    val ctx = LocalContext.current
    AnimatedVisibility(VtcUi.startOpen, enter = fadeIn(), exit = fadeOut()) {
        var plat by remember { mutableStateOf(Vtc.lastPlatform(ctx)) }
        var dest by remember { mutableStateOf("") }
        Scrim({ VtcUi.startOpen = false }) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.Panel).padding(24.dp)) {
                Text("Client à bord ?", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Le mode course démarre : écran privé, musique adoucie, chrono et km.", color = C.Sub, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                Text("PLATEFORME", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Vtc.PLATFORMS.forEach { p ->
                        val sel = p == plat
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(if (sel) C.Panel2 else Color.Transparent)
                                .tap(0.94f) { plat = p }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(platformColor(p)))
                            Spacer(Modifier.width(8.dp))
                            Text(p, color = C.Text, fontSize = 17.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("DESTINATION (facultatif)", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(dest, { dest = it }, "Adresse, gare, aéroport…", Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GOLD)
                        .tap(0.97f) { VtcUi.startOpen = false; Vtc.start(ctx, plat, dest) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Démarrer la course", color = INK, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ during

/** Thin bar at the top while a ride runs: platform, chrono, km, Terminer. */
@Composable
fun RideBar() {
    val ctx = LocalContext.current
    AnimatedVisibility(
        Vtc.active,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
        val sec = ((now - Vtc.startedAt) / 1000).toInt()
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(INK.copy(alpha = 0.92f))
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(platformColor(Vtc.platform)))
                Spacer(Modifier.width(10.dp))
                Text("En course · ${Vtc.platform}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Text("%d:%02d".format(sec / 60, sec % 60), color = GOLD, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text("%.1f km".format(Vtc.km(ctx)), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                Spacer(Modifier.width(14.dp))
                RideButton(compact = true)
            }
        }
    }
}

/** HMZ DRIVE welcome card for the passenger, a few seconds at pickup. */
@Composable
fun WelcomeCard() {
    LaunchedEffect(Vtc.showWelcome) { if (Vtc.showWelcome) { delay(6000); Vtc.showWelcome = false } }
    AnimatedVisibility(Vtc.showWelcome, enter = fadeIn(tween(500)), exit = fadeOut(tween(700))) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(INK, Color(0xFF1A1712))))
                .clickable(remember { MutableInteractionSource() }, null) { Vtc.showWelcome = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bienvenue à bord", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.width(60.dp).height(2.dp).background(GOLD))
                Spacer(Modifier.height(14.dp))
                Text("HMZ DRIVE", color = GOLD, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
                Spacer(Modifier.height(6.dp))
                Text(Vtc.SLOGAN, color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp)
                if (Vtc.destination.isNotBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text("Destination : ${Vtc.destination}", color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp)
                }
                Spacer(Modifier.height(16.dp))
                WeatherLabel(18, Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

// ------------------------------------------------------------------ end

@Composable
fun RideEndSheet() {
    val ctx = LocalContext.current
    val ride = Vtc.pending
    AnimatedVisibility(ride != null, enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
        val r = ride ?: return@AnimatedVisibility
        var price by remember(r.start) { mutableStateOf("") }
        var pay by remember(r.start) { mutableStateOf(r.payment) }
        Scrim({}) {
            Row(
                Modifier.widthIn(max = 820.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.Panel).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Course terminée", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${r.platform} · ${r.minutes} min · ${"%.1f".format(r.km)} km",
                        color = C.Sub, fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("PRIX DE LA COURSE", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(price, { v -> price = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7) }, "0,00", Modifier.width(160.dp), number = true)
                        Text("  €", color = C.Text, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("PAIEMENT", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Vtc.PAYMENTS.forEach { p -> PillButton(p, if (p == pay) C.Blue else C.Panel2) { pay = p } }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(GOLD)
                                .tap(0.97f) {
                                    Vtc.save(ctx, r.copy(price = price.replace(',', '.').toDoubleOrNull() ?: 0.0, payment = pay))
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Enregistrer", color = INK, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        PillButton("Ignorer", C.Panel2) { Vtc.discardPending() }
                    }
                }
                // Direct ride only: invite the client to book HMZ DRIVE directly next time.
                if (r.platform == "Direct") {
                    Column(
                        Modifier.width(230.dp).clip(RoundedCornerShape(22.dp)).background(INK).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("HMZ DRIVE", color = GOLD, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                        Spacer(Modifier.height(10.dp))
                        val qr = remember { qrBitmap(Vtc.SITE, 512) }
                        if (qr != null) {
                            Image(qr.asImageBitmap(), null, Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Votre prochain trajet,\nréservez en direct", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                        Text("hmzdrive.com", color = GOLD, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ journal screen

@Composable
fun VtcScreen() {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val all = remember(refresh, Vtc.pending) { Vtc.rides(ctx) }
    val today = all.filter { it.day == java.time.LocalDate.now() }
    val week = all.filter { it.day.isAfter(java.time.LocalDate.now().minusDays(7)) }
    val st = Vtc.stats(today)
    val wk = Vtc.stats(week)
    var goal by remember { mutableStateOf(Vtc.goal(ctx)) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val progress by animateFloatAsState((st.revenue / goal).toFloat().coerceIn(0f, 1f), tween(800), label = "goal")

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Courses", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("HMZ DRIVE · ${Vtc.SLOGAN}", color = GOLD, fontSize = 15.sp)
                }
                RideButton()
            }
        }
        item {
            Row(Modifier.fillMaxWidth().card().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AUJOURD'HUI", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text("%.2f €".format(st.revenue), color = C.Text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${st.rides} course${if (st.rides > 1) "s" else ""} · ${"%.0f".format(st.km)} km · ${st.minutes / 60} h ${"%02d".format(st.minutes % 60)}",
                        color = C.Sub, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(C.Panel2)) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(50)).background(GOLD))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Objectif $goal €", color = C.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("−", color = C.Blue, fontSize = 24.sp, modifier = Modifier.tap { goal = (goal - 10).coerceAtLeast(10); Vtc.setGoal(ctx, goal) }.padding(horizontal = 12.dp))
                        Text("+", color = C.Blue, fontSize = 24.sp, modifier = Modifier.tap { goal += 10; Vtc.setGoal(ctx, goal) }.padding(horizontal = 12.dp))
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Kpi("€ / km", if (st.perKm > 0) "%.2f".format(st.perKm) else "–")
                    Kpi("€ / heure", if (st.perHour > 0) "%.0f".format(st.perHour) else "–")
                    Kpi("Espèces", "%.2f €".format(st.cash))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().card().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("7 DERNIERS JOURS", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(
                        "%.2f € · %d courses · %.0f km · %.2f €/km".format(wk.revenue, wk.rides, wk.km, wk.perKm),
                        color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    )
                    exportMsg?.let { Text(it, color = C.Green, fontSize = 14.sp) }
                }
                PillButton("Exporter le mois", C.Panel2) {
                    val f = Vtc.exportMonth(ctx)
                    exportMsg = if (f != null) "Enregistré dans Documents/HMZ Play/${f.name}" else "Export impossible (autorisation Fichiers ?)"
                }
            }
        }
        if (all.isEmpty()) item { Text("Appuie sur « Course » quand un client monte : tes courses s'afficheront ici.", color = C.Sub, fontSize = 16.sp) }
        items(all.take(40), key = { it.start }) { r -> RideRow(r) }
    }
}

@Composable
private fun Kpi(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(value, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.Sub, fontSize = 13.sp)
    }
}

@Composable
private fun RideRow(r: Ride) {
    val tf = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE) }
    Row(Modifier.fillMaxWidth().card().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(platformColor(r.platform)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tf.format(Date(r.start)), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${r.platform} · ${r.minutes} min · ${"%.1f".format(r.km)} km · ${r.payment}" + if (r.destination.isNotBlank()) " · ${r.destination}" else "",
                color = C.Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("%.2f €".format(r.price), color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ helpers

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable(remember { MutableInteractionSource() }, null) {}) { content() }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, number: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(C.Panel2).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = C.Sub, fontSize = 20.sp)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = C.Text, fontSize = 20.sp),
            cursorBrush = SolidColor(C.Blue),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** QR code (ZXing) as a bitmap: black on white. */
fun qrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
    )
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
