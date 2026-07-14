package com.wwwescape.deviceinfox.console.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.game.GameResult
import com.wwwescape.deviceinfox.console.data.game.GameRoomRepository
import com.wwwescape.deviceinfox.console.data.game.Mark
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
 * a raw user id from [GameRoomRepository.roundResultEvent] isn't directly renderable. */
sealed interface RoundResult {
    data object SelfWin : RoundResult
    data object PartnerWin : RoundResult
    data object Draw : RoundResult
}

@HiltViewModel
class GameRoomViewModel @Inject constructor(
    private val repository: GameRoomRepository,
    partnerRepository: PartnerRepository,
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

    /** Transient per-round win/draw banner — see [GameRoomRepository.roundResultEvent]'s own doc
     * comment for why this is a one-shot event rather than folded into [uiState]. [selfId]'s
     * current value at the moment each event arrives is enough to resolve "who" — it's stable for
     * the whole session, no need to combine against every future emission. */
    val roundResultEvent: Flow<RoundResult> = repository.roundResultEvent.map { result ->
        when (result) {
            is GameResult.Win -> if (result.winnerUserId == selfId.value) RoundResult.SelfWin else RoundResult.PartnerWin
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
