package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.data.love.LoveQuoteRepository
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** True red regardless of the app's dynamic-color theme — a heart shouldn't shift hue with Material You. */
val LoveHeartRed = Color(0xFFE53935)

@Composable
fun LoveQuoteDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val quote = remember { LoveQuoteRepository.randomQuote(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingHearts(modifier = Modifier.fillMaxSize(), heartColor = LoveHeartRed)

            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.console_love_note_dismiss),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 44.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = LoveHeartRed,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = quote,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.console_love_note_dismiss))
                        }
                    }
                }
            }
        }
    }
}

private data class HeartParticle(
    val xFraction: Float,
    val amplitudeFraction: Float,
    val frequency: Float,
    val phase: Float,
    val sizePx: Float,
    val path: Path,
    val maxAlpha: Float,
    val durationMillis: Int,
    val delayMillis: Int,
)

private fun heartPath(size: Float): Path = Path().apply {
    moveTo(size / 2f, size / 5f)
    cubicTo(5f * size / 14f, 0f, 0f, size / 15f, size / 28f, 2f * size / 5f)
    cubicTo(size / 14f, 2f * size / 3f, 3f * size / 7f, 5f * size / 6f, size / 2f, size)
    cubicTo(4f * size / 7f, 5f * size / 6f, 13f * size / 14f, 2f * size / 3f, 27f * size / 28f, 2f * size / 5f)
    cubicTo(size, size / 15f, 9f * size / 14f, 0f, size / 2f, size / 5f)
    close()
}

private fun generateHeartParticles(count: Int, density: Density): List<HeartParticle> {
    val random = Random(System.nanoTime())
    return List(count) {
        val sizePx = with(density) { (14f + random.nextFloat() * 22f).dp.toPx() }
        HeartParticle(
            xFraction = random.nextFloat(),
            amplitudeFraction = 0.06f + random.nextFloat() * 0.16f,
            frequency = 1.2f + random.nextFloat() * 2.2f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            sizePx = sizePx,
            path = heartPath(sizePx),
            maxAlpha = 0.22f + random.nextFloat() * 0.33f,
            durationMillis = 7000 + random.nextInt(6000),
            delayMillis = random.nextInt(6000),
        )
    }
}

/** Translucent hearts drifting bottom-to-top in a smooth zig-zag, looping independently per particle. */
@Composable
private fun FloatingHearts(modifier: Modifier = Modifier, heartColor: Color, particleCount: Int = 18) {
    val density = LocalDensity.current
    val particles = remember(density) { generateHeartParticles(particleCount, density) }
    val infiniteTransition = rememberInfiniteTransition(label = "floatingHearts")
    val progresses = particles.map { particle ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = particle.durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(particle.delayMillis),
            ),
            label = "heartProgress",
        )
    }

    Canvas(modifier = modifier) {
        particles.forEachIndexed { index, particle ->
            val progress = progresses[index].value
            val y = size.height * (1f - progress)
            val zigzag = sin(progress * particle.frequency * 2f * PI.toFloat() + particle.phase)
            val x = particle.xFraction * size.width + zigzag * particle.amplitudeFraction * size.width
            val edgeFade = minOf((progress / 0.12f).coerceIn(0f, 1f), ((1f - progress) / 0.12f).coerceIn(0f, 1f))
            val alpha = particle.maxAlpha * edgeFade
            translate(left = x - particle.sizePx / 2f, top = y - particle.sizePx / 2f) {
                drawPath(particle.path, color = heartColor.copy(alpha = alpha))
            }
        }
    }
}
