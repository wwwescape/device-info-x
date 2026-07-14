package com.wwwescape.deviceinfox.console.data.reaction

import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.ui.home.ReactionEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Reactions tab's live delivery — mirrors [com.wwwescape.deviceinfox.console.data.presence.ServerPresenceRepository]'s
 * shape (its own `webSocketClient.events` collector) but for the `"reaction"` WS event instead of
 * presence/typing, since this is its own independent concern, not chat data or presence state.
 *
 * [incoming] is backed by an unlimited [Channel] rather than a `SharedFlow` specifically so
 * "queue them, one plays before the other" (both partners tapping at once) falls out for free: a
 * `Channel`-backed `Flow` only pulls its next element once the current collector's `collect`
 * lambda returns, so a single collector (see `ReactionEffectOverlay`) that shows an effect, waits
 * out its fixed duration, then clears it will naturally drain queued effects one at a time —
 * whether they arrived from this device's own tap or an incoming WS event.
 */
@Singleton
class ReactionEffectBus @Inject constructor(
    private val webSocketClient: ConsoleWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<ReactionEvent>(Channel.UNLIMITED)

    val incoming: Flow<ReactionEvent> = channel.receiveAsFlow()

    init {
        scope.launch {
            webSocketClient.events.collect { event ->
                if (event.type != "reaction") return@collect
                val wireValue = event.data["effect"]?.jsonPrimitive?.content ?: return@collect
                ReactionEvent.fromWireValue(wireValue)?.let { channel.trySend(it) }
            }
        }
    }

    /** Plays [event] on this device immediately (optimistic — doesn't wait for any network round
     * trip) and tells the partner's device to play it too. There's no server-side "both partners
     * Here" enforcement, so this always sends; the UI only ever calls this from an enabled button,
     * which is where that gate actually lives (see `ReactionsPanel`). */
    fun sendReaction(event: ReactionEvent) {
        channel.trySend(event)
        webSocketClient.send(type = "reaction.send", data = JsonObject(mapOf("effect" to JsonPrimitive(event.wireValue))))
    }
}
