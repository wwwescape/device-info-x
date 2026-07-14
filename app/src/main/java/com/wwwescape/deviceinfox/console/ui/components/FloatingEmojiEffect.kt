package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class EmojiParticle(
    val xFraction: Float,
    val amplitudeFraction: Float,
    val frequency: Float,
    val phase: Float,
    val fontSizeSp: Float,
    val durationMillis: Int,
    val delayMillis: Int,
)

private fun generateEmojiParticles(count: Int): List<EmojiParticle> {
    val random = Random(System.nanoTime())
    return List(count) {
        EmojiParticle(
            xFraction = random.nextFloat(),
            amplitudeFraction = 0.06f + random.nextFloat() * 0.16f,
            frequency = 1.2f + random.nextFloat() * 2.2f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            fontSizeSp = 20f + random.nextFloat() * 20f,
            durationMillis = 1600 + random.nextInt(1200),
            delayMillis = random.nextInt(900),
        )
    }
}

/** Bold, full-opacity drifting emoji particles — the Reactions tab's 4 emoji-based effects
 * (Hearts/Claps/Broken Hearts/Crying) all use this one composable, parameterized by [emoji].
 *
 * Same drift-bottom-to-top-with-zigzag-and-edge-fade math as
 * [com.wwwescape.deviceinfox.console.ui.settings.LoveQuoteDialog]'s `FloatingHearts`, but that one
 * doesn't fit here as-is: it draws a hand-built vector heart `Path` via `Canvas` at low alpha
 * (0.22-0.55) as ambient background decoration, not a real foreground glyph — and it's
 * heart-shape-specific, not parameterizable to other emoji. This is new, simpler code (real `Text`
 * particles instead of a vector path), not a promotion of that one.
 *
 * Like `Fireworks`, this loops forever internally (`infiniteRepeatable`/`RepeatMode.Restart`) —
 * the caller is expected to mount this only for a fixed window (see `ReactionEffectOverlay`) and
 * let Compose's own disposal cancel the animation, rather than this component tracking its own
 * lifetime. */
@Composable
fun FloatingEmojiEffect(emoji: String, modifier: Modifier = Modifier, particleCount: Int = 16) {
    val density = LocalDensity.current
    val particles = remember { generateEmojiParticles(particleCount) }
    val infiniteTransition = rememberInfiniteTransition(label = "floatingEmoji")
    val progresses = particles.map { particle ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = particle.durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(particle.delayMillis),
            ),
            label = "emojiProgress",
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        particles.forEachIndexed { index, particle ->
            val progress = progresses[index].value
            val y = heightPx * (1f - progress)
            val zigzag = sin(progress * particle.frequency * 2f * PI.toFloat() + particle.phase)
            val x = particle.xFraction * widthPx + zigzag * particle.amplitudeFraction * widthPx
            val edgeFade = minOf((progress / 0.12f).coerceIn(0f, 1f), ((1f - progress) / 0.12f).coerceIn(0f, 1f))

            Text(
                text = emoji,
                fontSize = particle.fontSizeSp.sp,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .alpha(edgeFade),
            )
        }
    }
}
