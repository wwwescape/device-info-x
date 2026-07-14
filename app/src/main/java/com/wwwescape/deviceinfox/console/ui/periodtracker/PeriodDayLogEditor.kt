package com.wwwescape.deviceinfox.console.ui.periodtracker

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity
import java.util.Date

private enum class FlowChoice { LIGHT, MEDIUM, HEAVY, SPOTTING }

private fun CycleFlowIntensity?.toFlowChoice(hasSpotting: Boolean): FlowChoice? = when {
    hasSpotting -> FlowChoice.SPOTTING
    this == CycleFlowIntensity.LIGHT -> FlowChoice.LIGHT
    this == CycleFlowIntensity.MEDIUM -> FlowChoice.MEDIUM
    this == CycleFlowIntensity.HEAVY -> FlowChoice.HEAVY
    else -> null
}

/** A full-screen editor for a single logged day — the date itself is fixed (whichever calendar
 * day was tapped to open this, see `CycleCalendarView.onDayClick`), not editable here; only its
 * flow/symptoms/mood/notes are. Spotting and mood are presented as first-class chips even though
 * only the 3 real [CycleFlowIntensity] values are backed by a server field — both fold into the
 * same synced `symptoms` tag list before [onSave], so the caller's signature never has to change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodDayLogEditor(
    existing: PeriodDayLog?,
    dateEpochMillis: Long,
    onSave: (
        id: String?,
        dateEpochMillis: Long,
        flowIntensity: CycleFlowIntensity?,
        symptoms: List<String>,
        notes: String?,
    ) -> Unit,
    isSaving: Boolean,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var flowChoice by remember(existing) {
        mutableStateOf(existing?.flowIntensity.toFlowChoice(existing?.symptoms?.hasSpotting() == true))
    }
    var symptoms by remember(existing) { mutableStateOf(existing?.symptoms?.asSymptomTags()?.toSet().orEmpty()) }
    var moods by remember(existing) { mutableStateOf(existing?.symptoms?.asMoodTags()?.toSet().orEmpty()) }
    var notes by rememberSaveable(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    fun save() {
        val flowIntensity = when (flowChoice) {
            FlowChoice.LIGHT -> CycleFlowIntensity.LIGHT
            FlowChoice.MEDIUM -> CycleFlowIntensity.MEDIUM
            FlowChoice.HEAVY -> CycleFlowIntensity.HEAVY
            FlowChoice.SPOTTING, null -> null
        }
        val spottingTag = if (flowChoice == FlowChoice.SPOTTING) listOf(SPOTTING_KEY) else emptyList()
        val tags = symptoms.toList() + moods.toList() + spottingTag
        onSave(
            existing?.id,
            dateEpochMillis,
            flowIntensity,
            tags,
            notes.trim().ifBlank { null },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false is what makes this dialog's own window (a Dialog opens
        // a separate Android window from the Activity's) actually report live IME insets to
        // Compose at all — left at its default true, the window falls back to the legacy
        // SOFT_INPUT_ADJUST_UNSPECIFIED behavior regardless of ConsoleActivity's own manifest
        // adjustResize, and the keyboard just overlaps whatever's focused with no resize/pan.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                // Excludes ime from Scaffold's own default (safeDrawing, which includes it) since
                // imePadding() below reserves that space instead — keeps top/nav-bar/cutout
                // handling unchanged, same "reserve it exactly once" reasoning as
                // ConsoleTabContentWindowInsets.
                contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                stringResource(
                                    if (existing != null) R.string.console_period_edit_entry_title else R.string.console_period_new_entry_title,
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                            }
                        },
                        actions = {
                            if (existing != null) {
                                TextButton(onClick = onDeleteRequest, enabled = !isSaving) {
                                    Text(
                                        stringResource(R.string.console_period_delete_confirm_action),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            TextButton(onClick = ::save, enabled = !isSaving) {
                                Text(stringResource(R.string.console_calendar_save_action))
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BigDateHeader(epochMillis = dateEpochMillis)

                    LabeledField(stringResource(R.string.console_period_flow_label)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            val flowOptions = listOf(
                                FlowChoice.LIGHT to stringResource(R.string.console_period_flow_light),
                                FlowChoice.MEDIUM to stringResource(R.string.console_period_flow_medium),
                                FlowChoice.HEAVY to stringResource(R.string.console_period_flow_heavy),
                                FlowChoice.SPOTTING to stringResource(R.string.console_period_flow_spotting),
                            )
                            flowOptions.forEach { (value, label) ->
                                FilterChip(
                                    selected = flowChoice == value,
                                    onClick = { flowChoice = if (flowChoice == value) null else value },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    LabeledField(stringResource(R.string.console_period_symptoms_label)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SYMPTOM_KEYS.forEach { key ->
                                FilterChip(
                                    selected = key in symptoms,
                                    onClick = { symptoms = if (key in symptoms) symptoms - key else symptoms + key },
                                    label = { Text(symptomLabel(key)) },
                                )
                            }
                        }
                    }

                    LabeledField(stringResource(R.string.console_period_mood_label)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MOOD_KEYS.forEach { key ->
                                FilterChip(
                                    selected = key in moods,
                                    onClick = { moods = if (key in moods) moods - key else moods + key },
                                    label = { Text(moodLabel(key)) },
                                )
                            }
                        }
                    }

                    LabeledField(stringResource(R.string.console_calendar_notes_label)) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            minLines = 2,
                            colors = periodUnderlineFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigDateHeader(epochMillis: Long) {
    val context = LocalContext.current
    Text(
        text = DateFormat.getMediumDateFormat(context).format(Date(epochMillis)),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
internal fun periodUnderlineFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
)
