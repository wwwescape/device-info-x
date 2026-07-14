package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.db.PartnerGender

/** Period Tracker: cycle length settings (only shown when the self profile's committed gender is
 * FEMALE, matching `CycleRepository`'s visibility rule for who gets a cycle at all — same
 * committed-gender read [ProfileScreen]'s own gender selector uses) plus the Period-scoped
 * "Delete data" action. The row itself on [ConsoleSettingsMainScreen] stays conditionally hidden
 * the same way the bottom tab already is, not a new gating mechanism. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTrackerSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsoleSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCycleLengthDialog by remember { mutableStateOf(false) }
    var showPeriodLengthDialog by remember { mutableStateOf(false) }
    var showLutealPhaseDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_period_tracker)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.gender == PartnerGender.FEMALE) {
                CycleTrackingSection(
                    averageCycleLengthDaysSeed = uiState.averageCycleLengthDaysSeed,
                    averagePeriodLengthDaysSeed = uiState.averagePeriodLengthDaysSeed,
                    averageLutealPhaseDaysSeed = uiState.averageLutealPhaseDaysSeed,
                    onCycleLengthClick = { showCycleLengthDialog = true },
                    onPeriodLengthClick = { showPeriodLengthDialog = true },
                    onLutealPhaseClick = { showLutealPhaseDialog = true },
                )
            }
            DataDeletionCategorySection(viewModel, DataSection.PERIOD)
        }
    }

    if (showCycleLengthDialog) {
        CycleLengthDialog(
            currentValue = uiState.averageCycleLengthDaysSeed,
            onSave = viewModel::setAverageCycleLengthDaysSeed,
            onDismiss = { showCycleLengthDialog = false },
        )
    }
    if (showPeriodLengthDialog) {
        PeriodLengthDialog(
            currentValue = uiState.averagePeriodLengthDaysSeed,
            onSave = viewModel::setAveragePeriodLengthDaysSeed,
            onDismiss = { showPeriodLengthDialog = false },
        )
    }
    if (showLutealPhaseDialog) {
        LutealPhaseDialog(
            currentValue = uiState.averageLutealPhaseDaysSeed,
            onSave = viewModel::setAverageLutealPhaseDaysSeed,
            onDismiss = { showLutealPhaseDialog = false },
        )
    }
}

@Composable
private fun CycleTrackingSection(
    averageCycleLengthDaysSeed: Int?,
    averagePeriodLengthDaysSeed: Int?,
    averageLutealPhaseDaysSeed: Int?,
    onCycleLengthClick: () -> Unit,
    onPeriodLengthClick: () -> Unit,
    onLutealPhaseClick: () -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.console_settings_section_period_tracker)) {
        DaysValueRow(
            label = stringResource(R.string.console_settings_cycle_length_row_label),
            value = averageCycleLengthDaysSeed,
            defaultValue = 28,
            onClick = onCycleLengthClick,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DaysValueRow(
            label = stringResource(R.string.console_period_period_length_label),
            value = averagePeriodLengthDaysSeed,
            defaultValue = 5,
            onClick = onPeriodLengthClick,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DaysValueRow(
            label = stringResource(R.string.console_settings_luteal_phase_row_label),
            value = averageLutealPhaseDaysSeed,
            defaultValue = 14,
            onClick = onLutealPhaseClick,
        )
    }
}

@Composable
private fun DaysValueRow(label: String, value: Int?, defaultValue: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val valueText = value?.let { stringResource(R.string.console_period_days_value, it) }
            ?: stringResource(R.string.console_settings_days_default_value, defaultValue)
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
