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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.data.LiveInfo
import com.hmz.play.data.LiveItem
import com.hmz.play.data.Split

private fun kindColor(k: LiveItem.Kind) = when (k) {
    LiveItem.Kind.TRAIN -> Color(0xFF0A84FF)
    LiveItem.Kind.FLIGHT -> Color(0xFFBF5AF2)
    LiveItem.Kind.EVENT -> Color(0xFFFF9F0A)
}
private fun kindIcon(k: LiveItem.Kind) = when (k) {
    LiveItem.Kind.TRAIN -> "🚄"
    LiveItem.Kind.FLIGHT -> "✈️"
    LiveItem.Kind.EVENT -> "🎟️"
}

/** Trains, flights and events that can bring rides, with exact times and places. */
@Composable
fun LiveScreen() {
    val ctx = LocalContext.current
    val items by LiveInfo.items.collectAsStateWithLifecycle()
    val status by LiveInfo.status.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<LiveItem.Kind?>(null) }
    var onlyBig by remember { mutableStateOf(false) }
    val shown = items.filter { (filter == null || it.kind == filter) && (!onlyBig || it.big) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Affluence", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PillButton("Actualiser", C.Panel2) { LiveInfo.refreshNow() }
        }
        Spacer(Modifier.padding(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Tout", if (filter == null) C.Blue else C.Panel2) { filter = null }
            PillButton("🚄 Trains", if (filter == LiveItem.Kind.TRAIN) C.Blue else C.Panel2) { filter = LiveItem.Kind.TRAIN }
            PillButton("✈️ Vols", if (filter == LiveItem.Kind.FLIGHT) C.Blue else C.Panel2) { filter = LiveItem.Kind.FLIGHT }
            PillButton("🎟️ Événements", if (filter == LiveItem.Kind.EVENT) C.Blue else C.Panel2) { filter = LiveItem.Kind.EVENT }
            PillButton("Gros uniquement", if (onlyBig) C.Orange else C.Panel2) { onlyBig = !onlyBig }
        }
        status.values.filter { it.isNotBlank() }.forEach { Text(it, color = C.Sub, fontSize = 14.sp) }
        Spacer(Modifier.padding(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (shown.isEmpty()) item { Text("Rien de prévu pour l'instant.", color = C.Sub, fontSize = 16.sp) }
            items(shown.take(80)) { it ->
                Row(
                    Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02d:%02d".format(it.time.hour, it.time.minute), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
                    Box(Modifier.size(4.dp, 40.dp).clip(RoundedCornerShape(2.dp)).background(kindColor(it.kind)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${kindIcon(it.kind)}  ${it.title}", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(it.place, it.detail).filter { s -> s.isNotBlank() }.joinToString(" · "),
                            color = if (it.detail.startsWith("+") || it.detail == "annulé") C.Orange else C.Sub,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (it.big) Text("AFFLUENCE", color = C.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                    PillButton("Y aller", C.Blue) {
                        if (it.lat != null && it.lon != null && !it.lat.isNaN()) Split.navigateToCoords(ctx, it.lat, it.lon, it.place)
                        else Split.navigateTo(ctx, it.place)
                    }
                }
            }
        }
    }
}

/** One-line alert for the home screen: the next big arrival/event in the next 90 minutes. */
@Composable
fun LiveHeadline(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val items by LiveInfo.items.collectAsStateWithLifecycle()
    val h = LiveInfo.headline(items) ?: return
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Panel).tap(0.97f, onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 34.dp).clip(RoundedCornerShape(2.dp)).background(kindColor(h.kind)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${kindIcon(h.kind)}  %02d:%02d · ${h.place}".format(h.time.hour, h.time.minute), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.title + if (h.detail.isNotBlank()) " · ${h.detail}" else "", color = C.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Free API keys for trains (SNCF) and flights (AirLabs). Stored only on the head unit. */
@Composable
fun LiveKeysCard() {
    val ctx = LocalContext.current
    var sncf by remember { mutableStateOf(LiveInfo.sncfKey(ctx)) }
    var air by remember { mutableStateOf(LiveInfo.airKey(ctx)) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
        Text("Affluence : trains, vols, événements", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Les événements de la MEL marchent sans rien faire. Pour les trains et les avions, il faut une clé gratuite (inscription par e-mail) : SNCF sur numerique.sncf.com (API SNCF) et AirLabs sur airlabs.co. Colle-les ici, elles restent sur l'autoradio.",
            color = C.Sub, fontSize = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        KeyField("Clé SNCF", sncf) { sncf = it; saved = false }
        Spacer(Modifier.height(8.dp))
        KeyField("Clé AirLabs", air) { air = it; saved = false }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (saved) "Enregistré ✓" else "Enregistrer", C.Blue) { LiveInfo.setKeys(ctx, sncf, air); saved = true }
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = C.Sub, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel2).padding(12.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value, { onChange(it.trim()) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = C.Text, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Blue), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
