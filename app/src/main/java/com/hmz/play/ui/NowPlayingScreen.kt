package com.hmz.play.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.Cmd
import com.hmz.play.ble.IphoneLink

@Composable
fun NowPlayingScreen() {
    val track by IphoneLink.track.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val narrow = maxWidth < 600.dp
        if (narrow) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Artwork(track, minOf(maxHeight * 0.32f, maxWidth * 0.5f), 18.dp)
                Spacer(Modifier.height(18.dp))
                TrackDetails(track)
            }
        } else {
            val art = minOf(maxHeight * 0.82f, maxWidth * 0.38f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track, art, 22.dp)
                Spacer(Modifier.width(36.dp))
                Box(Modifier.weight(1f)) { TrackDetails(track) }
            }
        }
    }
}

@Composable
private fun TrackDetails(track: com.hmz.play.ble.Track) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    track.playerName.ifBlank { "iPhone" }.uppercase(),
                    color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                AnimatedContent(
                    track.title to track.artist,
                    transitionSpec = { (fadeIn() + slideInVertically { it / 6 }) togetherWith fadeOut() },
                    label = "title",
                ) { (t, a) ->
                    Column {
                        Text(
                            t.ifBlank { "Aucune musique" },
                            color = C.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 36.sp,
                        )
                        Text(
                            a.ifBlank { "Lance une musique sur ton iPhone" },
                            color = C.Pink, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
                ProgressBar(track, height = 6.dp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    val pos = rememberPosition(track)
                    Text(fmtTime(pos), color = C.Sub, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    if (track.duration > 0f) Text("-" + fmtTime(track.duration - pos), color = C.Sub, fontSize = 13.sp)
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(Icons.AutoMirrored.Filled.VolumeDown, 30.dp) { IphoneLink.command(Cmd.VOLUME_DOWN) }
                    ControlButton(Icons.Filled.SkipPrevious, 52.dp) { IphoneLink.command(Cmd.PREVIOUS) }
                    Box(
                        Modifier.size(92.dp).clip(CircleShape).background(Color.White)
                            .tap(0.88f) { IphoneLink.command(Cmd.TOGGLE) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (track.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = Color.Black, modifier = Modifier.size(52.dp),
                        )
                    }
                    ControlButton(Icons.Filled.SkipNext, 52.dp) { IphoneLink.command(Cmd.NEXT) }
                    ControlButton(Icons.AutoMirrored.Filled.VolumeUp, 30.dp) { IphoneLink.command(Cmd.VOLUME_UP) }
                }
            }
}
