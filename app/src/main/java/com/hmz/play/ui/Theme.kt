package com.hmz.play.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object C {
    /** Night (dark) or day (light) palette; switched automatically at sunrise/sunset. */
    var dark by androidx.compose.runtime.mutableStateOf(true)

    /** True when a wallpaper is set: surfaces become translucent over it. */
    var glass by androidx.compose.runtime.mutableStateOf(false)

    val Bg get() = if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val Panel get() = (if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)).let { if (glass) it.copy(alpha = 0.78f) else it }
    val Panel2 get() = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val Line get() = if (dark) Color(0xFF38383A) else Color(0xFFD1D1D6)
    val Text get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val Sub get() = if (dark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Pink = Color(0xFFFF375F)
    val Purple = Color(0xFFBF5AF2)
    val Teal = Color(0xFF64D2FF)
    val Gray = Color(0xFF8E8E93)
}

val CardShape = RoundedCornerShape(22.dp)

@Composable
fun HmzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.Bg,
            surface = C.Panel,
            primary = C.Blue,
            onBackground = C.Text,
            onSurface = C.Text,
        ),
        content = content,
    )
}

/** Click with a quick spring "press" shrink — the tactile feel of the CarPlay buttons. */
@Composable
fun Modifier.tap(scaleTo: Float = 0.92f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

fun Modifier.card() = this.clip(CardShape).background(C.Panel)

fun glossy(color: Color) = Brush.verticalGradient(listOf(lerp(color, Color.White, 0.18f), color))

/** Rounded-square app tile with a gradient fill and a white glyph. */
@Composable
fun GlyphTile(icon: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(glossy(color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.54f))
    }
}

private val artPalettes = listOf(
    listOf(Color(0xFFFF375F), Color(0xFF5E2BFF)),
    listOf(Color(0xFFFF9F0A), Color(0xFFFF375F)),
    listOf(Color(0xFF30D158), Color(0xFF0A84FF)),
    listOf(Color(0xFF64D2FF), Color(0xFF5E5CE6)),
    listOf(Color(0xFFBF5AF2), Color(0xFFFF375F)),
    listOf(Color(0xFFFFD60A), Color(0xFFFF6B00)),
)

/** AMS doesn't carry album art, so each track gets its own stable gradient. */
fun artBrush(seed: String): Brush {
    val p = artPalettes[(seed.hashCode() and 0x7fffffff) % artPalettes.size]
    return Brush.linearGradient(p)
}

fun fmtTime(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
