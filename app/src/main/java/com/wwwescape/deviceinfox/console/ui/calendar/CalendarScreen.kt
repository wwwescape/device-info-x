package com.wwwescape.deviceinfox.console.ui.calendar

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem
import com.wwwescape.deviceinfox.console.ui.components.SettingsGearIcon
import com.wwwescape.deviceinfox.console.ui.components.drawHatchedCircle
import com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabContentWindowInsets
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CalendarViewMode { MONTH, TIMELINE, STATS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onSettingsClick: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationActive.collectAsStateWithLifecycle()

    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewEventDialog by remember { mutableStateOf(false) }
    var viewingItem by remember { mutableStateOf<CalendarItem?>(null) }
    var editingItem by remember { mutableStateOf<CalendarItem?>(null) }
    var pendingDelete by remember { mutableStateOf<CalendarItem?>(null) }

    val selfPartner by viewModel.selfPartner.collectAsStateWithLifecycle()
    val partnerPartner by viewModel.partnerPartner.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()

    // Tapping a card opens the read-only detail screen first — EventEditorDialog only opens from
    // there (or fresh, via the FAB), never directly from the agenda anymore.
    val onItemClick: (CalendarItem) -> Unit = { item -> viewingItem = item }

    // The 3-dot menu's Edit option, unlike tapping the card, skips View entirely and opens the
    // editor directly — that's the whole point of it being a separate menu action.
    val onItemEdit: (CalendarItem) -> Unit = { item -> editingItem = item }

    // Recurring items (Birthday, Wedding Anniversary, Anniversary, Reminder, Custom) are one row
    // server-side with a recurrence rule — expanded here into one entry per occurrence within the
    // same ~2-years-back/5-years-forward window CalendarMonthView already pages within (and
    // CalendarRepository already syncs), so Month/Timeline/Search show every nearby occurrence
    // instead of only the single next one. Non-recurring items pass through unchanged.
    val expandedItems = remember(uiState.items) {
        val now = Calendar.getInstance()
        val minEpochMillis = (now.clone() as Calendar).apply { add(Calendar.MONTH, -MONTH_RANGE_PAST.toInt()) }.timeInMillis
        val maxEpochMillis = (now.clone() as Calendar).apply { add(Calendar.MONTH, MONTH_RANGE_FUTURE.toInt()) }.timeInMillis
        expandRecurringItems(uiState.items, minEpochMillis, maxEpochMillis)
    }
    val filteredItems = remember(expandedItems, searchQuery) {
        if (searchQuery.isBlank()) {
            expandedItems
        } else {
            expandedItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val context = LocalContext.current
    val partnerWipedMessage = stringResource(R.string.console_calendar_partner_wiped)
    val genericErrorMessage = stringResource(R.string.console_error_network)
    val saveSuccessMessage = stringResource(R.string.console_calendar_save_success)
    val deleteSuccessMessage = stringResource(R.string.console_calendar_delete_success)
    LaunchedEffect(Unit) {
        viewModel.calendarWipedEvent.collect {
            Toast.makeText(context, partnerWipedMessage, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.saveFailed.collect { detail -> Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleteFailed.collect { detail -> Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.saveSucceeded.collect {
            Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
            showNewEventDialog = false
            editingItem = null
        }
    }
    LaunchedEffect(Unit) {
        viewModel.deleteSucceeded.collect {
            Toast.makeText(context, deleteSuccessMessage, Toast.LENGTH_SHORT).show()
            pendingDelete = null
        }
    }
    val imageSavedMessage = stringResource(R.string.console_home_image_saved)
    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect { Toast.makeText(context, imageSavedMessage, Toast.LENGTH_SHORT).show() }
    }
    val videoSavedMessage = stringResource(R.string.console_home_video_saved)
    LaunchedEffect(Unit) {
        viewModel.videoSavedEvent.collect { Toast.makeText(context, videoSavedMessage, Toast.LENGTH_SHORT).show() }
    }
    val documentSavedMessage = stringResource(R.string.console_home_document_saved)
    LaunchedEffect(Unit) {
        viewModel.documentSavedEvent.collect { Toast.makeText(context, documentSavedMessage, Toast.LENGTH_SHORT).show() }
    }
    val vaultSavedMessage = stringResource(R.string.console_home_saved_to_vault_message)
    val vaultAlreadySavedMessage = stringResource(R.string.console_home_already_saved_to_vault_message)
    LaunchedEffect(Unit) {
        viewModel.vaultSaveEvent.collect { saved ->
            Toast.makeText(context, if (saved) vaultSavedMessage else vaultAlreadySavedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail -> Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ConsoleTabContentWindowInsets,
        topBar = {
            Column {
                if (isSearching) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.console_calendar_search_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.console_calendar_title)) },
                        actions = {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.console_calendar_search_action))
                            }
                            IconButton(onClick = onLock) {
                                Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.console_home_lock_action))
                            }
                            IconButton(onClick = onSettingsClick) {
                                SettingsGearIcon(isLiveLocationActive = isLiveLocationActive, isUpdateAvailable = isUpdateAvailable)
                            }
                        },
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        SegmentedButton(
                            selected = viewMode == CalendarViewMode.MONTH,
                            onClick = { viewMode = CalendarViewMode.MONTH },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        ) {
                            Text(stringResource(R.string.console_calendar_view_month))
                        }
                        SegmentedButton(
                            selected = viewMode == CalendarViewMode.TIMELINE,
                            onClick = { viewMode = CalendarViewMode.TIMELINE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        ) {
                            Text(stringResource(R.string.console_calendar_view_timeline))
                        }
                        SegmentedButton(
                            selected = viewMode == CalendarViewMode.STATS,
                            onClick = { viewMode = CalendarViewMode.STATS },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        ) {
                            Text(stringResource(R.string.console_calendar_view_stats))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Search always shows the Timeline view underneath regardless of viewMode, so it's
            // grouped in with Timeline/Stats here too — "add" only makes sense while looking at
            // the Month grid, where tapping a day is the other way to create an event.
            if (!isSearching && viewMode == CalendarViewMode.MONTH) {
                FloatingActionButton(onClick = { showNewEventDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.console_calendar_add_action))
                }
            }
        },
    ) { innerPadding ->
        if (isSearching) {
            CalendarTimelineView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                items = filteredItems,
                onItemClick = onItemClick,
                onItemEdit = onItemEdit,
                onItemDelete = { pendingDelete = it },
            )
        } else {
            when (viewMode) {
                CalendarViewMode.MONTH -> CalendarMonthView(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    items = expandedItems,
                    onItemClick = onItemClick,
                    onItemEdit = onItemEdit,
                    onItemDelete = { pendingDelete = it },
                )
                CalendarViewMode.TIMELINE -> CalendarTimelineView(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    items = expandedItems,
                    onItemClick = onItemClick,
                    onItemEdit = onItemEdit,
                    onItemDelete = { pendingDelete = it },
                )
                CalendarViewMode.STATS -> CalendarStatsView(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    items = uiState.items,
                )
            }
        }
    }

    if (showNewEventDialog) {
        EventEditorDialog(
            existing = null,
            selfPartner = selfPartner,
            partnerPartner = partnerPartner,
            onSave = { id, title, type, notes, startAt, endAt, isAllDay, location, recurrenceRule, cancelled, cancellationReason,
                reminderMinutesBefore, intimacy, newMediaUris, newDocumentUris, keptAttachmentMediaIds,
                newCoverPhotoUri, keptCoverMediaId ->
                viewModel.saveEvent(
                    id, title, type, notes, startAt, endAt, isAllDay, location, recurrenceRule, cancelled, cancellationReason,
                    reminderMinutesBefore, intimacy, newMediaUris, newDocumentUris, keptAttachmentMediaIds,
                    newCoverPhotoUri, keptCoverMediaId,
                )
            },
            isSaving = isSaving,
            onDismiss = { showNewEventDialog = false },
        )
    }
    editingItem?.let { existing ->
        EventEditorDialog(
            existing = existing,
            selfPartner = selfPartner,
            partnerPartner = partnerPartner,
            onSave = { id, title, type, notes, startAt, endAt, isAllDay, location, recurrenceRule, cancelled, cancellationReason,
                reminderMinutesBefore, intimacy, newMediaUris, newDocumentUris, keptAttachmentMediaIds,
                newCoverPhotoUri, keptCoverMediaId ->
                viewModel.saveEvent(
                    id, title, type, notes, startAt, endAt, isAllDay, location, recurrenceRule, cancelled, cancellationReason,
                    reminderMinutesBefore, intimacy, newMediaUris, newDocumentUris, keptAttachmentMediaIds,
                    newCoverPhotoUri, keptCoverMediaId,
                )
            },
            isSaving = isSaving,
            onDismiss = { editingItem = null },
        )
    }
    viewingItem?.let { item ->
        EventDetailScreen(
            item = item,
            selfPartner = selfPartner,
            partnerPartner = partnerPartner,
            onEdit = {
                viewingItem = null
                editingItem = item
            },
            onBack = { viewingItem = null },
            ensureVideoDownloaded = { attachment -> viewModel.ensureEventVideoDownloaded(item.id, attachment) },
            onSaveImageAttachment = { attachment -> viewModel.saveEventImageAttachment(attachment) },
            onSaveVideoAttachment = { attachment -> viewModel.saveEventVideoAttachment(attachment) },
            onSaveDocumentAttachment = { attachment -> viewModel.saveEventDocumentAttachment(item.id, attachment) },
            onSaveImageAttachmentToVault = { attachment -> viewModel.saveEventImageAttachmentToVault(item.id, attachment) },
            onSaveVideoAttachmentToVault = { attachment -> viewModel.saveEventVideoAttachmentToVault(item.id, attachment) },
            onSaveDocumentAttachmentToVault = { attachment -> viewModel.saveEventDocumentAttachmentToVault(item.id, attachment) },
        )
    }

    pendingDelete?.let { item ->
        DeleteEventConfirmationDialog(
            isPending = isDeleting,
            onConfirm = { viewModel.deleteEvent(item.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The screen's original flat agenda list — unchanged behavior, just extracted so
 * [CalendarScreen] can offer it as one of two view modes instead of the only one. Only shows
 * months that actually have items (unlike [CalendarMonthView], which shows every month in
 * range). */
@Composable
private fun CalendarTimelineView(
    items: List<CalendarItem>,
    onItemClick: (CalendarItem) -> Unit,
    onItemEdit: (CalendarItem) -> Unit,
    onItemDelete: (CalendarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val rows = remember(items) { buildAgendaRows(items) }
    LaunchedEffect(rows) {
        val firstUpcomingIndex = rows.indexOfFirst { it is AgendaRow.Item && !isPast(it.item.displayAtEpochMillis) }
        if (firstUpcomingIndex > 0) listState.scrollToItem(firstUpcomingIndex)
    }

    if (items.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.console_calendar_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                rows,
                // A recurring item's id now repeats once per expanded occurrence — qualify with
                // its (occurrence-specific) displayAtEpochMillis so each row still gets a unique key.
                key = { row ->
                    when (row) {
                        is AgendaRow.MonthHeader -> row.label
                        is AgendaRow.Item -> "${row.item.id}_${row.item.displayAtEpochMillis}"
                    }
                },
            ) { row ->
                when (row) {
                    is AgendaRow.MonthHeader -> Text(
                        text = row.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                    is AgendaRow.Item -> AgendaItemRow(
                        item = row.item,
                        onClick = { onItemClick(row.item) },
                        onEdit = { onItemEdit(row.item) },
                        onDelete = { onItemDelete(row.item) },
                    )
                }
            }
        }
    }
}

/** Roughly matches [com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository]'s own
 * sync window (`refresh()`: ~10 years back, ~10 years forward from now) — the grid can't usefully
 * scroll further than the data actually covers. */
private const val MONTH_RANGE_PAST = 120L
private const val MONTH_RANGE_FUTURE = 120L

/** One month grid at a time, paged with the chevrons in [MonthCalendarCard]'s header, plus a
 * single agenda panel below it that always reflects [selectedDate] — independent of which month
 * page is currently showing, so paging away from the selected day's month doesn't clear it. */
@Composable
private fun CalendarMonthView(
    items: List<CalendarItem>,
    onItemClick: (CalendarItem) -> Unit,
    onItemEdit: (CalendarItem) -> Unit,
    onItemDelete: (CalendarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val currentYearMonth = remember(today) { YearMonth.from(today) }
    val minYearMonth = remember(currentYearMonth) { currentYearMonth.minusMonths(MONTH_RANGE_PAST) }
    val maxYearMonth = remember(currentYearMonth) { currentYearMonth.plusMonths(MONTH_RANGE_FUTURE) }
    val itemsByDate = remember(items, zoneId) { buildItemsByDate(items, zoneId) }

    var displayedYearMonth by remember { mutableStateOf(currentYearMonth) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(today) }
    var showJumpToDateDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        MonthCalendarCard(
            yearMonth = displayedYearMonth,
            today = today,
            locale = locale,
            itemsByDate = itemsByDate,
            selectedDate = selectedDate,
            canGoToPreviousMonth = displayedYearMonth > minYearMonth,
            canGoToNextMonth = displayedYearMonth < maxYearMonth,
            // Paging away clears the selection — otherwise DayAgendaSection keeps showing a day
            // that's no longer even visible in the grid above it, which reads as stale/wrong.
            onPreviousMonth = { displayedYearMonth = displayedYearMonth.minusMonths(1); selectedDate = null },
            onNextMonth = { displayedYearMonth = displayedYearMonth.plusMonths(1); selectedDate = null },
            onDayClick = { date -> selectedDate = if (selectedDate == date) null else date },
            // Today jumps straight back to today's own month/day — same "land on the day, not
            // just the month" spirit as the Jump to date dialog below.
            onTodayClick = { displayedYearMonth = currentYearMonth; selectedDate = today },
            onJumpToDateClick = { showJumpToDateDialog = true },
            modifier = Modifier.padding(12.dp),
        )
        DayAgendaSection(
            selectedDate = selectedDate,
            locale = locale,
            dayItems = selectedDate?.let { itemsByDate[it] }?.distinctBy { it.id }.orEmpty(),
            onItemClick = onItemClick,
            onItemEdit = onItemEdit,
            onItemDelete = onItemDelete,
            modifier = Modifier.weight(1f),
        )
    }

    if (showJumpToDateDialog) {
        JumpToDateDialog(
            minYearMonth = minYearMonth,
            maxYearMonth = maxYearMonth,
            onConfirm = { date -> displayedYearMonth = YearMonth.from(date); selectedDate = date },
            onDismiss = { showJumpToDateDialog = false },
        )
    }
}

@Composable
private fun MonthCalendarCard(
    yearMonth: YearMonth,
    today: LocalDate,
    locale: Locale,
    itemsByDate: Map<LocalDate, List<CalendarItem>>,
    selectedDate: LocalDate?,
    canGoToPreviousMonth: Boolean,
    canGoToNextMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onTodayClick: () -> Unit,
    onJumpToDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthLabel = remember(yearMonth, locale) {
        yearMonth.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } + " " + yearMonth.year
    }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekdayLabels = remember(firstDayOfWeek, locale) {
        (0..6).map { offset -> firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale) }
    }
    val firstOfMonth = remember(yearMonth) { yearMonth.atDay(1) }
    val daysInMonth = yearMonth.lengthOfMonth()
    val leadingBlanks = remember(firstOfMonth, firstDayOfWeek) {
        (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    }
    val weekCount = (leadingBlanks + daysInMonth + 6) / 7

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onTodayClick) {
                    Icon(Icons.Rounded.Today, contentDescription = stringResource(R.string.console_calendar_today_action))
                }
                IconButton(onClick = onJumpToDateClick) {
                    Icon(Icons.Rounded.EditCalendar, contentDescription = stringResource(R.string.console_calendar_jump_to_date_action))
                }
                IconButton(onClick = onPreviousMonth, enabled = canGoToPreviousMonth) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.console_calendar_previous_month))
                }
                IconButton(onClick = onNextMonth, enabled = canGoToNextMonth) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.console_calendar_next_month))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            for (week in 0 until weekCount) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val dayNumber = week * 7 + column - leadingBlanks + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayNumber)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                dayItems = itemsByDate[date].orEmpty(),
                                onClick = { onDayClick(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(44.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The panel below [MonthCalendarCard] — mirrors the reference design's "Wednesday, 18 · 3
 * events" header followed by that day's event cards, scrolling independently of the (fixed)
 * calendar grid above it. */
@Composable
private fun DayAgendaSection(
    selectedDate: LocalDate?,
    locale: Locale,
    dayItems: List<CalendarItem>,
    onItemClick: (CalendarItem) -> Unit,
    onItemEdit: (CalendarItem) -> Unit,
    onItemDelete: (CalendarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedDate == null) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.console_calendar_select_day_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val dayLabel = remember(selectedDate, locale) {
        DateTimeFormatter.ofPattern("EEEE, d", locale).format(selectedDate)
    }
    val eventCountLabel = when (dayItems.size) {
        0 -> stringResource(R.string.console_calendar_month_no_events)
        1 -> stringResource(R.string.console_calendar_month_one_event)
        else -> stringResource(R.string.console_calendar_month_event_count, dayItems.size)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = dayLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(text = eventCountLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dayItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.console_calendar_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(dayItems, key = { it.id }) { item ->
                    AgendaItemRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        onEdit = { onItemEdit(item) },
                        onDelete = { onItemDelete(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    dayItems: List<CalendarItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Today always renders as a hatch-filled circle rather than a solid one — colored to match
    // whatever solid fill the day would otherwise show (Selected's primaryContainer, or a neutral
    // primary when nothing else applies) — so "today" never disappears behind another state's
    // solid fill the way the old border-only marker used to when a day was also selected.
    val hatchColor = if (isToday) {
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    } else {
        null
    }
    val backgroundModifier = if (isSelected && !isToday) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .height(44.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .then(backgroundModifier)
            .drawBehind { hatchColor?.let { drawHatchedCircle(it) } }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (dayItems.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                dayItems.distinctBy { it.id }.take(3).forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(dotColorFor(item)),
                    )
                }
            }
        }
    }
}

@Composable
private fun dotColorFor(item: CalendarItem): androidx.compose.ui.graphics.Color =
    if (item.isRecurring) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

/** The span (start, end?) to actually display for an item — [CalendarItem.displayAtEpochMillis]
 * is already shifted to the next occurrence for a recurring entry, so a ranged recurring item
 * (e.g. a yearly recurring CUSTOM entry spanning a week) keeps the same span length, just moved to that next
 * occurrence rather than showing its original historical dates. */
private fun CalendarItem.displayRange(): Pair<Long, Long?> {
    val end = endAtEpochMillis ?: return displayAtEpochMillis to null
    val spanMillis = end - startAtEpochMillis
    return displayAtEpochMillis to displayAtEpochMillis + spanMillis
}

/** How many occurrences [occurrencesWithin] will ever generate for one recurring item — mainly a
 * guard for `FREQ=DAILY`/`FREQ=WEEKLY` reminders, which could otherwise flood the Timeline with
 * thousands of rows across the multi-year window; yearly items like a birthday never come close. */
private const val MAX_OCCURRENCES_PER_ITEM = 100

/** Steps a recurring item forward one occurrence at a time (same FREQ→[Calendar] field mapping
 * [com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository]'s `computeNextOccurrence`
 * uses server-side), but continues across the whole [minEpochMillis, maxEpochMillis] window
 * instead of stopping at the first occurrence at/after now — so Month/Timeline/Search can finally
 * show a recurring event on every occurrence in view, not just the single upcoming one. A
 * non-recurring item (or a rule this app doesn't know how to step, though every rule
 * [EventEditorDialog] can produce is one of these four) passes through unchanged. */
private fun CalendarItem.occurrencesWithin(minEpochMillis: Long, maxEpochMillis: Long): List<CalendarItem> {
    val field = when (recurrenceRule) {
        "FREQ=DAILY" -> Calendar.DAY_OF_YEAR
        "FREQ=WEEKLY" -> Calendar.WEEK_OF_YEAR
        "FREQ=MONTHLY" -> Calendar.MONTH
        "FREQ=YEARLY" -> Calendar.YEAR
        else -> return listOf(this)
    }
    val cursor = Calendar.getInstance().apply { timeInMillis = startAtEpochMillis }
    val occurrences = mutableListOf<CalendarItem>()
    while (cursor.timeInMillis <= maxEpochMillis && occurrences.size < MAX_OCCURRENCES_PER_ITEM) {
        if (cursor.timeInMillis >= minEpochMillis) {
            occurrences += copy(displayAtEpochMillis = cursor.timeInMillis)
        }
        cursor.add(field, 1)
    }
    return occurrences
}

/** Expands every recurring item in [items] into one entry per occurrence within
 * [minEpochMillis, maxEpochMillis], then re-sorts by [CalendarItem.displayAtEpochMillis] —
 * [buildAgendaRows] assumes ascending order to detect month-header transitions, which a plain
 * flatMap across multiple items' own occurrence lists wouldn't preserve on its own. */
private fun expandRecurringItems(items: List<CalendarItem>, minEpochMillis: Long, maxEpochMillis: Long): List<CalendarItem> =
    items.flatMap { it.occurrencesWithin(minEpochMillis, maxEpochMillis) }.sortedBy { it.displayAtEpochMillis }

/** Maps every [CalendarItem] onto every date it touches — a multi-day item plots on each day in
 * its (possibly recurrence-shifted) span; everything else is a single point in time. */
private fun buildItemsByDate(items: List<CalendarItem>, zoneId: ZoneId): Map<LocalDate, List<CalendarItem>> {
    val map = mutableMapOf<LocalDate, MutableList<CalendarItem>>()
    items.forEach { item ->
        val (start, end) = item.displayRange()
        val startDate = start.toLocalDate(zoneId)
        val endDate = (end ?: start).toLocalDate(zoneId)
        // Capped defensively — nothing validates a max span on the way in, and an unbounded
        // generateSequence here would be a self-inflicted ANR.
        val dates = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.take(370).toList()
        dates.forEach { date -> map.getOrPut(date) { mutableListOf() }.add(item) }
    }
    return map
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

@Composable
private fun AgendaItemRow(item: CalendarItem, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Deliberately layered *on top of* the Card's own opaque surfaceContainer background,
            // not instead of it, at a low fixed alpha — that low alpha is the legibility guarantee
            // itself (the vast majority of what's behind the text stays the theme's own safe
            // surface color regardless of the photo's own brightness/color), rather than needing a
            // separate scrim tuned per-photo.
            if (item.coverPhotoFilePath != null) {
                AsyncImage(
                    model = item.coverPhotoFilePath,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.12f,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Icon(
                    imageVector = item.type.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.cancelled) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    Text(
                        text = subtitleFor(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (item.cancelled) TextDecoration.LineThrough else TextDecoration.None,
                    )
                }
                if (item.cancelled) {
                    CancelledChip(modifier = Modifier.padding(end = 4.dp))
                }
                if (item.isRecurring) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.console_calendar_recurs_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.console_calendar_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.console_calendar_edit_action)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.console_calendar_delete_action)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun subtitleFor(item: CalendarItem): String {
    val (start, end) = item.displayRange()
    return if (end != null) {
        val context = LocalContext.current
        "${DateFormat.getMediumDateFormat(context).format(Date(start))} – " +
            DateFormat.getMediumDateFormat(context).format(Date(end))
    } else {
        relativeOrAbsoluteDateLabel(start)
    }
}

/** "Yesterday"/"Today"/"Tomorrow" for the immediate neighbors of today, then progressively
 * coarser units the further out an upcoming date is — "In 400 days" reads as a wall of digits in
 * a way "In 1 year" doesn't, so each bucket rounds down to the coarsest unit that's still under
 * one of the next unit. Anything before yesterday falls through to a plain absolute date, same as
 * before this bucketing existed — reminders (recomputed to always be upcoming, see
 * [com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository]) never hit that fallback;
 * past planned dates do. */
@Composable
private fun relativeOrAbsoluteDateLabel(epochMillis: Long): String {
    val diffDays = daysBetween(startOfDay(System.currentTimeMillis()), startOfDay(epochMillis))
    return when {
        diffDays == -1 -> stringResource(R.string.console_calendar_yesterday_label)
        diffDays == 0 -> stringResource(R.string.console_calendar_today_label)
        diffDays == 1 -> stringResource(R.string.console_calendar_tomorrow_label)
        diffDays in 2..6 -> stringResource(R.string.console_calendar_in_days_label, diffDays)
        diffDays in 7..29 -> {
            val weeks = diffDays / 7
            if (weeks == 1) stringResource(R.string.console_calendar_in_week_label) else stringResource(R.string.console_calendar_in_weeks_label, weeks)
        }
        diffDays in 30..364 -> {
            val months = diffDays / 30
            if (months == 1) stringResource(R.string.console_calendar_in_month_label) else stringResource(R.string.console_calendar_in_months_label, months)
        }
        diffDays >= 365 -> {
            val years = diffDays / 365
            if (years == 1) stringResource(R.string.console_calendar_in_year_label) else stringResource(R.string.console_calendar_in_years_label, years)
        }
        else -> DateFormat.getMediumDateFormat(LocalContext.current).format(Date(epochMillis))
    }
}

private fun isPast(epochMillis: Long): Boolean = startOfDay(epochMillis) < startOfDay(System.currentTimeMillis())

private fun startOfDay(epochMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = epochMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun daysBetween(fromEpochMillis: Long, toEpochMillis: Long): Int =
    ((toEpochMillis - fromEpochMillis) / (24 * 60 * 60 * 1000L)).toInt()

private sealed interface AgendaRow {
    data class MonthHeader(val label: String) : AgendaRow
    data class Item(val item: CalendarItem) : AgendaRow
}

private fun buildAgendaRows(items: List<CalendarItem>): List<AgendaRow> {
    val rows = mutableListOf<AgendaRow>()
    var lastMonthKey: String? = null
    items.forEach { item ->
        val monthKey = DateFormat.format("MMMM yyyy", Date(item.displayAtEpochMillis)).toString()
        if (monthKey != lastMonthKey) {
            rows += AgendaRow.MonthHeader(monthKey)
            lastMonthKey = monthKey
        }
        rows += AgendaRow.Item(item)
    }
    return rows
}
