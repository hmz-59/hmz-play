package com.hmz.play.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmz.play.data.AppEntry
import com.hmz.play.data.Apps
import com.hmz.play.data.Shell
import com.hmz.play.data.Split

private sealed interface GridItem {
    val label: String

    data class Builtin(override val label: String, val icon: ImageVector, val color: Color, val action: () -> Unit) : GridItem
    data class App(val entry: AppEntry) : GridItem {
        override val label: String get() = entry.label
    }
}

@Composable
fun AppGrid(apps: List<AppEntry>, onNavigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val builtins = listOf(
        GridItem.Builtin("Itinéraire", Icons.Filled.Navigation, C.Blue) { Split.openNavigation(ctx) },
        GridItem.Builtin("Musique", Icons.Filled.MusicNote, C.Pink) { onNavigate(Screen.MUSIC) },
        GridItem.Builtin("Messages", Icons.AutoMirrored.Filled.Chat, C.Green) { onNavigate(Screen.NOTIFS) },
        GridItem.Builtin("Réglages", Icons.Filled.Settings, Color(0xFF636366)) { onNavigate(Screen.SETTINGS) },
    )
    val items: List<GridItem> = builtins + apps.map { GridItem.App(it) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        val cols = when {
            maxWidth < 560.dp -> 3
            maxWidth < 760.dp -> 4
            else -> 5
        }
        val rows = if (maxHeight < 360.dp) 1 else 2
        val pages = items.chunked(cols * rows)
        val pager = rememberPagerState { pages.size }

        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                val pageItems = pages[page]
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val item = pageItems.getOrNull(r * cols + c)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (item != null) GridIcon(item) {
                                        when (item) {
                                            is GridItem.Builtin -> item.action()
                                            is GridItem.App -> Shell.open(ctx, item.entry.pkg)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages.size) { i ->
                        val color by animateColorAsState(if (i == pager.currentPage) C.Text else C.Line, label = "dot")
                        Box(Modifier.padding(horizontal = 5.dp).size(8.dp).clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

@Composable
private fun GridIcon(item: GridItem, onClick: () -> Unit) {
    Column(
        Modifier.tap(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val size = 84.dp
        when (item) {
            is GridItem.Builtin -> GlyphTile(item.icon, item.color, size)
            is GridItem.App -> Image(
                bitmap = item.entry.icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(C.Panel2),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.label,
            color = C.Text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = 104.dp, height = 20.dp),
        )
    }
}
