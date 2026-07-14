package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.console.data.asset.AssetKind
import com.wwwescape.deviceinfox.console.data.asset.AssetTier
import com.wwwescape.deviceinfox.console.data.asset.assetImageUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Time reserved around the hold: the spring-in (~0.5s, with margin) and the exit fade (0.4s, plus a
// little slack so it finishes before the overlay unmounts it).
private const val POP_EXIT_MILLIS = 400
private const val POP_RESERVED_MILLIS = 1000L

// The idle pulse: a slow breathing between 98% and 104% of full size while the emoji holds.
private const val PULSE_HALF_CYCLE_MILLIS = 900
private const val PULSE_MIN_SCALE = 0.98f
private const val PULSE_MAX_SCALE = 1.04f

/** One server-hosted reaction emoji (standard or nsfw folder), centered and large: springs in with
 * a little overshoot, then **breathes gently** (a slow ~98-104% pulse) for the rest of [totalMillis]
 * so it doesn't sit frozen, then shrinks and fades out. Deliberately not the multi-particle
 * `FloatingEmojiEffect` the built-in reactions use. Sized ~160dp (the source art is 300px, so this
 * keeps the upscale to roughly 1.3x on a Pixel 8 instead of looking soft at a bigger size).
 * `ReactionEffectOverlay` unmounts it after [totalMillis]. */
@Composable
fun CustomEmojiPop(tier: AssetTier, id: String, totalMillis: Long, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(tier, id) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        // Pulse until the hold window is over. Timing out cancels the running tween mid-way, which
        // is fine: the exit shrink below starts from wherever the scale happens to be.
        withTimeoutOrNull(totalMillis - POP_RESERVED_MILLIS) {
            while (true) {
                scale.animateTo(PULSE_MAX_SCALE, tween(PULSE_HALF_CYCLE_MILLIS, easing = FastOutSlowInEasing))
                scale.animateTo(PULSE_MIN_SCALE, tween(PULSE_HALF_CYCLE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
        launch { alpha.animateTo(0f, tween(POP_EXIT_MILLIS)) }
        scale.animateTo(0.7f, tween(POP_EXIT_MILLIS))
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = assetImageUrl(AssetKind.REACTIONS, tier, id),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
        )
    }
}
