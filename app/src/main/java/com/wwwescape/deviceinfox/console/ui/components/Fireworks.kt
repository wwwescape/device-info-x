package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val FIREWORK_COLORS = listOf(
    Color(0xFFFFD54F), // gold
    Color(0xFFE57373), // red
    Color(0xFF64B5F6), // blue
    Color(0xFF81C784), // green
    Color(0xFFBA68C8), // purple
    Color(0xFFF06292), // pink
)

private data class FireworkBurst(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val rayAngles: List<Float>,
    val maxRadiusPx: Float,
    val color: Color,
    val durationMillis: Int,
    val delayMillis: Int,
)

private fun generateFireworkBursts(count: Int, density: Density): List<FireworkBurst> {
    val random = Random(System.nanoTime())
    return List(count) {
        val rayCount = 10 + random.nextInt(6)
        FireworkBurst(
            centerXFraction = 0.15f + random.nextFloat() * 0.7f,
            centerYFraction = 0.08f + random.nextFloat() * 0.6f,
            rayAngles = List(rayCount) { i -> (2f * PI.toFloat() * i / rayCount) + random.nextFloat() * 0.3f },
            maxRadiusPx = with(density) { (50f + random.nextFloat() * 60f).dp.toPx() },
            color = FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.size)],
            durationMillis = 1300 + random.nextInt(900),
            delayMillis = random.nextInt(5000),
        )
    }
}

/** Random firework bursts radiating outward and fading, looping independently per burst — same
 * per-particle `infiniteRepeatable` + `StartOffset` shape as `LoveQuoteDialog`'s `FloatingHearts`,
 * just with a burst of rays instead of one drifting glyph. Shared between
 * [com.wwwescape.deviceinfox.console.ui.whatsnew.WhatsNewDialog]'s background and the Tic Tac Toe
 * game room's win celebration. */
@Composable
fun Fireworks(modifier: Modifier = Modifier, burstCount: Int = 7) {
    val density = LocalDensity.current
    val bursts = remember(density) { generateFireworkBursts(burstCount, density) }
    val infiniteTransition = rememberInfiniteTransition(label = "fireworks")
    val progresses = bursts.map { burst ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = burst.durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(burst.delayMillis),
            ),
            label = "fireworkProgress",
        )
    }

    Canvas(modifier = modifier) {
        val sparkRadiusPx = 3.dp.toPx()
        val trailStrokePx = 1.5.dp.toPx()
        val flashRadiusPx = 6.dp.toPx()

        bursts.forEachIndexed { index, burst ->
            val progress = progresses[index].value
            val centerX = burst.centerXFraction * size.width
            val centerY = burst.centerYFraction * size.height
            val eased = 1f - (1f - progress) * (1f - progress)
            val radius = burst.maxRadiusPx * eased
            val alpha = (1f - progress).coerceIn(0f, 1f)

            burst.rayAngles.forEach { angle ->
                val x = centerX + radius * cos(angle)
                val y = centerY + radius * sin(angle)
                val trailX = centerX + radius * 0.6f * cos(angle)
                val trailY = centerY + radius * 0.6f * sin(angle)
                drawLine(
                    color = burst.color.copy(alpha = alpha * 0.4f),
                    start = Offset(trailX, trailY),
                    end = Offset(x, y),
                    strokeWidth = trailStrokePx,
                )
                drawCircle(color = burst.color.copy(alpha = alpha * 0.85f), radius = sparkRadiusPx, center = Offset(x, y))
            }

            // Brief bright flash at the moment of ignition.
            if (progress < 0.2f) {
                val flashAlpha = 0.6f * (1f - progress / 0.2f)
                drawCircle(color = Color.White.copy(alpha = flashAlpha), radius = flashRadiusPx, center = Offset(centerX, centerY))
            }
        }
    }
}
