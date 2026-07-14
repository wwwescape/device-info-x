package com.wwwescape.deviceinfox.console.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.ui.components.LiveLocationDotColor
import com.wwwescape.deviceinfox.console.ui.components.UpdateAvailableCardColor
import com.wwwescape.deviceinfox.console.ui.components.UpdateAvailableCardContentColor
import com.wwwescape.deviceinfox.console.ui.tour.FeatureTourTarget

/** The WhatsApp/Signal-style top-level Settings menu — a profile row, the Update Available card,
 * then one row per category, each navigating to its own dedicated sub-page via
 * [ConsoleSettingsNavHost]. Was previously one long flat scroll containing every setting inline;
 * see the Settings-restructure TODO writeup for the full category → sub-page content mapping this
 * was built from. [onBack] here pops the *outer* nav graph (back to the tabs), unlike every
 * sub-page's own back arrow, which only pops this screen's nested graph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleSettingsMainScreen(
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onGeneralClick: () -> Unit,
    onBirthdayClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onPeriodTrackerClick: () -> Unit,
    onSafeLockerClick: () -> Unit,
    onDangerousClick: () -> Unit,
    onLiveLocationClick: () -> Unit,
    onNotepadClick: () -> Unit,
    onScheduledMessagesClick: () -> Unit,
    onGameRoomClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsoleSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLoveQuoteDialog by remember { mutableStateOf(false) }

    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationActive.collectAsStateWithLifecycle()
    val updateDownloadUrl by viewModel.updateDownloadUrl.collectAsStateWithLifecycle()
    // Per-composition only, not persisted — reappears next time Settings is opened while still
    // out of date, which is fine; the point is letting this specific visit stop being interrupted
    // by it, not permanently silencing it the way a real "don't show again" would.
    var updateCardDismissed by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    FeatureTourTarget(
                        tourKey = "game_room",
                        description = stringResource(R.string.console_game_room_tour_description),
                    ) {
                        IconButton(onClick = onGameRoomClick) {
                            Icon(
                                imageVector = Icons.Rounded.Casino,
                                contentDescription = stringResource(R.string.console_game_room_title),
                            )
                        }
                    }
                    FeatureTourTarget(
                        tourKey = "scheduled_messages",
                        description = stringResource(R.string.console_scheduled_messages_tour_description),
                    ) {
                        IconButton(onClick = onScheduledMessagesClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ScheduleSend,
                                contentDescription = stringResource(R.string.console_scheduled_messages_title),
                            )
                        }
                    }
                    FeatureTourTarget(
                        tourKey = "notepad",
                        description = stringResource(R.string.console_notepad_tour_description),
                    ) {
                        IconButton(onClick = onNotepadClick) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = stringResource(R.string.console_notepad_title),
                            )
                        }
                    }
                    FeatureTourTarget(
                        tourKey = "live_location",
                        description = stringResource(R.string.console_live_location_tour_description),
                    ) {
                        IconButton(onClick = onLiveLocationClick) {
                            // The badge dot lives here, not on this screen's own gear-icon entry
                            // point (there isn't one — this *is* Settings) — see isLiveLocationActive's
                            // own doc comment.
                            if (isLiveLocationActive) {
                                BadgedBox(badge = { Badge(containerColor = LiveLocationDotColor) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Map,
                                        contentDescription = stringResource(R.string.console_live_location_title),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Map,
                                    contentDescription = stringResource(R.string.console_live_location_title),
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLoveQuoteDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.console_love_note_action),
                            tint = LoveHeartRed,
                        )
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
            // First card in the screen, per the design this was built from — download_url can be
            // null (APK_DOWNLOAD_URL not configured server-side yet), in which case there's
            // nowhere useful to send a tap, so the card just doesn't show rather than rendering a
            // dead button.
            if (isUpdateAvailable && !updateCardDismissed && updateDownloadUrl != null) {
                UpdateAvailableCard(
                    downloadUrl = updateDownloadUrl!!,
                    onDismiss = { updateCardDismissed = true },
                )
            }

            ProfileRow(
                displayName = uiState.displayName,
                firstName = uiState.firstName,
                lastName = uiState.lastName,
                photoUri = uiState.photoUri,
                onClick = onProfileClick,
            )

            SettingsCategoryRow(
                icon = Icons.Rounded.Tune,
                label = stringResource(R.string.console_settings_section_general),
                onClick = onGeneralClick,
            )
            SettingsCategoryRow(
                icon = Icons.Rounded.Cake,
                label = stringResource(R.string.console_settings_section_birthday),
                onClick = onBirthdayClick,
            )
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Rounded.Chat,
                label = stringResource(R.string.console_settings_section_messages),
                onClick = onMessagesClick,
            )
            SettingsCategoryRow(
                icon = Icons.Rounded.CalendarMonth,
                label = stringResource(R.string.console_settings_section_calendar),
                onClick = onCalendarClick,
            )
            // Same gender-based visibility rule as the bottom Period Tracker tab
            // (ConsoleTabsViewModel.isPeriodTrackerVisible) — not a new gating mechanism, just
            // reused for this row too.
            if (uiState.gender == PartnerGender.FEMALE) {
                SettingsCategoryRow(
                    icon = Icons.Rounded.WaterDrop,
                    label = stringResource(R.string.console_settings_section_period_tracker),
                    onClick = onPeriodTrackerClick,
                )
            }
            SettingsCategoryRow(
                icon = Icons.Rounded.PhotoLibrary,
                label = stringResource(R.string.console_settings_section_safe_locker),
                onClick = onSafeLockerClick,
            )
            SettingsCategoryRow(
                icon = Icons.Rounded.Warning,
                label = stringResource(R.string.console_settings_section_dangerous),
                iconTint = MaterialTheme.colorScheme.error,
                labelColor = MaterialTheme.colorScheme.error,
                onClick = onDangerousClick,
            )
        }
    }

    if (showLoveQuoteDialog) {
        LoveQuoteDialog(onDismiss = { showLoveQuoteDialog = false })
    }
}

/** WhatsApp/Signal's own profile row, adapted: avatar + name only (no phone/QR, which don't apply
 * here), tapping the whole row opens [ProfileScreen] — this row is purely a navigation/preview
 * surface, not itself a photo picker or editable field the way it was inline in the old flat
 * page. */
@Composable
private fun ProfileRow(displayName: String, firstName: String, lastName: String, photoUri: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val name = displayName.ifBlank { "$firstName $lastName".trim() }
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
    }
}

/** One row per category on the main menu — icon, label, trailing chevron, matching the reference
 * apps' own row shape. [iconTint]/[labelColor] default to plain content colors; Dangerous is the
 * only category that overrides both to `error`, matching how its own destructive rows are styled
 * on the sub-page it leads to. */
@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            modifier = Modifier.weight(1f).padding(start = 20.dp),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The in-app update check's card — always the first thing in this screen when shown (see the
 * TODOS.md writeup this was built from), so it can't be mistaken for one more routine settings
 * row. No longer flashes/pulses (removed per on-device feedback — read as an error/urgent-alert
 * state rather than a routine "an update exists" notice, and was distracting on a screen the user
 * might sit on for a while). Dismissible via its own close button ([onDismiss]); tapping Download
 * opens [downloadUrl] in the browser — no in-app installer, no `REQUEST_INSTALL_PACKAGES`
 * permission, by design (see that decision's own reasoning in the TODOS.md writeup). */
@Composable
private fun UpdateAvailableCard(downloadUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = UpdateAvailableCardColor),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = UpdateAvailableCardContentColor,
                )
                Text(
                    text = stringResource(R.string.console_settings_update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = UpdateAvailableCardContentColor,
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp),
                )
                // Sized down from IconButton's 48dp default so its visible glyph sits close to
                // the card's own edge instead of floating in a conspicuously larger touch target
                // than the leading (unwrapped) icon has on the opposite side — same 32dp compact
                // size this app already uses for other in-context icon buttons (e.g. the tone
                // preview button, a voice-note bubble's play button).
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = UpdateAvailableCardContentColor,
                    )
                }
            }
            Text(
                text = stringResource(R.string.console_settings_update_available_body),
                style = MaterialTheme.typography.bodyMedium,
                color = UpdateAvailableCardContentColor,
            )
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, downloadUrl.toUri())) },
                colors = ButtonDefaults.textButtonColors(contentColor = UpdateAvailableCardContentColor),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.console_settings_update_available_download_action))
            }
        }
    }
}
