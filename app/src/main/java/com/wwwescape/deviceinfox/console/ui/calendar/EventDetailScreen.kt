package com.wwwescape.deviceinfox.console.ui.calendar

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem
import com.wwwescape.deviceinfox.console.data.db.INTIMACY_INITIATED_BY_BOTH
import com.wwwescape.deviceinfox.console.data.db.INTIMACY_ORGASMED_BY_NONE
import com.wwwescape.deviceinfox.console.data.db.PartnerEntity
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.ui.components.DocumentAttachmentRow
import com.wwwescape.deviceinfox.console.ui.components.ImagePreviewDialog
import com.wwwescape.deviceinfox.console.ui.components.VideoPreviewDialog
import java.util.Date

/** Read-only "view mode" for a single agenda entry — tapping a card opens this instead of
 * jumping straight into [EventEditorDialog]; the Edit button up top is what opens that. Shows
 * every core field, plus the Intimacy Log section (reference screenshot's 2×2 grid + chip lists)
 * when [CalendarItem.hasIntimacyLogged] holds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    item: CalendarItem,
    selfPartner: PartnerEntity?,
    partnerPartner: PartnerEntity?,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    ensureVideoDownloaded: suspend (MessageAttachment) -> String?,
    onSaveImageAttachment: (MessageAttachment) -> Unit,
    onSaveVideoAttachment: (MessageAttachment) -> Unit,
    onSaveDocumentAttachment: (MessageAttachment) -> Unit,
    onSaveImageAttachmentToVault: (MessageAttachment) -> Unit,
    onSaveVideoAttachmentToVault: (MessageAttachment) -> Unit,
    onSaveDocumentAttachmentToVault: (MessageAttachment) -> Unit,
) {
    var previewAttachment by remember(item) { mutableStateOf<MessageAttachment?>(null) }

    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.console_calendar_event_detail_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.console_pin_cancel),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.console_calendar_edit_action))
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
                ) {
                    DetailHeroHeader(item = item, coverPhotoModel = item.coverPhotoFilePath)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                textDecoration = if (item.cancelled) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (item.cancelled) CancelledChip()
                        }

                        DetailRow(
                            label = stringResource(R.string.console_calendar_date_label),
                            value = formatDateRange(item, LocalContext.current),
                        )
                        item.cancellationReason?.let {
                            DetailRow(label = stringResource(R.string.console_calendar_cancellation_reason_label), value = it)
                        }
                        item.location?.let {
                            DetailRow(label = stringResource(R.string.console_calendar_location_label), value = it)
                        }
                        if (item.isRecurring) {
                            DetailRow(
                                label = stringResource(R.string.console_calendar_frequency_label),
                                value = stringResource(item.recurrenceRule.toRecurrencePreset().labelRes),
                            )
                        }
                        if (item.hasNotification) {
                            DetailRow(
                                label = stringResource(R.string.console_calendar_notification_label),
                                value = stringResource(R.string.console_calendar_notification_description),
                            )
                        }
                        item.notes?.let {
                            DetailRow(label = stringResource(R.string.console_calendar_description_label), value = it)
                        }

                        if (item.attachments.isNotEmpty()) {
                            SectionDivider(stringResource(R.string.console_calendar_attachments_section_header))
                            val mediaAttachments = item.attachments.filter { it.kind != MessageAttachmentKind.DOCUMENT }
                            val documentAttachments = item.attachments.filter { it.kind == MessageAttachmentKind.DOCUMENT }
                            if (mediaAttachments.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    mediaAttachments.forEach { attachment ->
                                        DetailMediaThumbnail(attachment = attachment, onClick = { previewAttachment = attachment })
                                    }
                                }
                            }
                            documentAttachments.forEach { attachment ->
                                DocumentAttachmentRow(
                                    attachment = attachment,
                                    onSave = onSaveDocumentAttachment,
                                    onSaveToVault = onSaveDocumentAttachmentToVault,
                                )
                            }
                        }

                        if (item.hasIntimacyLogged) {
                            SectionDivider(stringResource(R.string.console_calendar_intimacy_section_header))
                            IntimacyDetailGrid(item = item, selfPartner = selfPartner, partnerPartner = partnerPartner)
                            if (item.intimacyPositions.isNotEmpty()) {
                                DetailChipRow(
                                    label = stringResource(R.string.console_calendar_positions_label),
                                    values = item.intimacyPositions,
                                )
                            }
                            if (item.intimacyLocations.isNotEmpty()) {
                                DetailChipRow(
                                    label = stringResource(R.string.console_calendar_locations_label),
                                    values = item.intimacyLocations,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewAttachment?.let { attachment ->
        if (attachment.kind == MessageAttachmentKind.VIDEO) {
            VideoPreviewDialog(
                parentId = item.id,
                attachment = attachment,
                onDismiss = { previewAttachment = null },
                onDownload = { onSaveVideoAttachment(attachment) },
                ensureDownloaded = { ensureVideoDownloaded(attachment) },
                onSaveToVault = { onSaveVideoAttachmentToVault(attachment) },
            )
        } else {
            ImagePreviewDialog(
                attachment = attachment,
                onDismiss = { previewAttachment = null },
                onDownload = { onSaveImageAttachment(attachment) },
                onSaveToVault = { onSaveImageAttachmentToVault(attachment) },
            )
        }
    }
}

@Composable
private fun DetailMediaThumbnail(attachment: MessageAttachment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = attachment.filePath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (attachment.kind == MessageAttachmentKind.VIDEO) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
    }
}

/** Read-only counterpart to [EventEditorDialog]'s `EventHeroHeader` — same cover-photo-behind-a
 * semi-transparent-icon treatment, but [coverPhotoModel] never changes on tap here (View mode has
 * no edit affordance outside the dedicated Edit button in the top bar, per the cover-photo TODO
 * writeup's own "does nothing in View mode" decision). */
@Composable
private fun DetailHeroHeader(item: CalendarItem, coverPhotoModel: Any?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = item.type.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (coverPhotoModel != null) 0.7f else 1f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(item.displayLabelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailChipRow(label: String, values: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> AssistChip(onClick = {}, enabled = false, label = { Text(value) }) }
        }
    }
}

private data class IntimacyTileSpec(val icon: ImageVector, val label: String, val value: String)

/** [RatingPicker][com.wwwescape.deviceinfox.console.ui.calendar.EventEditorDialog] only ever
 * writes 1-5, so this is a closed, exhaustive mapping rather than a lookup with a fallback. */
private fun ratingTermRes(rating: Int): Int = when (rating) {
    1 -> R.string.console_calendar_rating_term_1
    2 -> R.string.console_calendar_rating_term_2
    3 -> R.string.console_calendar_rating_term_3
    4 -> R.string.console_calendar_rating_term_4
    else -> R.string.console_calendar_rating_term_5
}

/** The reference design's fixed-row tile grid — Rating alone, then Mood/Initiated By,
 * Protection/Duration, and Rounds/Orgasmed each paired side by side (the same weighted-`Row`
 * pairing `StatTile` uses elsewhere, e.g. `CpuScreen.kt`), rather than left to an auto-wrapping
 * `FlowRow` whose pairing would depend on font scaling/text length. Only the fields that were
 * actually set render a tile, so a partially-filled log doesn't show empty placeholders — a row
 * with just one side set renders that one tile alone. */
@Composable
private fun IntimacyDetailGrid(
    item: CalendarItem,
    selfPartner: PartnerEntity?,
    partnerPartner: PartnerEntity?,
    modifier: Modifier = Modifier,
) {
    val initiatedByLabel = when (item.intimacyInitiatedByPartnerId) {
        null -> null
        INTIMACY_INITIATED_BY_BOTH -> stringResource(R.string.console_calendar_initiated_by_both)
        selfPartner?.id -> selfPartner.firstName
        partnerPartner?.id -> partnerPartner.firstName
        else -> null
    }
    val orgasmedByLabel = when (item.intimacyOrgasmedByPartnerId) {
        null -> null
        INTIMACY_ORGASMED_BY_NONE -> stringResource(R.string.console_calendar_orgasmed_none)
        INTIMACY_INITIATED_BY_BOTH -> stringResource(R.string.console_calendar_initiated_by_both)
        selfPartner?.id -> selfPartner.firstName
        partnerPartner?.id -> partnerPartner.firstName
        else -> null
    }
    val protectionLabel = when (item.intimacyProtectionUsed) {
        true -> stringResource(R.string.console_calendar_yes)
        false -> stringResource(R.string.console_calendar_no)
        null -> null
    }
    val moodLabel = item.intimacyMoods.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val ratingTile = item.intimacyRating?.let {
        IntimacyTileSpec(
            Icons.Rounded.LocalFireDepartment,
            stringResource(R.string.console_calendar_rating_label),
            stringResource(ratingTermRes(it)),
        )
    }
    val moodTile = moodLabel?.let {
        IntimacyTileSpec(Icons.Rounded.Favorite, stringResource(R.string.console_calendar_mood_label), it)
    }
    val initiatedByTile = initiatedByLabel?.let {
        IntimacyTileSpec(Icons.Rounded.Bolt, stringResource(R.string.console_calendar_initiated_by_label), it)
    }
    val protectionTile = protectionLabel?.let {
        IntimacyTileSpec(Icons.Rounded.Shield, stringResource(R.string.console_calendar_protection_used_label), it)
    }
    val durationTile = item.intimacyDurationMinutes?.let {
        IntimacyTileSpec(
            Icons.Rounded.Timer,
            stringResource(R.string.console_calendar_duration_label),
            stringResource(R.string.console_calendar_duration_minutes_format, it),
        )
    }
    val roundsTile = item.intimacyRounds?.let {
        IntimacyTileSpec(Icons.Rounded.Loop, stringResource(R.string.console_calendar_rounds_label), it.toString())
    }
    val orgasmedTile = orgasmedByLabel?.let {
        IntimacyTileSpec(Icons.Rounded.WaterDrop, stringResource(R.string.console_calendar_orgasmed_label), it)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntimacyStatRow(ratingTile, null)
        IntimacyStatRow(moodTile, initiatedByTile)
        IntimacyStatRow(protectionTile, durationTile)
        IntimacyStatRow(roundsTile, orgasmedTile)
    }
}

@Composable
private fun IntimacyStatRow(left: IntimacyTileSpec?, right: IntimacyTileSpec?, modifier: Modifier = Modifier) {
    if (left == null && right == null) return
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left?.let { IntimacyStatCard(it.icon, it.label, it.value, modifier = Modifier.weight(1f)) }
        right?.let { IntimacyStatCard(it.icon, it.label, it.value, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun IntimacyStatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatDateRange(item: CalendarItem, context: Context): String {
    fun format(epochMillis: Long): String = if (item.isAllDay) {
        DateFormat.getMediumDateFormat(context).format(Date(epochMillis))
    } else {
        "${DateFormat.getMediumDateFormat(context).format(Date(epochMillis))} ${DateFormat.getTimeFormat(context).format(Date(epochMillis))}"
    }
    val end = item.endAtEpochMillis
    return if (end != null && end != item.startAtEpochMillis) {
        "${format(item.startAtEpochMillis)} – ${format(end)}"
    } else {
        format(item.startAtEpochMillis)
    }
}
