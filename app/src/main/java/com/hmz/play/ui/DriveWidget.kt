package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.data.Drive
import com.hmz.play.data.NavInfo

/** Speed (GPS), today's km and driving time, and Waze's next instruction when available. */
@Composable
fun DriveWidget(modifier: Modifier = Modifier) {
    val d by Drive.state.collectAsStateWithLifecycle()
    val g by NavInfo.guidance.collectAsStateWithLifecycle()
    val speed by animateIntAsState(d.speedKmh, tween(600), label = "speed")
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val limit = androidx.compose.runtime.remember { Drive.speedAlert(ctx) }
    val over = limit > 0 && d.speedKmh > limit
    Column(modifier.card().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (d.hasFix) "$speed" else "–", color = if (over) C.Orange else C.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("km/h", color = C.Sub, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("%.1f km".format(d.km), color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                val m = d.movingSec / 60
                Text("aujourd'hui · ${m / 60} h ${"%02d".format(m % 60)}", color = C.Sub, fontSize = 13.sp)
            }
        }
        AnimatedVisibility(g != null) {
            val gg = g ?: return@AnimatedVisibility
            Column {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlyphTile(Icons.Filled.Navigation, C.Blue, 34.dp)
                    Column(Modifier.fillMaxWidth()) {
                        Text(gg.title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (gg.text.isNotBlank()) Text(gg.text, color = C.Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

