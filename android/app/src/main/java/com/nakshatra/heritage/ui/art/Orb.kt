package com.nakshatra.heritage.ui.art

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.nakshatra.heritage.ui.theme.nk
import com.nakshatra.heritage.voice.VoiceState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Seconds per turn of the wheel in each state -- the durations in styles.css. */
private fun turnSeconds(state: VoiceState) = when (state) {
    VoiceState.IDLE -> 140f
    VoiceState.ARMED -> 60f
    VoiceState.WAKE -> 6f
    VoiceState.LISTENING -> 18f
    VoiceState.PROCESSING -> 3.2f
    VoiceState.ANSWERING -> 40f
}

/**
 * The voice orb: a Konark wheel inside a ring of ticks. Its colour, its speed,
 * its glow and its rings all follow the real pipeline state; [level] (0..1) is
 * the live input level.
 */
@Composable
fun VoiceOrb(state: VoiceState, level: Float, modifier: Modifier = Modifier) {
    val accent by animateColorAsState(nk.accent(state), tween(500), label = "accent")
    val glow by animateFloatAsState(level, tween(140, easing = LinearEasing), label = "level")
    val dim by animateFloatAsState(if (state == VoiceState.IDLE) 0.62f else 1f, tween(400), label = "dim")

    // The wheel keeps its angle when the speed changes, so it never jumps.
    var angle by remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(360f / turnSeconds(state))
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { it }
            angle = (angle + speed * (t - last) / 1e9f) % 360f
            last = t
        }
    }

    val loop = rememberInfiniteTransition(label = "orb")
    val pulse by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val breathe by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "breathe")
    val arc by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "arc")

    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val u = side / 200f

        // Glow behind the wheel, swelling with the input level (or pulsing while answering).
        val swell = if (state == VoiceState.ANSWERING) breathe * 0.5f else glow
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = (0.14f + swell * 0.3f) * dim), Color.Transparent), c, side * (0.42f + swell * 0.12f)),
            radius = side * (0.42f + swell * 0.12f), center = c,
        )

        // State ring.
        when (state) {
            VoiceState.ARMED -> drawCircle(accent.copy(alpha = 0.08f + 0.32f * breathe), 96f * u * (0.98f + 0.05f * breathe), c, style = Stroke(1.5f * u))
            VoiceState.WAKE, VoiceState.LISTENING ->
                drawCircle(accent.copy(alpha = 0.6f * (1f - pulse)), 96f * u * (0.96f + 0.28f * pulse), c, style = Stroke(1.5f * u))
            else -> Unit
        }

        // Ticks.
        for (i in 0 until 60) {
            val a = i / 60f * 2f * PI.toFloat()
            val r0 = (if (i % 5 == 0) 93f else 95f) * u
            val r1 = 98f * u
            drawLine(
                accent.copy(alpha = 0.45f * dim),
                Offset(c.x + r0 * cos(a), c.y + r0 * sin(a)), Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)),
                strokeWidth = 0.8f * u, cap = StrokeCap.Round,
            )
        }

        if (state == VoiceState.PROCESSING) {
            drawArc(
                accent, startAngle = arc, sweepAngle = 80f, useCenter = false,
                topLeft = Offset(c.x - 90f * u, c.y - 90f * u), size = Size(180f * u, 180f * u),
                style = Stroke(2.2f * u, cap = StrokeCap.Round),
            )
        }

        rotate(angle, c) {
            scale(0.87f, 0.87f, c) {
                // drawMotif centres itself in the draw area.
                drawMotif("wheel", accent.copy(alpha = dim), baseStroke = 1.7f)
            }
        }
    }
}
