package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wwwescape.deviceinfox.console.data.emoji.matureEmojiImageUrl
import com.wwwescape.deviceinfox.console.ui.components.FloatingEmojiEffect
import com.wwwescape.deviceinfox.console.ui.components.Fireworks
import com.wwwescape.deviceinfox.console.ui.components.MATURE_EMOJI_POP_TOTAL_MILLIS
import com.wwwescape.deviceinfox.console.ui.components.MatureEmojiPop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

private const val EMOJI_EFFECT_DURATION_MILLIS = 2500L

// Fireworks' own bursts stagger their ignition up to 5s in (Fireworks.kt's generateFireworkBursts),
// so a slightly longer window than the emoji effects lets more of them actually fire before this
// gets unmounted, rather than most of a 2.5s window passing with only one or two visible.
private const val FIREWORKS_EFFECT_DURATION_MILLIS = 3500L

// A mature emoji is fetched from the server, so the pop waits for the image to be in Coil's cache
// first (otherwise it would spring in as an empty box); past this it's skipped rather than played
// blank or stalling the queue behind it.
private const val MATURE_EMOJI_PRELOAD_TIMEOUT_MILLIS = 4000L

private fun ReactionEvent.durationMillis(): Long = when {
    this is ReactionEvent.Mature -> MATURE_EMOJI_POP_TOTAL_MILLIS
    this is ReactionEvent.Builtin && effect == ReactionEffect.FIREWORKS -> FIREWORKS_EFFECT_DURATION_MILLIS
    else -> EMOJI_EFFECT_DURATION_MILLIS
}

/** Plays [effects] full-screen, one at a time — mounted once as a sibling in `HomeScreen`'s root
 * `Box` (same convention as `ScrollToBottomOverlay`/the message context overlay), above everything
 * else, non-interactive (a plain `Box` with no `clickable`/pointer handling, so taps pass through
 * to whatever's underneath).
 *
 * The one-at-a-time queueing needs no logic here at all: [effects] is backed by
 * `ReactionEffectBus`'s `Channel`, which only yields its next element once this `collect` call
 * below returns — so simply doing "show it, wait out its duration, clear it" per element already
 * drains the queue correctly, including racing local-tap-vs-incoming-WS-event cases. Neither
 * `Fireworks` nor `FloatingEmojiEffect` needs a "stop after N seconds" mechanism of its own — both
 * loop forever internally, but mounting/unmounting them via [currentEffect] going non-null/null is
 * enough; Compose cancels their internal animation coroutines on disposal. */
@Composable
fun ReactionEffectOverlay(effects: Flow<ReactionEvent>, modifier: Modifier = Modifier) {
    var currentEffect by remember { mutableStateOf<ReactionEvent?>(null) }
    val context = LocalContext.current

    LaunchedEffect(effects) {
        effects.collect { event ->
            if (event is ReactionEvent.Mature && !preloadMatureEmoji(context, event.emojiId)) return@collect
            currentEffect = event
            delay(event.durationMillis())
            currentEffect = null
        }
    }

    currentEffect?.let { event ->
        Box(modifier = modifier.fillMaxSize()) {
            when (event) {
                is ReactionEvent.Mature -> MatureEmojiPop(emojiId = event.emojiId)
                is ReactionEvent.Builtin -> if (event.effect == ReactionEffect.FIREWORKS) {
                    Fireworks(modifier = Modifier.fillMaxSize())
                } else {
                    FloatingEmojiEffect(emoji = event.effect.glyph, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private suspend fun preloadMatureEmoji(context: android.content.Context, emojiId: String): Boolean {
    val request = ImageRequest.Builder(context).data(matureEmojiImageUrl(emojiId)).build()
    return withTimeoutOrNull(MATURE_EMOJI_PRELOAD_TIMEOUT_MILLIS) { context.imageLoader.execute(request) } is SuccessResult
}
