package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.data.UpdateState
import com.hmz.play.data.Updater

/** Small pill at the bottom right when a new version is ready: one tap downloads and installs it. */
@Composable
fun UpdateBanner() {
    val ctx = LocalContext.current
    val st by Updater.state.collectAsStateWithLifecycle()
    val show = st is UpdateState.Available || st is UpdateState.Downloading || st is UpdateState.Failed
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(show, enter = slideInVertically { it * 2 }, exit = slideOutVertically { it * 2 }) {
            val (text, color) = when (val s = st) {
                is UpdateState.Available -> "Mise à jour ${s.version} : installer" to C.Blue
                is UpdateState.Downloading -> "Téléchargement… ${s.percent} %" to C.Panel2
                is UpdateState.Failed -> s.reason to C.Red
                else -> "" to C.Panel2
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(color)
                    .tap(0.95f) {
                        if (st is UpdateState.Failed) Updater.check(ctx, force = true) else Updater.install(ctx)
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
