package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.animation.core.Animatable
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
import com.wwwescape.deviceinfox.console.data.emoji.matureEmojiImageUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long [MatureEmojiPop] stays up in total — `ReactionEffectOverlay` unmounts it after this. */
const val MATURE_EMOJI_POP_TOTAL_MILLIS = 2600L

private const val POP_HOLD_MILLIS = 1700L
private const val POP_EXIT_MILLIS = 400

/** One server-hosted emoji, centered and large: springs in with a little overshoot, holds, then
 * shrinks and fades out — about 2.6s end to end. Deliberately not the multi-particle
 * `FloatingEmojiEffect` the regular reactions use. Sized ~160dp (the source art is 300px, so this
 * keeps the upscale to roughly 1.3x on a Pixel 8 instead of looking soft at a bigger size). */
@Composable
fun MatureEmojiPop(emojiId: String, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(emojiId) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        delay(POP_HOLD_MILLIS)
        launch { alpha.animateTo(0f, tween(POP_EXIT_MILLIS)) }
        scale.animateTo(0.7f, tween(POP_EXIT_MILLIS))
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = matureEmojiImageUrl(emojiId),
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
