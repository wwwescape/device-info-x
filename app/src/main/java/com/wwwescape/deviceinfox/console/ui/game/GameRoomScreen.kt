package com.wwwescape.deviceinfox.console.ui.game

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.game.Mark
import com.wwwescape.deviceinfox.console.ui.components.Fireworks
import com.wwwescape.deviceinfox.console.ui.components.PresenceAvatar
import com.wwwescape.deviceinfox.console.ui.components.ScreenPresenceIcon
import com.wwwescape.deviceinfox.console.ui.components.ScreenPresenceTier
import com.wwwescape.deviceinfox.console.ui.settings.ServerOnlineColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESULT_BANNER_MILLIS = 1_800L

// WS actions have no request/response pairing to hook a "done" signal off — same debounce
// shape as CallRoomScreen's own StartRow, applied to Start Game and each cell tap (the board
// doesn't optimistically update locally, so a same-cell double-tap would otherwise fire twice).
private const val GAME_ACTION_DEBOUNCE_MILLIS = 1_000L
private val GRID_CELL_SIZE = 88.dp
private val GRID_CELL_GAP = 8.dp
private val AVATAR_SIZE = 56.dp

/** Matches `Fireworks.kt`'s own gold (`FIREWORK_COLORS[0]`) — the win-moment-is-allowed-to-be-
 * playful exception this screen already has for that celebration burst extends to this one text
 * line too, rather than inventing a new one-off color just for the gradient. */
private val WinGradientGold = Color(0xFFFFD54F)

/** Shared Tic Tac Toe game room — Settings' dice icon. Same overall shape as `LiveLocationScreen`
 * (back-arrow `TopAppBar`, a self/partner status row up top, a "waiting for partner" placeholder
 * swapping in for real content), minus the toggle/map/ETA specifics: the status row also carries
 * each player's assigned mark, the placeholder becomes a Start button once both are present, and
 * the bottom block becomes the running score instead of an ETA. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameRoomScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: GameRoomViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partnerPresenceTier by viewModel.partnerPresenceTier.collectAsStateWithLifecycle()

    // Guards Start Game / cell taps against a double-tap firing two WS sends — these are
    // fire-and-forget with no response to hook a reset off, same shape as CallRoomScreen's
    // StartRow debounce.
    var actionDebounced by remember { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()
    fun debouncedAction(action: () -> Unit) {
        if (actionDebounced) return
        actionDebounced = true
        actionScope.launch { delay(GAME_ACTION_DEBOUNCE_MILLIS); actionDebounced = false }
        action()
    }

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

    // Purely a local display counter, not server state — collects the same roundResultEvent the
    // banner above already does (a SharedFlow, so multiple independent collectors are fine),
    // incrementing on every resolved round including draws (which don't move either score).
    // Reset whenever the room comes back to its fresh/never-played state, which also covers a
    // partner-left full wipe (GameRoomRepository.leaveRoom/handleEvent both reset scores to 0).
    var roundNumber by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        viewModel.roundResultEvent.collect { roundNumber++ }
    }
    LaunchedEffect(uiState.started, uiState.selfScore, uiState.partnerScore) {
        if (!uiState.started && uiState.selfScore == 0 && uiState.partnerScore == 0) roundNumber = 1
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
                partnerTier = partnerPresenceTier,
                partnerMark = uiState.partnerMark,
            )

            if (uiState.started) {
                Text(
                    text = stringResource(R.string.console_game_room_round_label, roundNumber),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (resultBanner is RoundResult.SelfWin) {
                    Fireworks(modifier = Modifier.fillMaxSize())
                }

                // While a win banner is showing, the board/highlight come from the frozen
                // snapshot the result itself carries — uiState.board has already moved on to the
                // reset board for the next round by the time this fires (see RoundResult's own
                // doc comment), so rendering it live here would just show an empty grid.
                val frozenResult = resultBanner
                val displayedBoard = when (frozenResult) {
                    is RoundResult.SelfWin -> frozenResult.winningBoard
                    is RoundResult.PartnerWin -> frozenResult.winningBoard
                    else -> uiState.board
                }
                val winningLine = when (frozenResult) {
                    is RoundResult.SelfWin -> frozenResult.winningLine
                    is RoundResult.PartnerWin -> frozenResult.winningLine
                    else -> emptyList()
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
                        !uiState.started -> Button(
                            onClick = { debouncedAction(viewModel::startGame) },
                            enabled = !actionDebounced,
                        ) {
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
                            val selfMark = uiState.selfMark
                            if (uiState.isSelfTurn && selfMark != null) {
                                Text(
                                    text = stringResource(R.string.console_game_room_place_mark_subtitle, selfMark.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            TicTacToeGrid(
                                board = displayedBoard,
                                winningLine = winningLine,
                                enabled = uiState.isSelfTurn && resultBanner == null && !actionDebounced,
                                onCellClick = { cell -> debouncedAction { viewModel.makeMove(cell) } },
                            )
                        }
                    }

                    resultBanner?.let { result ->
                        Spacer(modifier = Modifier.height(20.dp))
                        val partnerName = uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback)
                        when (result) {
                            is RoundResult.SelfWin -> {
                                Text(
                                    text = stringResource(R.string.console_game_room_you_win),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, WinGradientGold)),
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.console_game_room_win_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is RoundResult.PartnerWin -> {
                                Text(
                                    text = stringResource(R.string.console_game_room_partner_wins, partnerName),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.console_game_room_partner_win_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RoundResult.Draw -> {
                                Text(
                                    text = stringResource(R.string.console_game_room_draw),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.console_game_room_draw_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
    partnerTier: ScreenPresenceTier,
    partnerMark: Mark?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
    ) {
        // Self only ever needs the plain "am I on this screen" boolean — this device is
        // definitionally online while it's the one rendering the screen, so there's no "amber"
        // tier for self to ever meaningfully show, unlike the partner below.
        PlayerAvatarColumn(
            name = selfDisplayName ?: stringResource(R.string.console_home_title_fallback),
            mark = selfMark,
            isSelf = true,
        ) {
            Icon(
                painter = painterResource(if (selfPresent) R.drawable.ic_live_location_online else R.drawable.ic_live_location_offline),
                contentDescription = stringResource(
                    if (selfPresent) R.string.console_game_room_present_content_description else R.string.console_game_room_not_present_content_description,
                ),
                tint = if (selfPresent) ServerOnlineColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
        }
        PlayerAvatarColumn(
            name = partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
            mark = partnerMark,
            isSelf = false,
        ) {
            ScreenPresenceIcon(
                tier = partnerTier,
                contentDescription = partnerPresenceTierContentDescription(partnerTier),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Self/partner initials-in-a-circle, self solid violet and partner neutral gray, plus this
 * screen's own name + mark caption underneath — wraps the shared [PresenceAvatar] (originally
 * built here, now promoted to `console/ui/components` and reused by Live Location/Call Room too,
 * see TODOS.md) rather than a one-off implementation. */
@Composable
private fun PlayerAvatarColumn(name: String, mark: Mark?, isSelf: Boolean, presenceBadge: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PresenceAvatar(name = name, isSelf = isSelf, presenceBadge = presenceBadge, size = AVATAR_SIZE)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        if (mark != null) {
            Text(text = mark.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun partnerPresenceTierContentDescription(tier: ScreenPresenceTier): String = when (tier) {
    ScreenPresenceTier.ON_SCREEN -> stringResource(R.string.console_game_room_present_content_description)
    ScreenPresenceTier.IN_APP -> stringResource(R.string.console_game_room_partner_in_app_content_description)
    ScreenPresenceTier.NOT_IN_APP -> stringResource(R.string.console_game_room_not_present_content_description)
}

@Composable
private fun TicTacToeGrid(board: List<Mark?>, winningLine: List<Int>, enabled: Boolean, onCellClick: (Int) -> Unit) {
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(GRID_CELL_GAP)) {
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_CELL_GAP)) {
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
        if (winningLine.size == 3) {
            WinningLineOverlay(winningLine = winningLine, modifier = Modifier.matchParentSize())
        }
    }
}

/** The colored line/glow connecting the three winning cells once a round resolves — previously
 * a win was only ever communicated via the "You win!" banner + `Fireworks`, the board itself
 * never showed *why*. A single line from the first to the last winning cell's center passes
 * through the middle one too, since all three are always colinear. Two overlapping strokes (a
 * wide translucent one underneath, a slimmer solid one on top) give the glow rather than a flat
 * line, in the app's own primary color rather than introducing a new one-off hue. */
@Composable
private fun WinningLineOverlay(winningLine: List<Int>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val cellSizePx = GRID_CELL_SIZE.toPx()
        val gapPx = GRID_CELL_GAP.toPx()
        fun cellCenter(index: Int): Offset {
            val row = index / 3
            val col = index % 3
            return Offset(
                x = col * (cellSizePx + gapPx) + cellSizePx / 2f,
                y = row * (cellSizePx + gapPx) + cellSizePx / 2f,
            )
        }
        val start = cellCenter(winningLine.first())
        val end = cellCenter(winningLine.last())
        drawLine(color = color.copy(alpha = 0.35f), start = start, end = end, strokeWidth = 20.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = color, start = start, end = end, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun GridCell(mark: Mark?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(GRID_CELL_SIZE)
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
