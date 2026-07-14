package com.wwwescape.deviceinfox.console.data.game

import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** WebSocket-backed state for the shared Tic Tac Toe game room (Settings' dice icon) — mirrors
 * `app/services/game_room_service.py`'s `game.state`/`game.partner_left` events exactly, and
 * [ServerPresenceRepository][com.wwwescape.deviceinfox.console.data.presence.ServerPresenceRepository]'s
 * own shape for re-announcing this device's own screen presence on reconnect.
 *
 * There's no REST fallback and no on-connect snapshot the way presence gets — unlike Messages,
 * the game room only exists at all while both partners are actively looking at it, so there's
 * nothing meaningful to resync ahead of the next `screen.tic_tac_toe_enter`. */
@Singleton
class GameRoomRepository @Inject constructor(
    private val webSocketClient: ConsoleWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _roomState = MutableStateFlow(GameRoomState())
    val roomState: StateFlow<GameRoomState> = _roomState.asStateFlow()

    /** Transient — fired once per completed round, never folded into [roomState] itself, so a
     * later recomposition/reconnect can't re-trigger a stale "you win!" banner from a StateFlow's
     * still-current value. */
    private val _roundResultEvent = MutableSharedFlow<GameResult>(extraBufferCapacity = 1)
    val roundResultEvent: SharedFlow<GameResult> = _roundResultEvent.asSharedFlow()

    /** One-shot popup trigger — the leaving partner's user id. */
    private val _partnerLeftEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val partnerLeftEvent: SharedFlow<String> = _partnerLeftEvent.asSharedFlow()

    /** This device's own "am I on the game screen" state, mirrored locally so it can be re-sent
     * on every reconnect — same reasoning as `ServerPresenceRepository`'s `_selfInMessagesScreen`:
     * without this, entering the room right as the socket is still finishing its handshake would
     * silently drop the enter message. */
    private val _selfInRoom = MutableStateFlow(false)

    init {
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleEvent(event) } }
        }
        scope.launch {
            webSocketClient.connected.collect {
                if (_selfInRoom.value) webSocketClient.send(type = "screen.tic_tac_toe_enter")
            }
        }
    }

    private fun handleEvent(event: WsEvent) {
        when (event.type) {
            "game.state" -> {
                _roomState.value = event.data.toGameRoomState()
                event.data["result"]?.toGameResultOrNull()?.let { _roundResultEvent.tryEmit(it) }
            }
            "game.partner_left" -> {
                val userId = event.data["user_id"]?.jsonPrimitive?.contentOrNull ?: return
                _partnerLeftEvent.tryEmit(userId)
                _roomState.value = GameRoomState()
            }
        }
    }

    fun enterRoom() {
        _selfInRoom.value = true
        webSocketClient.send(type = "screen.tic_tac_toe_enter")
    }

    /** Also clears local state immediately, rather than waiting for the server's own reset to
     * round-trip back — the room is meant to look fully reset the instant either side leaves, per
     * the explicit product decision behind `game_room_service.leave_room`. */
    fun leaveRoom() {
        _selfInRoom.value = false
        webSocketClient.send(type = "screen.tic_tac_toe_leave")
        _roomState.value = GameRoomState()
    }

    fun startGame() {
        webSocketClient.send(type = "game.start")
    }

    fun makeMove(cell: Int) {
        webSocketClient.send(type = "game.move", data = buildJsonObject { put("cell", cell) })
    }
}

private fun JsonObject.toGameRoomState(): GameRoomState {
    val present = this["present"]?.jsonObject.orEmpty().mapValues { (_, v) -> v.jsonPrimitive.boolean }
    val marks = this["marks"]?.jsonObject.orEmpty().mapValues { (_, v) -> Mark.valueOf(v.jsonPrimitive.content) }
    val scores = this["scores"]?.jsonObject.orEmpty().mapValues { (_, v) -> v.jsonPrimitive.int }
    val board = this["board"]?.jsonArray.orEmpty().map { element ->
        if (element is JsonNull) null else Mark.valueOf(element.jsonPrimitive.content)
    }
    val turn = this["turn"]?.jsonPrimitive?.contentOrNull
    val started = this["started"]?.jsonPrimitive?.boolean ?: false
    return GameRoomState(
        present = present,
        marks = marks,
        scores = scores,
        board = if (board.size == 9) board else List(9) { null },
        turn = turn,
        started = started,
    )
}

private fun JsonElement?.toGameResultOrNull(): GameResult? {
    if (this == null || this is JsonNull) return null
    val obj = jsonObject
    return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
        "win" -> obj["winner"]?.jsonPrimitive?.contentOrNull?.let { GameResult.Win(it) }
        "draw" -> GameResult.Draw
        else -> null
    }
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
