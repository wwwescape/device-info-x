package com.wwwescape.deviceinfox.console.ui.settings

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R

/** [title] is null when a category has exactly one card whose content already IS the category
 * (e.g. Danger Zone) — that sub-page's own [androidx.compose.material3.TopAppBar] title already
 * says the same thing, so repeating it as this card's own caption would be pure duplication.
 * Categories with several differently-named cards (Security/Pairing/Server all under General)
 * still pass a [title], since there the caption carries real information the page title doesn't. */
@Composable
fun SettingsSectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Used for pairing status ([com.wwwescape.deviceinfox.console.data.pairing.PairingRepository])
 * and the server address row — both [Text]s get equal `weight(1f)` rather than sizing to their
 * own content: without it, a long value (the server row's URL, in particular) sized itself at
 * full natural width, wrapped, and visually overlapped the label instead of the label reserving
 * its own space — splitting the row in half up front means each side wraps independently within
 * its own half. */
@Composable
fun StatusRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** [ServerStatus.CHECKING] renders the same dot as [ServerStatus.OFFLINE] (dim/neutral, not red)
 * — a fresh page load reads as "not yet known," not "there's a problem," which red would imply
 * before the first poll has even had a chance to resolve. */
@Composable
fun ServerStatusRow(label: String, status: ServerStatus, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (dotColor, labelRes) = when (status) {
                ServerStatus.ONLINE -> ServerOnlineColor to R.string.console_settings_server_status_online
                ServerStatus.OFFLINE -> MaterialTheme.colorScheme.error to R.string.console_settings_server_status_offline
                ServerStatus.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant to R.string.console_settings_server_status_checking
            }
            if (status != ServerStatus.CHECKING) PulsatingDot(color = dotColor)
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** No fixed "success" role in Material3's default color scheme (only `error`), so online gets
 * its own literal — same "small fixed literal for one specific meaning" choice
 * [com.wwwescape.deviceinfox.console.ui.calendar.CalendarStatsView]'s `IntimacyChartColor` makes. */
val ServerOnlineColor = Color(0xFF34C759)

/** A single dot that scales in and out forever — same [rememberInfiniteTransition]-driven pulse
 * shape as [com.wwwescape.deviceinfox.console.ui.home.HomeScreen]'s typing-indicator dots, just
 * one dot instead of three staggered ones. */
@Composable
fun PulsatingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "serverStatusDot")
    val scale by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "serverStatusDotScale",
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(color, CircleShape),
    )
}

/** Title/body copy for [DataDeletionConfirmationDialog] when confirming a single section's
 * delete — kept next to [DataSection] rather than baked into the dialog itself since "Delete all
 * data" (in [DangerZoneSettingsScreen]) needs its own distinct copy that isn't per-section. */
@Composable
fun DataSection.confirmationCopy(): Pair<String, String> = when (this) {
    DataSection.MESSAGES -> stringResource(R.string.console_settings_delete_messages_confirm_title) to
        stringResource(R.string.console_settings_delete_messages_confirm_body)
    DataSection.CALENDAR -> stringResource(R.string.console_settings_delete_calendar_confirm_title) to
        stringResource(R.string.console_settings_delete_calendar_confirm_body)
    DataSection.PERIOD -> stringResource(R.string.console_settings_delete_period_confirm_title) to
        stringResource(R.string.console_settings_delete_period_confirm_body)
    DataSection.LOCKER -> stringResource(R.string.console_settings_delete_locker_confirm_title) to
        stringResource(R.string.console_settings_delete_locker_confirm_body)
}

/** The "Delete data" action for one content section — its own [SettingsSectionCard] rather than a
 * row appended to that section's existing preference card, so a destructive action never sits
 * directly next to a toggle. */
@Composable
private fun DataDeletionSection(onDeleteClick: () -> Unit) {
    SettingsSectionCard(title = stringResource(R.string.console_settings_data_section_title)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDeleteClick),
        ) {
            Text(
                text = stringResource(R.string.console_settings_delete_data_action),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Bundles the confirm-dialog state, the success/failure toast collectors, and the
 * [DataDeletionSection] row itself — used identically by the Messages/Calendar/Period
 * Tracker/Safe Locker sub-pages (each scoped to their own [DataSection]) now that each lives on
 * its own screen rather than sharing one [LaunchedEffect] pair at the old single-screen level. */
@Composable
fun DataDeletionCategorySection(viewModel: ConsoleSettingsViewModel, section: DataSection) {
    var confirmDelete by remember { mutableStateOf(false) }
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deleteSuccessMessage = stringResource(R.string.console_settings_delete_success)
    val genericErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.deleteSucceeded.collect {
            Toast.makeText(context, deleteSuccessMessage, Toast.LENGTH_SHORT).show()
            confirmDelete = false
        }
    }
    LaunchedEffect(Unit) {
        viewModel.deleteFailed.collect {
            Toast.makeText(context, genericErrorMessage, Toast.LENGTH_LONG).show()
            confirmDelete = false
        }
    }
    DataDeletionSection(onDeleteClick = { confirmDelete = true })
    if (confirmDelete) {
        val (title, body) = section.confirmationCopy()
        DataDeletionConfirmationDialog(
            title = title,
            body = body,
            isPending = isDeleting,
            onConfirm = { viewModel.deleteData(section) },
            onDismiss = { confirmDelete = false },
        )
    }
}
