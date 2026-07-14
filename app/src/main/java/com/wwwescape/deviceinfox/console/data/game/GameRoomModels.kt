package com.wwwescape.deviceinfox.console.data.game

enum class Mark { X, O }

sealed interface GameResult {
    /** [winningBoard] is the board as it stood at the moment of the win — the server folds the
     * already-reset (cleared for the next round) board into the very same `game.state` payload
     * this result rides in, so [GameRoomRepository][com.wwwescape.deviceinfox.console.data.game.GameRoomRepository]
     * captures it from [GameRoomState.board] one tick before applying that update, otherwise
     * there'd be nothing left to actually highlight the winning line against. */
    data class Win(val winnerUserId: String, val winningBoard: List<Mark?> = List(9) { null }) : GameResult
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
