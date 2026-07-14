package com.wwwescape.deviceinfox.console.data.game

enum class Mark { X, O }

sealed interface GameResult {
    data class Win(val winnerUserId: String) : GameResult
    data object Draw : GameResult
}

/** Mirrors the server's `game.state` payload (`app/services/game_room_service.py`) exactly — a
 * full snapshot every time, never a delta, so a reconnect or a late-arriving event always redraws
 * correctly from whatever the most recent broadcast said rather than needing any client-side
 * patch/merge logic. Every field is keyed by the real user id of whichever partner it describes,
 * symmetric for both sides — resolving "which one is me" is left to [GameRoomViewModel]. */
data class GameRoomState(
    val present: Map<String, Boolean> = emptyMap(),
    val marks: Map<String, Mark> = emptyMap(),
    val scores: Map<String, Int> = emptyMap(),
    val board: List<Mark?> = List(9) { null },
    val turn: String? = null,
    val started: Boolean = false,
)
