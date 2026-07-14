package com.wwwescape.deviceinfox.console.ui.calendar

import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.wwwescape.deviceinfox.console.ui.components.AppTimePickerDialog
import com.wwwescape.deviceinfox.console.ui.components.withDate
import com.wwwescape.deviceinfox.console.ui.components.withTime
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem
import com.wwwescape.deviceinfox.console.data.calendar.IntimacyLogInput
import com.wwwescape.deviceinfox.console.data.db.EventType
import com.wwwescape.deviceinfox.console.data.db.INTIMACY_INITIATED_BY_BOTH
import com.wwwescape.deviceinfox.console.data.db.INTIMACY_ORGASMED_BY_NONE
import com.wwwescape.deviceinfox.console.data.db.IntimacyLocation
import com.wwwescape.deviceinfox.console.data.db.IntimacyMood
import com.wwwescape.deviceinfox.console.data.db.IntimacyPosition
import com.wwwescape.deviceinfox.console.data.db.PartnerEntity
import com.wwwescape.deviceinfox.console.data.db.isIntimacyEligible
import com.wwwescape.deviceinfox.console.data.db.referenceDateEpochMillis
import com.wwwescape.deviceinfox.console.data.db.supportsCancellation
import com.wwwescape.deviceinfox.console.data.db.supportsRecurrence
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import java.util.Date

/** A single unified editor for every [EventType] — the old two-flow (planned date / reminder)
 * split is gone; every type shows the same field set (Title, Type, All Day, date range,
 * Recurring when the type allows it, Location, Notification, Description), plus an Intimacy Log
 * section when [CalendarItem.isIntimacyEligible] would hold for the
 * currently-selected type/date. [existing] null means "create"; non-null pre-fills every field
 * and saves back to that same item's id. [selfPartner]/[partnerPartner] feed the "Who started
 * it" picker's real first-name labels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorDialog(
    existing: CalendarItem?,
    selfPartner: PartnerEntity?,
    partnerPartner: PartnerEntity?,
    onSave: (
        id: String?,
        title: String,
        type: EventType,
        notes: String?,
        startAtEpochMillis: Long,
        endAtEpochMillis: Long?,
        isAllDay: Boolean,
        location: String?,
        recurrenceRule: String?,
        cancelled: Boolean,
        cancellationReason: String?,
        reminderMinutesBefore: List<Int>,
        intimacy: IntimacyLogInput?,
        newMediaUris: List<Uri>,
        newDocumentUris: List<Uri>,
        keptAttachmentMediaIds: List<String>,
        newCoverPhotoUri: Uri?,
        keptCoverMediaId: String?,
    ) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
) {
    var existingAttachments by remember(existing) { mutableStateOf(existing?.attachments.orEmpty()) }
    var newMediaUris by remember(existing) { mutableStateOf<List<Uri>>(emptyList()) }
    var newDocumentUris by remember(existing) { mutableStateOf<List<Uri>>(emptyList()) }
    // Null means "unchanged" — tapping the hero banner's icon replaces this with a freshly-picked
    // photo; there's no separate "remove cover" affordance today (see EventHeroHeader/save()).
    var newCoverPhotoUri by remember(existing) { mutableStateOf<Uri?>(null) }
    var type by remember(existing) { mutableStateOf(existing?.type ?: EventType.PLANNED_DATE) }
    var title by rememberSaveable(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var description by rememberSaveable(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var location by rememberSaveable(existing) { mutableStateOf(existing?.location.orEmpty()) }
    var isAllDay by remember(existing) { mutableStateOf(existing?.isAllDay ?: true) }
    var startAtEpochMillis by remember(existing) { mutableStateOf(existing?.startAtEpochMillis) }
    var endAtEpochMillis by remember(existing) { mutableStateOf(existing?.endAtEpochMillis ?: existing?.startAtEpochMillis) }
    var recurrencePreset by remember(existing) { mutableStateOf(existing?.recurrenceRule.toRecurrencePreset()) }
    var notificationEnabled by remember(existing) { mutableStateOf(existing?.hasNotification ?: false) }
    var cancelled by remember(existing) { mutableStateOf(existing?.cancelled ?: false) }
    var cancellationReason by rememberSaveable(existing) { mutableStateOf(existing?.cancellationReason.orEmpty()) }

    var intimacyRating by remember(existing) { mutableStateOf(existing?.intimacyRating) }
    var intimacyMoods by remember(existing) { mutableStateOf(existing?.intimacyMoods?.toSet().orEmpty()) }
    var intimacyDurationMinutes by remember(existing) { mutableStateOf(existing?.intimacyDurationMinutes) }
    var intimacyCustomDurationText by rememberSaveable(existing) { mutableStateOf("") }
    var intimacyInitiatedBy by remember(existing) { mutableStateOf(existing?.intimacyInitiatedByPartnerId) }
    var intimacyProtectionUsed by remember(existing) { mutableStateOf(existing?.intimacyProtectionUsed) }
    var intimacyRoundsText by rememberSaveable(existing) { mutableStateOf(existing?.intimacyRounds?.toString().orEmpty()) }
    var intimacyOrgasmedBy by remember(existing) { mutableStateOf(existing?.intimacyOrgasmedByPartnerId) }
    var intimacyPositions by remember(existing) { mutableStateOf(existing?.intimacyPositions?.toSet().orEmpty()) }
    var intimacyLocations by remember(existing) { mutableStateOf(existing?.intimacyLocations?.toSet().orEmpty()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // Only PLANNED_DRIVE/UNPLANNED_DRIVE lock Locations to Car — reset whenever the type swaps
    // into that state, same reactive-reset spirit as the Recurring section's clamp above.
    val locationsLockedToCar = type == EventType.PLANNED_DRIVE || type == EventType.UNPLANNED_DRIVE
    LaunchedEffect(locationsLockedToCar) {
        if (locationsLockedToCar) intimacyLocations = setOf(IntimacyLocation.CAR.label)
    }

    val showIntimacySection = startAtEpochMillis?.let { start ->
        isIntimacyEligible(type, referenceDateEpochMillis(start, endAtEpochMillis))
    } == true

    val canSave = title.isNotBlank() && startAtEpochMillis != null && !isSaving

    fun save() {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim().ifBlank { null }
        val trimmedLocation = location.trim().ifBlank { null }
        val recurrenceRule = if (type.supportsRecurrence) recurrencePreset.toRecurrenceRule() else null
        // Same "clamp to false when ineligible" spirit as recurrenceRule above — the toggle being
        // hidden already prevents editing this, this just guarantees nothing stale from before a
        // type swap ever reaches save().
        val cancelledValue = cancelled && type.supportsCancellation
        // The reason textbox only ever shows alongside an active cancellation — clamp it the same
        // way, so unchecking Cancelled (or a type swap clearing it above) always drops any
        // previously-typed reason too.
        val cancellationReasonValue = cancellationReason.trim().ifBlank { null }.takeIf { cancelledValue }
        val reminderMinutesBefore = if (notificationEnabled) listOf(1440, 5) else emptyList()
        // Same "clamp to null when ineligible" spirit as recurrenceRule above — the section
        // being hidden already prevents editing these, this just guarantees nothing stale from
        // before a type/date swap ever reaches save().
        val intimacy = if (showIntimacySection) {
            IntimacyLogInput(
                rating = intimacyRating,
                durationMinutes = intimacyDurationMinutes,
                initiatedByPartnerId = intimacyInitiatedBy,
                protectionUsed = intimacyProtectionUsed,
                positions = intimacyPositions.toList(),
                locations = intimacyLocations.toList(),
                moods = intimacyMoods.toList(),
                rounds = intimacyRoundsText.toIntOrNull()?.coerceIn(1, 10),
                orgasmedByPartnerId = intimacyOrgasmedBy,
            )
        } else {
            null
        }
        onSave(
            existing?.id,
            trimmedTitle,
            type,
            trimmedDescription,
            startAtEpochMillis!!,
            // A range collapses to a single day/instant when end == start — only send a real
            // end_at when the user actually pushed it past the start.
            endAtEpochMillis?.takeIf { it != startAtEpochMillis },
            isAllDay,
            trimmedLocation,
            recurrenceRule,
            cancelledValue,
            cancellationReasonValue,
            reminderMinutesBefore,
            intimacy,
            newMediaUris,
            newDocumentUris,
            existingAttachments.mapNotNull { it.mediaId },
            newCoverPhotoUri,
            existing?.coverMediaId,
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
                                    if (existing != null) R.string.console_calendar_edit_event_title else R.string.console_calendar_new_event_title,
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                            }
                        },
                        actions = {
                            TextButton(onClick = ::save, enabled = canSave) {
                                Text(stringResource(R.string.console_calendar_save_action))
                            }
                        },
                    )
                },
            ) { innerPadding ->
                val coverPhotoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri -> if (uri != null) newCoverPhotoUri = uri }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EventHeroHeader(
                        type = type,
                        coverPhotoModel = newCoverPhotoUri ?: existing?.coverPhotoFilePath,
                        onIconClick = {
                            coverPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        LabeledField(stringResource(R.string.console_calendar_event_title_label)) {
                            TextField(
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.console_calendar_event_title_placeholder)) },
                                colors = underlineFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        LabeledField(stringResource(R.string.console_calendar_category_label)) {
                            TypeDropdown(
                                selected = type,
                                onSelected = { newType ->
                                    type = newType
                                    // A type swap away from a recurring-capable type must clear a
                                    // previously-chosen recurrence so save() never sends a
                                    // combination the server would reject (see CalendarRepository.saveEvent's clamp).
                                    if (!newType.supportsRecurrence) recurrencePreset = RecurrencePreset.NONE
                                    // Same reasoning for Cancelled — only Planned Date/Planned
                                    // Drive/Planned Trip/Custom may carry it.
                                    if (!newType.supportsCancellation) cancelled = false
                                },
                            )
                        }

                        if (type.supportsCancellation) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.console_calendar_cancelled_label),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = cancelled,
                                        onCheckedChange = { checked ->
                                            cancelled = checked
                                            // Unchecking clears any previously-typed reason — same
                                            // reactive-reset spirit as the type-swap resets above.
                                            if (!checked) cancellationReason = ""
                                        },
                                    )
                                }
                                if (cancelled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LabeledField(stringResource(R.string.console_calendar_cancellation_reason_label)) {
                                        TextField(
                                            value = cancellationReason,
                                            onValueChange = { cancellationReason = it },
                                            placeholder = { Text(stringResource(R.string.console_calendar_cancellation_reason_placeholder)) },
                                            minLines = 2,
                                            colors = underlineFieldColors(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.console_calendar_all_day_label),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CalendarDateTimeField(
                                dateLabel = stringResource(R.string.console_calendar_start_date_label),
                                timeLabel = stringResource(R.string.console_calendar_start_time_label),
                                valueEpochMillis = startAtEpochMillis,
                                isAllDay = isAllDay,
                                onDateClick = { showStartDatePicker = true },
                                onTimeClick = { showStartTimePicker = true },
                            )
                            CalendarDateTimeField(
                                dateLabel = stringResource(R.string.console_calendar_end_date_label),
                                timeLabel = stringResource(R.string.console_calendar_end_time_label),
                                valueEpochMillis = endAtEpochMillis,
                                isAllDay = isAllDay,
                                onDateClick = { showEndDatePicker = true },
                                onTimeClick = { showEndTimePicker = true },
                            )
                        }

                        if (type.supportsRecurrence) {
                            LabeledField(stringResource(R.string.console_calendar_frequency_label)) {
                                RecurrenceDropdown(selected = recurrencePreset, onSelected = { recurrencePreset = it })
                            }
                        }

                        LabeledField(stringResource(R.string.console_calendar_location_label)) {
                            TextField(
                                value = location,
                                onValueChange = { location = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.console_calendar_location_placeholder)) },
                                colors = underlineFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.console_calendar_notification_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(checked = notificationEnabled, onCheckedChange = { notificationEnabled = it })
                            }
                            Text(
                                text = stringResource(R.string.console_calendar_notification_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        LabeledField(stringResource(R.string.console_calendar_description_label)) {
                            TextField(
                                value = description,
                                onValueChange = { description = it },
                                placeholder = { Text(stringResource(R.string.console_calendar_notes_memories_placeholder)) },
                                minLines = 3,
                                colors = underlineFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        SectionDivider(
                            label = stringResource(R.string.console_calendar_attachments_section_header),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        EventAttachmentsField(
                            existingAttachments = existingAttachments,
                            newMediaUris = newMediaUris,
                            newDocumentUris = newDocumentUris,
                            onRemoveExisting = { attachment -> existingAttachments = existingAttachments - attachment },
                            onRemoveNewMedia = { uri -> newMediaUris = newMediaUris - uri },
                            onRemoveNewDocument = { uri -> newDocumentUris = newDocumentUris - uri },
                            onMediaPicked = { uris -> newMediaUris = newMediaUris + uris },
                            onDocumentsPicked = { uris -> newDocumentUris = newDocumentUris + uris },
                        )

                        if (showIntimacySection) {
                            SectionDivider(
                                label = stringResource(R.string.console_calendar_intimacy_section_header),
                                modifier = Modifier.padding(top = 16.dp),
                            )

                            LabeledField(stringResource(R.string.console_calendar_rating_label)) {
                                RatingPicker(rating = intimacyRating, onRatingChange = { intimacyRating = it })
                            }

                            LabeledField(stringResource(R.string.console_calendar_mood_label)) {
                                ChipMultiSelect(
                                    options = IntimacyMood.entries.map { it.label },
                                    selected = intimacyMoods,
                                    onToggle = { label -> intimacyMoods = intimacyMoods.toggle(label) },
                                )
                            }

                            LabeledField(stringResource(R.string.console_calendar_initiated_by_label)) {
                                val unknownLabel = stringResource(R.string.console_calendar_initiated_by_unknown)
                                InitiatedByPicker(
                                    selected = intimacyInitiatedBy,
                                    selfId = selfPartner?.id,
                                    selfLabel = selfPartner?.firstName?.ifBlank { null } ?: unknownLabel,
                                    partnerId = partnerPartner?.id,
                                    partnerLabel = partnerPartner?.firstName?.ifBlank { null } ?: unknownLabel,
                                    onSelected = { intimacyInitiatedBy = it },
                                )
                            }

                            LabeledField(stringResource(R.string.console_calendar_protection_used_label)) {
                                ProtectionUsedPicker(
                                    protectionUsed = intimacyProtectionUsed,
                                    onChange = { intimacyProtectionUsed = it },
                                )
                            }

                            LabeledField(stringResource(R.string.console_calendar_duration_label)) {
                                DurationPicker(
                                    durationMinutes = intimacyDurationMinutes,
                                    customText = intimacyCustomDurationText,
                                    onPresetSelected = { minutes ->
                                        intimacyDurationMinutes = minutes
                                        intimacyCustomDurationText = ""
                                    },
                                    onCustomTextChange = { text ->
                                        intimacyCustomDurationText = text
                                        intimacyDurationMinutes = text.toIntOrNull()
                                    },
                                )
                            }

                            LabeledField(stringResource(R.string.console_calendar_rounds_label)) {
                                RoundsField(text = intimacyRoundsText, onTextChange = { intimacyRoundsText = it })
                            }

                            LabeledField(stringResource(R.string.console_calendar_orgasmed_label)) {
                                val unknownLabel = stringResource(R.string.console_calendar_initiated_by_unknown)
                                OrgasmedByPicker(
                                    selected = intimacyOrgasmedBy,
                                    selfId = selfPartner?.id,
                                    selfLabel = selfPartner?.firstName?.ifBlank { null } ?: unknownLabel,
                                    partnerId = partnerPartner?.id,
                                    partnerLabel = partnerPartner?.firstName?.ifBlank { null } ?: unknownLabel,
                                    onSelected = { intimacyOrgasmedBy = it },
                                )
                            }

                            LabeledField(stringResource(R.string.console_calendar_positions_label)) {
                                ChipMultiSelect(
                                    options = IntimacyPosition.entries.map { it.label },
                                    selected = intimacyPositions,
                                    onToggle = { label ->
                                        intimacyPositions = intimacyPositions.toggle(label)
                                    },
                                )
                            }

                            LabeledField(stringResource(R.string.console_calendar_locations_label)) {
                                ChipMultiSelect(
                                    options = IntimacyLocation.entries.map { it.label },
                                    selected = intimacyLocations,
                                    onToggle = { label ->
                                        if (!locationsLockedToCar) intimacyLocations = intimacyLocations.toggle(label)
                                    },
                                    enabled = !locationsLockedToCar,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startAtEpochMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { newDate ->
                        val updated = startAtEpochMillis?.withDate(newDate) ?: newDate
                        // Keep the range non-inverted — pushing start past a same/earlier end
                        // drags end along with it, same as most calendar apps.
                        if (endAtEpochMillis == null || updated > endAtEpochMillis!!) endAtEpochMillis = updated
                        startAtEpochMillis = updated
                    }
                    showStartDatePicker = false
                }) { Text(stringResource(R.string.console_pin_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text(stringResource(R.string.console_pin_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endAtEpochMillis ?: startAtEpochMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { newDate ->
                        endAtEpochMillis = (endAtEpochMillis ?: startAtEpochMillis)?.withDate(newDate) ?: newDate
                    }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.console_pin_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text(stringResource(R.string.console_pin_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }
    if (showStartTimePicker) {
        AppTimePickerDialog(
            initialEpochMillis = startAtEpochMillis,
            onDismissRequest = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                startAtEpochMillis = (startAtEpochMillis ?: System.currentTimeMillis()).withTime(hour, minute)
                showStartTimePicker = false
            },
        )
    }
    if (showEndTimePicker) {
        AppTimePickerDialog(
            initialEpochMillis = endAtEpochMillis,
            onDismissRequest = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                endAtEpochMillis = (endAtEpochMillis ?: startAtEpochMillis ?: System.currentTimeMillis()).withTime(hour, minute)
                showEndTimePicker = false
            },
        )
    }
}

/** Gradient banner with the selected type's icon front and center, so it visibly reacts as the
 * user changes type — was purely decorative ("standing in for the reference design's photo," per
 * this composable's own prior doc comment), now the cover photo's actual home. [coverPhotoModel]
 * is either a freshly-picked [android.net.Uri] or the existing cover's already-downloaded local
 * file path; when non-null it fills the banner (`ContentScale.Crop` — ordinary automatic fit, no
 * manual crop/clip step) and the type icon renders semi-transparent on top of it rather than
 * disappearing, doubling as [onIconClick]'s tap target for adding/replacing the photo. The empty
 * state is deliberately not visually hinted at as tappable — same plain icon-on-gradient look as
 * before this feature existed. */
@Composable
private fun EventHeroHeader(type: EventType, coverPhotoModel: Any?, onIconClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPhotoModel != null) {
            AsyncImage(
                model = coverPhotoModel,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Icon(
            imageVector = type.icon,
            contentDescription = stringResource(R.string.console_settings_change_photo),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (coverPhotoModel != null) 0.7f else 1f),
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onIconClick),
        )
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

/** Existing attachments (edit mode) plus newly-picked-this-session, not-yet-uploaded ones —
 * uploads happen at Save time, not at pick time (see `CalendarRepository.saveEvent`'s own doc
 * comment for why: avoids orphaning a server-side upload if the dialog is cancelled). Picking
 * existing files only, per the confirmed scope — no in-app camera/video-recording capture here,
 * unlike Messages' `AttachPanel`. */
@Composable
private fun EventAttachmentsField(
    existingAttachments: List<MessageAttachment>,
    newMediaUris: List<Uri>,
    newDocumentUris: List<Uri>,
    onRemoveExisting: (MessageAttachment) -> Unit,
    onRemoveNewMedia: (Uri) -> Unit,
    onRemoveNewDocument: (Uri) -> Unit,
    onMediaPicked: (List<Uri>) -> Unit,
    onDocumentsPicked: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) onMediaPicked(uris) }
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onDocumentsPicked(uris) }

    val existingMedia = existingAttachments.filter { it.kind != MessageAttachmentKind.DOCUMENT }
    val existingDocuments = existingAttachments.filter { it.kind == MessageAttachmentKind.DOCUMENT }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (existingMedia.isNotEmpty() || newMediaUris.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                existingMedia.forEach { attachment ->
                    PendingMediaThumbnail(
                        model = attachment.filePath,
                        isVideo = attachment.kind == MessageAttachmentKind.VIDEO,
                        onRemove = { onRemoveExisting(attachment) },
                    )
                }
                newMediaUris.forEach { uri ->
                    PendingMediaThumbnail(
                        model = uri,
                        isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true,
                        onRemove = { onRemoveNewMedia(uri) },
                    )
                }
            }
        }
        existingDocuments.forEach { attachment ->
            PendingDocumentChip(
                name = attachment.originalFilename ?: stringResource(R.string.console_home_document_generic_name),
                onRemove = { onRemoveExisting(attachment) },
            )
        }
        newDocumentUris.forEach { uri ->
            val name = remember(uri) { resolveDisplayName(context, uri) }
            PendingDocumentChip(
                name = name ?: stringResource(R.string.console_home_document_generic_name),
                onRemove = { onRemoveNewDocument(uri) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = {
                mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.console_calendar_attach_media_action))
            }
            TextButton(onClick = { documentPickerLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.console_calendar_attach_document_action))
            }
        }
    }
}

@Composable
private fun PendingMediaThumbnail(model: Any, isVideo: Boolean, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(72.dp)) {
        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isVideo) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.console_pin_cancel),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun PendingDocumentChip(name: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel), modifier = Modifier.size(16.dp))
        }
    }
}

/** [OpenableColumns.DISPLAY_NAME] is the SAF-standard way to ask a content provider for a
 * human-readable name — same approach `MediaStorage.resolveDisplayName` uses for the actual
 * upload path; this is purely for the picked-but-not-yet-uploaded preview label. */
private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index) else null
        } else {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(selected: EventType, onSelected: (EventType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            leadingIcon = { Icon(selected.icon, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = underlineFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EventType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = { Icon(option.icon, contentDescription = null) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceDropdown(selected: RecurrencePreset, onSelected: (RecurrencePreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = underlineFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecurrencePreset.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

/** A tappable "label / value" date field, plus a second tappable time field alongside it
 * whenever [isAllDay] is false — shared by the start/end rows. */
@Composable
private fun CalendarDateTimeField(
    dateLabel: String,
    timeLabel: String,
    valueEpochMillis: Long?,
    isAllDay: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CalendarValueField(
            label = dateLabel,
            icon = Icons.Rounded.Event,
            text = valueEpochMillis?.let { DateFormat.getMediumDateFormat(LocalContext.current).format(Date(it)) },
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
        )
        if (!isAllDay) {
            CalendarValueField(
                label = timeLabel,
                icon = Icons.Rounded.Schedule,
                text = valueEpochMillis?.let { DateFormat.getTimeFormat(LocalContext.current).format(Date(it)) },
                onClick = onTimeClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarValueField(
    label: String,
    icon: ImageVector,
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        ) {
            Text(
                text = text ?: stringResource(R.string.console_calendar_date_not_set),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// AppTimePickerDialog/Long.withDate/Long.withTime moved to
// console/ui/components/AppTimePickerDialog.kt — shared with the Messages composer's Schedule
// Send dialog, which needs the identical Material3 time-picker wrapper.

@Composable
private fun underlineFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
)

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

/** The reference design's "— OPTIONAL DETAILS —" hairline-divided header, marking where the
 * Intimacy Log section begins — also reused by [EventDetailScreen] for its Intimacy Log section
 * header. */
@Composable
fun SectionDivider(label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The "Cancelled" badge shown on an agenda card ([CalendarScreen]'s `AgendaItemRow`) and in
 * [EventDetailScreen]'s view mode for any Planned Date/Planned Drive/Planned Trip/Custom event
 * marked cancelled — the one place both screens source that chip's look from, so they can never
 * drift. */
@Composable
fun CancelledChip(modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(R.string.console_calendar_cancelled_chip)) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        border = null,
        modifier = modifier,
    )
}

/** 5 tappable flames, 1-5 — tapping the already-selected flame clears the rating back to
 * "not rated" rather than leaving it stuck, since there's no other way to unset it. */
@Composable
private fun RatingPicker(rating: Int?, onRatingChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (value in 1..5) {
            val selected = rating != null && value <= rating
            IconButton(onClick = { onRatingChange(if (rating == value) null else value) }) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = value.toString(),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/** 15/30/60-minute preset chips plus a free-entry minutes field — picking a preset clears the
 * custom text and vice versa, so exactly one of the two is ever the source of truth. */
@Composable
private fun DurationPicker(
    durationMinutes: Int?,
    customText: String,
    onPresetSelected: (Int) -> Unit,
    onCustomTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60).forEach { preset ->
            FilterChip(
                selected = customText.isEmpty() && durationMinutes == preset,
                onClick = { onPresetSelected(preset) },
                label = { Text(stringResource(R.string.console_calendar_duration_minutes_format, preset)) },
            )
        }
        TextField(
            value = customText,
            onValueChange = onCustomTextChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.console_calendar_duration_custom_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = underlineFieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Exactly 3 options, per the confirmed design: the two partners' real first names, plus
 * "Both" — never the reference design's generic "Myself/Partner/Each of us/Spontaneous". The
 * stored value is a stable partner id (or the "both" sentinel), not a self/partner-relative
 * label, so it reads identically correct from either partner's device later. */
@Composable
private fun InitiatedByPicker(
    selected: String?,
    selfId: String?,
    selfLabel: String,
    partnerId: String?,
    partnerLabel: String,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOfNotNull(
        selfId?.let { it to selfLabel },
        partnerId?.let { it to partnerLabel },
        INTIMACY_INITIATED_BY_BOTH to stringResource(R.string.console_calendar_initiated_by_both),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(if (selected == value) null else value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(text = label, maxLines = 1)
            }
        }
    }
}

/** Free-entry rounds count, 1-10 — digits only (same filtering approach as [DateTextField]/
 * `JumpToDateDialog`'s date fields), clamped to range at save time in [EventEditorDialog.save]. */
@Composable
private fun RoundsField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = text,
        onValueChange = { raw -> onTextChange(raw.filter { it.isDigit() }.take(2)) },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.console_calendar_rounds_placeholder)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = underlineFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Same shape as [InitiatedByPicker], plus an explicit "None" 4th option — a real answer (nobody
 * finished), distinct from the field simply not having been answered yet. */
@Composable
private fun OrgasmedByPicker(
    selected: String?,
    selfId: String?,
    selfLabel: String,
    partnerId: String?,
    partnerLabel: String,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOfNotNull(
        selfId?.let { it to selfLabel },
        partnerId?.let { it to partnerLabel },
        INTIMACY_INITIATED_BY_BOTH to stringResource(R.string.console_calendar_initiated_by_both),
        INTIMACY_ORGASMED_BY_NONE to stringResource(R.string.console_calendar_orgasmed_none),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(if (selected == value) null else value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(text = label, maxLines = 1)
            }
        }
    }
}

/** Yes/No, nullable — tapping the already-selected option clears it back to "not answered"
 * rather than forcing a choice, matching every other optional field in this section. */
@Composable
private fun ProtectionUsedPicker(protectionUsed: Boolean?, onChange: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = protectionUsed == true,
            onClick = { onChange(if (protectionUsed == true) null else true) },
            label = { Text(stringResource(R.string.console_calendar_yes)) },
        )
        FilterChip(
            selected = protectionUsed == false,
            onClick = { onChange(if (protectionUsed == false) null else false) },
            label = { Text(stringResource(R.string.console_calendar_no)) },
        )
    }
}

/** The fixed-vocabulary Positions/Locations pickers — no custom entries (confirmed). [enabled]
 * false renders every chip disabled, used for the Locations picker when locked to Car only. */
@Composable
private fun ChipMultiSelect(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                enabled = enabled,
                label = { Text(option) },
            )
        }
    }
}
