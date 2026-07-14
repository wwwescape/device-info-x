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
import com.wwwescape.deviceinfox.console.data.asset.AssetKind
import com.wwwescape.deviceinfox.console.data.asset.AssetTier
import com.wwwescape.deviceinfox.console.data.asset.assetImageUrl
import com.wwwescape.deviceinfox.console.ui.components.FloatingEmojiEffect
import com.wwwescape.deviceinfox.console.ui.components.Fireworks
import com.wwwescape.deviceinfox.console.ui.components.CustomEmojiPop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

// Every reaction — the built-in floating-emoji effects and fireworks, and the server-hosted pop
// emojis — plays for the same 5 seconds (it was 2.5s / 3.5s / 2.6s). Fireworks' own bursts stagger
// their ignition up to 5s in (Fireworks.kt's generateFireworkBursts), so this also lets all of
// them fire before it's unmounted. The Reactions tab stays disabled for this whole window.
internal const val REACTION_EFFECT_DURATION_MILLIS = 5000L

// A custom emoji is fetched from the server, so the pop waits for the image to be in Coil's cache
// first (otherwise it would spring in as an empty box); past this it's skipped rather than played
// blank or stalling the queue behind it.
private const val CUSTOM_EMOJI_PRELOAD_TIMEOUT_MILLIS = 4000L

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
fun ReactionEffectOverlay(
    effects: Flow<ReactionEvent>,
    onEffectFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentEffect by remember { mutableStateOf<ReactionEvent?>(null) }
    val context = LocalContext.current

    LaunchedEffect(effects) {
        effects.collect { event ->
            try {
                if (event is ReactionEvent.Custom && !preloadCustomEmoji(context, event.tier, event.id)) return@collect
                currentEffect = event
                delay(REACTION_EFFECT_DURATION_MILLIS)
                currentEffect = null
            } finally {
                // Every queued reaction is reported exactly once — played, skipped (image couldn't
                // load) or cut short — which is what lets the picker re-enable itself.
                onEffectFinished()
            }
        }
    }

    currentEffect?.let { event ->
        Box(modifier = modifier.fillMaxSize()) {
            when (event) {
                is ReactionEvent.Custom -> CustomEmojiPop(tier = event.tier, id = event.id, totalMillis = REACTION_EFFECT_DURATION_MILLIS)
                is ReactionEvent.Builtin -> if (event.effect == ReactionEffect.FIREWORKS) {
                    Fireworks(modifier = Modifier.fillMaxSize())
                } else {
                    FloatingEmojiEffect(emoji = event.effect.glyph, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private suspend fun preloadCustomEmoji(context: android.content.Context, tier: AssetTier, id: String): Boolean {
    val request = ImageRequest.Builder(context).data(assetImageUrl(AssetKind.REACTIONS, tier, id)).build()
    return withTimeoutOrNull(CUSTOM_EMOJI_PRELOAD_TIMEOUT_MILLIS) { context.imageLoader.execute(request) } is SuccessResult
}
