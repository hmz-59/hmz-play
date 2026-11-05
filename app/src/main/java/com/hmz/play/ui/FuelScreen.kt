package com.hmz.play.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.data.Fuel
import com.hmz.play.data.Split

/** Cheapest stations within 8 km, official prices (prix-carburants.gouv.fr). */
@Composable
fun FuelScreen() {
    val ctx = LocalContext.current
    var fuel by remember { mutableStateOf(Fuel.fuel(ctx)) }
    val stations by Fuel.stations.collectAsStateWithLifecycle()
    val status by Fuel.status.collectAsStateWithLifecycle()
    LaunchedEffect(fuel) { Fuel.refresh(ctx) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Carburant", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PillButton("Actualiser", C.Panel2) { Fuel.refresh(ctx) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fuel.FUELS.take(4).forEach { (k, label) ->
                PillButton(label, if (fuel == k) C.Blue else C.Panel2) { fuel = k; Fuel.setFuel(ctx, k) }
            }
        }
        Spacer(Modifier.height(12.dp))
        status?.let { Text(it, color = C.Sub, fontSize = 16.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            itemsIndexed(stations.take(15)) { i, s ->
                Row(
                    Modifier.fillMaxWidth().card().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.city} · %.1f km".format(s.distanceKm), color = C.Sub, fontSize = 15.sp)
                    }
                    Text(
                        "%.3f €".format(s.prices[fuel] ?: 0.0),
                        color = if (i == 0) C.Green else C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(14.dp))
                    PillButton("Y aller", C.Blue) { Split.navigateToCoords(ctx, s.lat, s.lon, s.name) }
                }
            }
        }
    }
}
