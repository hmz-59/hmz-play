package com.hmz.play.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.Cmd
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.LinkStatus
import com.hmz.play.ble.Track
import com.hmz.play.data.Apps
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun Dashboard(locationGranted: Boolean, onNavigate: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        key(locationGranted) {
            MapCard(locationGranted, Modifier.weight(1.55f).fillMaxHeight())
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingCard(Modifier.weight(1f).fillMaxWidth()) { onNavigate(Screen.MUSIC) }
            InfoCard(Modifier.weight(0.78f).fillMaxWidth(), onNavigate)
        }
    }
}

// ------------------------------------------------------------------ map

private fun darkMapFilter(): ColorMatrixColorFilter {
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val m = ColorMatrix().apply { setSaturation(0.3f) }
    m.preConcat(invert)
    val tint = ColorMatrix(
        floatArrayOf(
            0.78f, 0f, 0f, 0f, 0f,
            0f, 0.84f, 0f, 0f, 0f,
            0f, 0f, 0.98f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    m.postConcat(tint)
    return ColorMatrixColorFilter(m)
}

@Composable
private fun MapCard(locationGranted: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    Box(modifier.clip(CardShape).background(Color(0xFF15171C))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                MapView(c).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    setTilesScaledToDpi(true)
                    overlayManager.tilesOverlay.setColorFilter(darkMapFilter())
                    overlayManager.tilesOverlay.setLoadingBackgroundColor(android.graphics.Color.rgb(21, 23, 28))
                    overlayManager.tilesOverlay.setLoadingLineColor(android.graphics.Color.rgb(21, 23, 28))
                    controller.setZoom(16.5)
                    controller.setCenter(GeoPoint(50.6292, 3.0573))
                    if (locationGranted) {
                        val provider = GpsMyLocationProvider(c).apply {
                            addLocationSource(LocationManager.NETWORK_PROVIDER)
                        }
                        val me = MyLocationNewOverlay(provider, this)
                        me.enableMyLocation()
                        me.enableFollowLocation()
                        overlays.add(me)
                    }
                    onResume()
                }
            },
            onRelease = { it.onPause(); it.onDetach() },
        )
        // Whole card opens the navigation app (Waze / Maps), like tapping the CarPlay map.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null) { Apps.openNavigation(ctx) },
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(50))
                .background(C.Panel.copy(alpha = 0.94f))
                .tap { Apps.openNavigation(ctx) }
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = C.Sub, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Où allons-nous ?", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ------------------------------------------------------------------ now playing

@Composable
fun rememberPosition(track: Track): Float {
    val now by produceState(SystemClock.elapsedRealtime(), track.playing, track.elapsedAt) {
        while (track.playing) {
            value = SystemClock.elapsedRealtime()
            delay(250)
        }
        value = SystemClock.elapsedRealtime()
    }
    return track.positionAt(now)
}

@Composable
fun Artwork(track: Track, size: Dp, corner: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(
            if (track.isEmpty) Brush.linearGradient(listOf(C.Panel2, C.Panel2)) else artBrush(track.title + track.artist),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun ProgressBar(track: Track, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    val pos = rememberPosition(track)
    val target = if (track.duration > 0f) (pos / track.duration).coerceIn(0f, 1f) else 0f
    val fraction by animateFloatAsState(target, tween(250, easing = LinearEasing), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(C.Panel2)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(C.Text))
    }
}

@Composable
private fun NowPlayingCard(modifier: Modifier, onOpen: () -> Unit) {
    val track by IphoneLink.track.collectAsStateWithLifecycle()
    Column(modifier.card().tap(0.97f, onOpen).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, 74.dp, 12.dp)
            Spacer(Modifier.width(14.dp))
            AnimatedContent(track.title to track.artist, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "t") { (t, a) ->
                Column {
                    Text(
                        t.ifBlank { "Aucune musique" },
                        color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        a.ifBlank { "Lance une musique sur ton iPhone" },
                        color = C.Sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        ProgressBar(track)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ControlButton(Icons.Filled.SkipPrevious, 40.dp) { IphoneLink.command(Cmd.PREVIOUS) }
            ControlButton(if (track.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 48.dp) {
                IphoneLink.command(Cmd.TOGGLE)
            }
            ControlButton(Icons.Filled.SkipNext, 40.dp) { IphoneLink.command(Cmd.NEXT) }
        }
    }
}

@Composable
fun ControlButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size + 20.dp).clip(CircleShape).tap(0.85f, onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(size))
    }
}

// ------------------------------------------------------------------ info card

@Composable
private fun InfoCard(modifier: Modifier, onNavigate: (Screen) -> Unit) {
    val status by IphoneLink.status.collectAsStateWithLifecycle()
    val notifs by IphoneLink.notifications.collectAsStateWithLifecycle()
    val battery by IphoneLink.battery.collectAsStateWithLifecycle()
    val phone by IphoneLink.phoneName.collectAsStateWithLifecycle()
    val latest = notifs.firstOrNull()

    Box(
        modifier.card().tap(0.97f) {
            onNavigate(if (status == LinkStatus.CONNECTED && latest != null) Screen.NOTIFS else Screen.SETTINGS)
        }.padding(18.dp),
    ) {
        if (status == LinkStatus.CONNECTED && latest != null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBadge(latest.appName, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(latest.appName.uppercase(), color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(ago(latest.time), color = C.Sub, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(latest.title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(latest.message, color = C.Sub, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            val (icon, color, title, sub) = when (status) {
                LinkStatus.CONNECTED -> Quad(Icons.Filled.PhoneIphone, C.Green, phone ?: "iPhone connecté", battery?.let { "Connecté · batterie $it %" } ?: "Connecté")
                LinkStatus.CONNECTING -> Quad(Icons.Filled.Bluetooth, C.Blue, "Connexion…", "Liaison avec l'iPhone en cours")
                LinkStatus.WAITING -> Quad(Icons.Filled.Bluetooth, C.Orange, "En attente de l'iPhone", "Touche ici pour le jumeler")
                LinkStatus.BLUETOOTH_OFF -> Quad(Icons.Filled.BluetoothDisabled, C.Red, "Bluetooth désactivé", "Active le Bluetooth de la tablette")
                LinkStatus.NEED_PERMISSION -> Quad(Icons.Filled.BluetoothDisabled, C.Red, "Autorisations requises", "Touche ici pour les accorder")
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlyphTile(icon, color, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = C.Sub, fontSize = 15.sp)
                }
            }
        }
    }
}

private data class Quad(val icon: ImageVector, val color: Color, val title: String, val sub: String)
