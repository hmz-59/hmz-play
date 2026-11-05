package com.hmz.play.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.filled.LocalGasStation
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
        GridItem.Builtin("Carburant", Icons.Filled.LocalGasStation, C.Orange) { onNavigate(Screen.FUEL) },
        GridItem.Builtin("Réglages", Icons.Filled.Settings, Color(0xFF636366)) { onNavigate(Screen.SETTINGS) },
    )
    // User order first (long press an icon to rearrange), then the default order.
    val order = com.hmz.play.data.Prefs.appOrder
    val sortedApps = apps.sortedBy { a -> order.indexOf(a.pkg).let { if (it < 0) Int.MAX_VALUE else it } }
    val items: List<GridItem> = builtins + sortedApps.map { GridItem.App(it) }
    var editing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    fun move(pkg: String, delta: Int) {
        val list = sortedApps.map { it.pkg }.toMutableList()
        val i = list.indexOf(pkg)
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.add(j, list.removeAt(i))
        com.hmz.play.data.Prefs.setOrder(list)
    }

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
            if (editing) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Utilise ◀ ▶ pour déplacer les applis", color = C.Sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    PillButton("Terminé", C.Blue) { editing = false }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                val pageItems = pages[page]
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val item = pageItems.getOrNull(r * cols + c)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (item != null) GridIcon(
                                        item,
                                        editing = editing,
                                        onLongPress = { editing = true },
                                        onMove = { d -> (item as? GridItem.App)?.let { move(it.entry.pkg, d) } },
                                    ) {
                                        if (editing) return@GridIcon
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GridIcon(
    item: GridItem,
    editing: Boolean,
    onLongPress: () -> Unit,
    onMove: (Int) -> Unit,
    onClick: () -> Unit,
) {
    // iOS-like wiggle while rearranging
    val wiggle = androidx.compose.animation.core.rememberInfiniteTransition(label = "wiggle")
    val angle by wiggle.animateFloat(
        -2.5f, 2.5f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(140),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "angle",
    )
    Column(
        Modifier
            .graphicsLayer { rotationZ = if (editing && item is GridItem.App) angle else 0f }
            .combinedClickable(
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(6.dp),
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
        if (editing && item is GridItem.App) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("◀", color = C.Blue, fontSize = 22.sp, modifier = Modifier.tap { onMove(-1) }.padding(4.dp))
                Text("▶", color = C.Blue, fontSize = 22.sp, modifier = Modifier.tap { onMove(1) }.padding(4.dp))
            }
        }
    }
}
