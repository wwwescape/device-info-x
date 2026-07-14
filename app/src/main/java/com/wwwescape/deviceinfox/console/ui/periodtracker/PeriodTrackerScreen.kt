package com.wwwescape.deviceinfox.console.ui.periodtracker

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.CycleVisibility
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity
import com.wwwescape.deviceinfox.console.ui.components.SHOW_LOCK_ICON
import com.wwwescape.deviceinfox.console.ui.components.SettingsGearIcon
import com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabContentWindowInsets

/** What [PeriodDayLogEditor] is currently open for — a brand-new day log for a given date (the
 * FAB, or a tap on an unlogged calendar day), or an existing one (a tap on a logged day). */
private sealed interface LogEditorTarget {
    data class New(val dateEpochMillis: Long) : LogEditorTarget
    data class Edit(val dayLog: PeriodDayLog) : LogEditorTarget
}

/** Mirrors `CalendarScreen`'s own `CalendarViewMode` (Month/Timeline/Stats) — same hoisted-state,
 * no-ViewModel shape, just renamed for this tab's own Insights/History content. */
private enum class PeriodViewMode { MONTH, INSIGHTS, HISTORY }

/** A brand-new day log staged behind [UnexpectedCycleStartConfirmationDialog] — only ever built
 * for a new entry (never an edit, see `CycleRepository.isUnexpectedCycleStart`'s own doc
 * comment), so there's no `id` field to carry. */
private data class PendingDayLogSave(
    val dateEpochMillis: Long,
    val flowIntensity: CycleFlowIntensity?,
    val symptoms: List<String>,
    val notes: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTrackerScreen(
    onSettingsClick: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeriodTrackerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationActive.collectAsStateWithLifecycle()

    var editorTarget by remember { mutableStateOf<LogEditorTarget?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingSaveConfirmation by remember { mutableStateOf<PendingDayLogSave?>(null) }
    var viewMode by remember { mutableStateOf(PeriodViewMode.MONTH) }

    val visibility = uiState.visibility
    // Onboarding's visibility is fully derived, not a separate flag — it's the self tab with
    // zero logged days. Once completeOnboarding() logs the first batch, this is false forever.
    val showOnboarding = uiState.canEdit && uiState.dayLogs.isEmpty()

    val context = LocalContext.current
    val genericErrorMessage = stringResource(R.string.console_error_network)
    val saveSuccessMessage = stringResource(R.string.console_period_save_success)
    val deleteSuccessMessage = stringResource(R.string.console_period_delete_success)
    LaunchedEffect(Unit) {
        viewModel.saveFailed.collect { detail -> Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleteFailed.collect { detail -> Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.saveSucceeded.collect {
            Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
            editorTarget = null
            pendingSaveConfirmation = null
        }
    }
    LaunchedEffect(Unit) {
        viewModel.deleteSucceeded.collect {
            Toast.makeText(context, deleteSuccessMessage, Toast.LENGTH_SHORT).show()
            pendingDeleteId = null
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ConsoleTabContentWindowInsets,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_period_title)) },
                    actions = {
                        if (SHOW_LOCK_ICON) {
                            IconButton(onClick = onLock) {
                                Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.console_home_lock_action))
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            SettingsGearIcon(isLiveLocationActive = isLiveLocationActive, isUpdateAvailable = isUpdateAvailable)
                        }
                    },
                )
                if (visibility is CycleVisibility.TwoCycles) {
                    val selectedIndex = if (uiState.selectedPartnerId == visibility.partnerId) 1 else 0
                    TabRow(selectedTabIndex = selectedIndex) {
                        Tab(
                            selected = selectedIndex == 0,
                            onClick = { viewModel.selectTab(visibility.selfId) },
                            text = { Text(stringResource(R.string.console_period_tab_me)) },
                        )
                        Tab(
                            selected = selectedIndex == 1,
                            onClick = { viewModel.selectTab(visibility.partnerId) },
                            text = { Text(uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback)) },
                        )
                    }
                }
                if (!showOnboarding) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        SegmentedButton(
                            selected = viewMode == PeriodViewMode.MONTH,
                            onClick = { viewMode = PeriodViewMode.MONTH },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        ) {
                            Text(stringResource(R.string.console_period_view_month))
                        }
                        SegmentedButton(
                            selected = viewMode == PeriodViewMode.INSIGHTS,
                            onClick = { viewMode = PeriodViewMode.INSIGHTS },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        ) {
                            Text(stringResource(R.string.console_period_view_insights))
                        }
                        SegmentedButton(
                            selected = viewMode == PeriodViewMode.HISTORY,
                            onClick = { viewMode = PeriodViewMode.HISTORY },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        ) {
                            Text(stringResource(R.string.console_period_view_history))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState.canEdit && !showOnboarding && viewMode == PeriodViewMode.MONTH) {
                FloatingActionButton(onClick = { editorTarget = LogEditorTarget.New(System.currentTimeMillis()) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.console_period_add_action))
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showOnboarding) {
                CycleSetupScreen(
                    onGetStarted = { startDate, cycleLength, periodLength, lutealPhaseLength ->
                        viewModel.completeOnboarding(startDate, cycleLength, periodLength, lutealPhaseLength)
                    },
                    isSaving = isSaving,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                when (viewMode) {
                    PeriodViewMode.MONTH -> CycleCalendarView(
                        dayLogs = uiState.dayLogs,
                        prediction = uiState.prediction,
                        canEdit = uiState.canEdit,
                        intimacyDates = uiState.intimacyDates,
                        onEditDayLog = { dayLog -> editorTarget = LogEditorTarget.Edit(dayLog) },
                        onNewDayLog = { date -> editorTarget = LogEditorTarget.New(date) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    PeriodViewMode.INSIGHTS -> PeriodInsightsView(
                        dayLogs = uiState.dayLogs,
                        modifier = Modifier.fillMaxSize(),
                    )
                    PeriodViewMode.HISTORY -> PeriodHistoryView(
                        dayLogs = uiState.dayLogs,
                        canEdit = uiState.canEdit,
                        onItemClick = { dayLog -> editorTarget = LogEditorTarget.Edit(dayLog) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    editorTarget?.let { target ->
        val existing = (target as? LogEditorTarget.Edit)?.dayLog
        PeriodDayLogEditor(
            existing = existing,
            dateEpochMillis = when (target) {
                is LogEditorTarget.New -> target.dateEpochMillis
                is LogEditorTarget.Edit -> target.dayLog.dateEpochMillis
            },
            onSave = { id, date, flow, symptoms, notes ->
                if (id == null && viewModel.isUnexpectedCycleStart(date, flow)) {
                    pendingSaveConfirmation = PendingDayLogSave(date, flow, symptoms, notes)
                    editorTarget = null
                } else {
                    viewModel.saveDayLog(id, date, flow, symptoms, notes)
                }
            },
            isSaving = isSaving,
            onDeleteRequest = {
                existing?.let { pendingDeleteId = it.id }
                editorTarget = null
            },
            onDismiss = { editorTarget = null },
        )
    }

    pendingDeleteId?.let { id ->
        DeleteCycleEntryConfirmationDialog(
            isPending = isDeleting,
            onConfirm = { viewModel.deleteDayLog(id) },
            onDismiss = { pendingDeleteId = null },
        )
    }

    pendingSaveConfirmation?.let { pending ->
        UnexpectedCycleStartConfirmationDialog(
            isPending = isSaving,
            onConfirm = {
                viewModel.saveDayLog(null, pending.dateEpochMillis, pending.flowIntensity, pending.symptoms, pending.notes)
            },
            onDismiss = { pendingSaveConfirmation = null },
        )
    }
}
