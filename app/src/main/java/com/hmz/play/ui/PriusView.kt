package com.hmz.play.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.hmz.play.data.DoorState

/**
 * Top view of a 2nd-generation Toyota Prius (2003–2009 liftback) in grey, nose up.
 * Proportions follow the real car: rounded short nose, very raked windscreen, long roof,
 * steep rear hatch glass with the split spoiler, Kamm tail. Open doors swing out in red.
 * Drawn in a 200 × 440 design space, scaled to fit.
 */
@Composable
fun PriusView(doors: DoorState, modifier: Modifier = Modifier) {
    val fl by animateFloatAsState(if (doors.frontLeft) 1f else 0f, spring(0.7f, 300f), label = "fl")
    val fr by animateFloatAsState(if (doors.frontRight) 1f else 0f, spring(0.7f, 300f), label = "fr")
    val rl by animateFloatAsState(if (doors.rearLeft) 1f else 0f, spring(0.7f, 300f), label = "rl")
    val rr by animateFloatAsState(if (doors.rearRight) 1f else 0f, spring(0.7f, 300f), label = "rr")
    val tr by animateFloatAsState(if (doors.trunk) 1f else 0f, spring(0.7f, 300f), label = "tr")
    val hd by animateFloatAsState(if (doors.hood) 1f else 0f, spring(0.7f, 300f), label = "hd")

    Canvas(modifier) {
        val s = minOf(size.width / 280f, size.height / 460f)
        withTransform({
            translate((size.width - 280f * s) / 2f + 40f * s, (size.height - 460f * s) / 2f + 10f * s)
            scale(s, s, Offset.Zero)
        }) {
            drawPrius(fl, fr, rl, rr, tr, hd)
        }
    }
}

private val BODY = Color(0xFF8D9196)        // Prius "Silver" / grey
private val BODY_DARK = Color(0xFF6E7277)
private val BODY_LIGHT = Color(0xFFB4B8BD)
private val GLASS = Color(0xFF1E252D)
private val GLASS_HI = Color(0xFF3A4652)
private val OPEN = Color(0xFFFF453A)
private val TYRE = Color(0xFF111111)

private fun DrawScope.drawPrius(fl: Float, fr: Float, rl: Float, rr: Float, tr: Float, hd: Float) {
    // Wheels (visible under the body edges)
    listOf(Offset(-2f, 78f), Offset(186f, 78f), Offset(-2f, 318f), Offset(186f, 318f)).forEach {
        drawRoundRect(TYRE, it, Size(16f, 58f), CornerRadius(6f))
    }

    // Body outline: narrow rounded nose, widest at the rear wheels, flat Kamm tail.
    val body = Path().apply {
        moveTo(100f, 0f)
        cubicTo(150f, 0f, 178f, 8f, 186f, 40f)    // front right corner
        lineTo(194f, 150f)
        lineTo(198f, 330f)
        cubicTo(198f, 395f, 190f, 425f, 160f, 432f) // rear right
        lineTo(40f, 432f)
        cubicTo(10f, 425f, 2f, 395f, 2f, 330f)
        lineTo(6f, 150f)
        lineTo(14f, 40f)
        cubicTo(22f, 8f, 50f, 0f, 100f, 0f)
        close()
    }
    drawPath(body, Brush.horizontalGradient(listOf(BODY_DARK, BODY_LIGHT, BODY, BODY_LIGHT, BODY_DARK), 0f, 200f))

    // Hood (short) + hood gap; opens upward (lighter + red edge)
    val hood = Path().apply {
        moveTo(30f, 30f); cubicTo(60f, 14f, 140f, 14f, 170f, 30f)
        lineTo(176f, 108f); cubicTo(140f, 100f, 60f, 100f, 24f, 108f); close()
    }
    drawPath(hood, if (hd > 0.01f) OPEN.copy(alpha = 0.35f + 0.4f * hd) else BODY_LIGHT.copy(alpha = 0.35f))
    drawPath(hood, Color.Black.copy(alpha = 0.25f), style = Stroke(1.5f))

    // Windscreen — long and very raked (typical Prius 2)
    val ws = Path().apply {
        moveTo(26f, 116f); cubicTo(60f, 104f, 140f, 104f, 174f, 116f)
        lineTo(166f, 186f); cubicTo(130f, 178f, 70f, 178f, 34f, 186f); close()
    }
    drawPath(ws, Brush.verticalGradient(listOf(GLASS_HI, GLASS), 104f, 186f))

    // Roof
    drawRoundRect(BODY, Offset(36f, 186f), Size(128f, 112f), CornerRadius(14f))
    drawRoundRect(BODY_LIGHT.copy(alpha = 0.25f), Offset(56f, 196f), Size(88f, 92f), CornerRadius(10f))

    // Rear hatch glass with the Prius split (spoiler bar)
    val rg = Path().apply {
        moveTo(36f, 298f); lineTo(164f, 298f); lineTo(170f, 372f); cubicTo(130f, 380f, 70f, 380f, 30f, 372f); close()
    }
    drawPath(rg, Brush.verticalGradient(listOf(GLASS, GLASS_HI), 298f, 380f))
    drawRect(BODY_DARK, Offset(30f, 350f), Size(140f, 6f))

    // Tailgate area / rear lights
    drawRoundRect(Color(0xFFB0202A), Offset(8f, 404f), Size(34f, 14f), CornerRadius(5f))
    drawRoundRect(Color(0xFFB0202A), Offset(158f, 404f), Size(34f, 14f), CornerRadius(5f))
    // Headlights
    drawRoundRect(Color(0xFFDCE3EA), Offset(22f, 18f), Size(36f, 12f), CornerRadius(6f))
    drawRoundRect(Color(0xFFDCE3EA), Offset(142f, 18f), Size(36f, 12f), CornerRadius(6f))

    // Mirrors
    drawRoundRect(BODY_DARK, Offset(-14f, 128f), Size(20f, 14f), CornerRadius(5f))
    drawRoundRect(BODY_DARK, Offset(194f, 128f), Size(20f, 14f), CornerRadius(5f))

    // Door seams (closed doors)
    val seam = Color.Black.copy(alpha = 0.35f)
    listOf(150f, 238f, 320f).forEach { y ->
        drawLine(seam, Offset(4f, y), Offset(36f, y), 1.6f)
        drawLine(seam, Offset(164f, y), Offset(196f, y), 1.6f)
    }

    // Doors: hinge at the front edge, swing outward up to 50°
    door(left = true, top = 150f, bottom = 238f, open = fl)
    door(left = false, top = 150f, bottom = 238f, open = fr)
    door(left = true, top = 238f, bottom = 320f, open = rl)
    door(left = false, top = 238f, bottom = 320f, open = rr)

    // Hatch: lifts (drawn sliding back past the tail, red)
    if (tr > 0.01f) {
        drawRoundRect(
            OPEN.copy(alpha = 0.85f), Offset(26f, 300f + 140f * tr), Size(148f, 110f),
            CornerRadius(16f), alpha = tr,
        )
    }
}

private fun DrawScope.door(left: Boolean, top: Float, bottom: Float, open: Float) {
    val w = 30f
    val x = if (left) 3f else 167f
    if (open < 0.01f) return
    val pivot = Offset(if (left) x else x + w, top)
    val angle = (if (left) 50f else -50f) * open
    // gap left in the body
    drawRect(Color.Black.copy(alpha = 0.55f * open), Offset(x, top), Size(w, bottom - top))
    rotate(angle, pivot) {
        drawRoundRect(OPEN, Offset(x, top), Size(w, bottom - top), CornerRadius(6f))
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(x, top), Size(w, bottom - top), CornerRadius(6f), style = Stroke(2f))
    }
}

/** Card shown on top of everything while a door is open. */
@Composable
fun DoorAlert() {
    val doors by com.hmz.play.data.Doors.state.collectAsStateWithLifecycle()
    val drive by com.hmz.play.data.Drive.state.collectAsStateWithLifecycle()
    androidx.compose.animation.AnimatedVisibility(
        doors.any,
        enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val moving = drive.speedKmh > 5
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(top = 16.dp, end = 16.dp),
            contentAlignment = androidx.compose.ui.Alignment.TopEnd,
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.width(240.dp).clip(RoundedCornerShape(24.dp)).background(if (moving) OPEN else C.Panel).padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                PriusView(doors, Modifier.width(200.dp).height(250.dp))
                androidx.compose.material3.Text(
                    if (doors.labels.size == 1) {
                        val l = doors.labels.first()
                        if (l == "coffre" || l == "capot") "${l.replaceFirstChar { it.uppercase() }} ouvert" else "Porte $l ouverte"
                    } else "Ouvert : " + doors.labels.joinToString(", "),
                    color = if (moving) Color.White else C.Text,
                    fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
