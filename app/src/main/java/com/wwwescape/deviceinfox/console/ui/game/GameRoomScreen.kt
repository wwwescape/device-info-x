package com.wwwescape.deviceinfox.console.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.game.Mark
import com.wwwescape.deviceinfox.console.ui.components.Fireworks
import com.wwwescape.deviceinfox.console.ui.settings.ServerOnlineColor
import kotlinx.coroutines.delay

private const val RESULT_BANNER_MILLIS = 1_800L

/** Shared Tic Tac Toe game room — Settings' dice icon. Same overall shape as `LiveLocationScreen`
 * (back-arrow `TopAppBar`, a self/partner status row up top, a "waiting for partner" placeholder
 * swapping in for real content), minus the toggle/map/ETA specifics: the status row also carries
 * each player's assigned mark, the placeholder becomes a Start button once both are present, and
 * the bottom block becomes the running score instead of an ETA. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameRoomScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: GameRoomViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Announces presence on entry, withdraws it on exit — mirrors HomeScreen's identical
    // DisposableEffect(Unit) wiring for the Messages "Here" tier, since Compose entering/leaving
    // composition (not any ViewModel lifecycle callback) is the real "opened/closed this screen"
    // signal here.
    DisposableEffect(Unit) {
        viewModel.enterRoom()
        onDispose { viewModel.leaveRoom() }
    }

    var resultBanner by remember { mutableStateOf<RoundResult?>(null) }
    var showPartnerLeftDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.roundResultEvent.collect { result ->
            resultBanner = result
            delay(RESULT_BANNER_MILLIS)
            resultBanner = null
        }
    }
    LaunchedEffect(Unit) {
        viewModel.partnerLeftEvent.collect { showPartnerLeftDialog = true }
    }

    val bothPresent = uiState.selfPresent && uiState.partnerPresent

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_game_room_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PlayerStatusRow(
                selfDisplayName = uiState.selfDisplayName,
                selfPresent = uiState.selfPresent,
                selfMark = uiState.selfMark,
                partnerDisplayName = uiState.partnerDisplayName,
                partnerPresent = uiState.partnerPresent,
                partnerMark = uiState.partnerMark,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (resultBanner is RoundResult.SelfWin) {
                    Fireworks(modifier = Modifier.fillMaxSize())
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when {
                        !bothPresent -> Text(
                            text = stringResource(R.string.console_game_room_waiting_message),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        !uiState.started -> Button(onClick = viewModel::startGame) {
                            Text(stringResource(R.string.console_game_room_start_action))
                        }
                        else -> {
                            val turnText = if (uiState.isSelfTurn) {
                                stringResource(R.string.console_game_room_your_turn)
                            } else {
                                stringResource(
                                    R.string.console_game_room_partner_turn,
                                    uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
                                )
                            }
                            Text(text = turnText, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            TicTacToeGrid(
                                board = uiState.board,
                                enabled = uiState.isSelfTurn,
                                onCellClick = viewModel::makeMove,
                            )
                        }
                    }

                    resultBanner?.let { result ->
                        Spacer(modifier = Modifier.height(20.dp))
                        val text = when (result) {
                            RoundResult.SelfWin -> stringResource(R.string.console_game_room_you_win)
                            RoundResult.PartnerWin -> stringResource(
                                R.string.console_game_room_partner_wins,
                                uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
                            )
                            RoundResult.Draw -> stringResource(R.string.console_game_room_draw)
                        }
                        Text(text = text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.started) {
                ScoreBar(
                    selfDisplayName = uiState.selfDisplayName ?: stringResource(R.string.console_home_title_fallback),
                    selfScore = uiState.selfScore,
                    partnerDisplayName = uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
                    partnerScore = uiState.partnerScore,
                )
            }
        }
    }

    if (showPartnerLeftDialog) {
        AlertDialog(
            onDismissRequest = { showPartnerLeftDialog = false },
            title = { Text(stringResource(R.string.console_game_room_partner_left_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.console_game_room_partner_left_message,
                        uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { showPartnerLeftDialog = false }) {
                    Text(stringResource(R.string.console_game_room_partner_left_dismiss))
                }
            },
        )
    }
}

@Composable
private fun PlayerStatusRow(
    selfDisplayName: String?,
    selfPresent: Boolean,
    selfMark: Mark?,
    partnerDisplayName: String?,
    partnerPresent: Boolean,
    partnerMark: Mark?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
    ) {
        PlayerStatusEntry(
            name = selfDisplayName ?: stringResource(R.string.console_home_title_fallback),
            present = selfPresent,
            mark = selfMark,
        )
        PlayerStatusEntry(
            name = partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
            present = partnerPresent,
            mark = partnerMark,
        )
    }
}

@Composable
private fun PlayerStatusEntry(name: String, present: Boolean, mark: Mark?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Icon(
                painter = painterResource(if (present) R.drawable.ic_live_location_online else R.drawable.ic_live_location_offline),
                contentDescription = stringResource(
                    if (present) R.string.console_game_room_present_content_description else R.string.console_game_room_not_present_content_description,
                ),
                tint = if (present) ServerOnlineColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
        if (mark != null) {
            Text(
                text = mark.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TicTacToeGrid(board: List<Mark?>, enabled: Boolean, onCellClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    GridCell(
                        mark = board.getOrNull(index),
                        enabled = enabled && board.getOrNull(index) == null,
                        onClick = { onCellClick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(mark: Mark?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) {
            Text(
                text = mark.name,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (mark == Mark.X) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Uncapped running score for this room session — takes the same pinned-bottom, primary-colored
 * bar shape `LiveLocationMapView`'s ETA block uses, just with score text instead of a duration. */
@Composable
private fun ScoreBar(selfDisplayName: String, selfScore: Int, partnerDisplayName: String, partnerScore: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$selfDisplayName  $selfScore – $partnerScore  $partnerDisplayName",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
