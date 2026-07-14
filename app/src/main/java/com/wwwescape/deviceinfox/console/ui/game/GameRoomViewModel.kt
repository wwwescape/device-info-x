package com.wwwescape.deviceinfox.console.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.game.GameResult
import com.wwwescape.deviceinfox.console.data.game.GameRoomRepository
import com.wwwescape.deviceinfox.console.data.game.Mark
import com.wwwescape.deviceinfox.console.data.presence.PresenceRepository
import com.wwwescape.deviceinfox.console.ui.components.ScreenPresenceTier
import com.wwwescape.deviceinfox.console.ui.components.screenPresenceTier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** UI-ready view of [com.wwwescape.deviceinfox.console.data.game.GameRoomState], with "which side
 * is me" already resolved — mirrors [com.wwwescape.deviceinfox.console.ui.livelocation.LiveLocationViewModel]'s
 * own self/partner-name resolution via [PartnerRepository]. */
data class GameRoomUiState(
    val selfDisplayName: String? = null,
    val partnerDisplayName: String? = null,
    val selfPresent: Boolean = false,
    val partnerPresent: Boolean = false,
    val selfMark: Mark? = null,
    val partnerMark: Mark? = null,
    val selfScore: Int = 0,
    val partnerScore: Int = 0,
    val board: List<Mark?> = List(9) { null },
    val started: Boolean = false,
    val isSelfTurn: Boolean = false,
)

/** [GameResult] with "who" already resolved to self/partner, for the transient round-end banner —
 * a raw user id from [GameRoomRepository.roundResultEvent] isn't directly renderable. [SelfWin]/
 * [PartnerWin] also carry the winning board snapshot and the resolved 3-cell winning line, so the
 * screen can render/highlight it during the banner window — by the time this fires, live
 * [GameRoomUiState.board] has already moved on to the reset board for the next round. */
sealed interface RoundResult {
    data class SelfWin(val winningBoard: List<Mark?>, val winningLine: List<Int>) : RoundResult
    data class PartnerWin(val winningBoard: List<Mark?>, val winningLine: List<Int>) : RoundResult
    data object Draw : RoundResult
}

/** The 8 standard 3-in-a-row lines, index-based into a flat 9-cell board (row-major). */
private val WIN_LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

/** Re-derives which line actually won from the finished board — the server broadcasts only the
 * winner's user id, never the line itself, so this is resolved client-side purely for the
 * highlight visual. Returns the first matching line if a single move somehow completed two at
 * once (mathematically possible, e.g. a center move); good enough for a cosmetic highlight. */
private fun findWinningLine(board: List<Mark?>): List<Int> =
    WIN_LINES.firstOrNull { line ->
        val (a, b, c) = line
        val mark = board.getOrNull(a)
        mark != null && board.getOrNull(b) == mark && board.getOrNull(c) == mark
    }.orEmpty()

@HiltViewModel
class GameRoomViewModel @Inject constructor(
    private val repository: GameRoomRepository,
    partnerRepository: PartnerRepository,
    presenceRepository: PresenceRepository,
) : ViewModel() {

    private val selfId: StateFlow<String?> =
        partnerRepository.selfProfile.map { it?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<GameRoomUiState> = combine(
        repository.roomState,
        partnerRepository.selfProfile,
        partnerRepository.partnerProfile,
    ) { state, self, partner ->
        val partnerId = partner?.id
        val myId = self?.id
        GameRoomUiState(
            selfDisplayName = self?.displayName,
            partnerDisplayName = partner?.displayName,
            selfPresent = myId != null && state.present[myId] == true,
            partnerPresent = partnerId != null && state.present[partnerId] == true,
            selfMark = myId?.let { state.marks[it] },
            partnerMark = partnerId?.let { state.marks[it] },
            selfScore = myId?.let { state.scores[it] } ?: 0,
            partnerScore = partnerId?.let { state.scores[it] } ?: 0,
            board = state.board,
            started = state.started,
            isSelfTurn = myId != null && state.turn == myId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameRoomUiState())

    /** Drives the status row's 3-tier presence dot for the partner (CALLING_PLAN.md §7) — general
     * online presence (already tracked independently of any screen) combined with the existing
     * [GameRoomUiState.partnerPresent] screen-presence signal, recoloring what used to be a plain
     * 2-tier red/green boolean. [GameRoomUiState.partnerPresent] itself (and the "must both be
     * present to play" gameplay gate it drives) is unchanged — this only affects the icon. */
    val partnerPresenceTier: StateFlow<ScreenPresenceTier> = combine(
        uiState, presenceRepository.partnerPresence,
    ) { state, presence ->
        screenPresenceTier(isOnline = presence.isOnline, isOnScreen = state.partnerPresent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenPresenceTier.NOT_IN_APP)

    /** Transient per-round win/draw banner — see [GameRoomRepository.roundResultEvent]'s own doc
     * comment for why this is a one-shot event rather than folded into [uiState]. [selfId]'s
     * current value at the moment each event arrives is enough to resolve "who" — it's stable for
     * the whole session, no need to combine against every future emission. */
    val roundResultEvent: Flow<RoundResult> = repository.roundResultEvent.map { result ->
        when (result) {
            is GameResult.Win -> {
                val line = findWinningLine(result.winningBoard)
                if (result.winnerUserId == selfId.value) {
                    RoundResult.SelfWin(result.winningBoard, line)
                } else {
                    RoundResult.PartnerWin(result.winningBoard, line)
                }
            }
            GameResult.Draw -> RoundResult.Draw
        }
    }

    /** Fires once whenever the partner leaves the room/app — every emission means the same thing
     * ("the partner left"; a 2-person app has no other possible sender), so the id itself is
     * dropped rather than threaded through to the UI. */
    val partnerLeftEvent: Flow<Unit> = repository.partnerLeftEvent.map {}

    fun enterRoom() = repository.enterRoom()
    fun leaveRoom() = repository.leaveRoom()
    fun startGame() = repository.startGame()
    fun makeMove(cell: Int) = repository.makeMove(cell)
}
