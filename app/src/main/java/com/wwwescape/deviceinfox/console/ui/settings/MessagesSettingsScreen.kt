package com.wwwescape.deviceinfox.console.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.settings.NotificationImportance
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundMode
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundTone
import com.wwwescape.deviceinfox.console.data.settings.NotificationTier
import com.wwwescape.deviceinfox.console.ui.tour.FeatureTourTarget
import com.wwwescape.deviceinfox.console.ui.tour.rememberFeatureTourScrollControl

/** Messages: notification wording/importance/sound settings, the Messages-scoped "Delete data"
 * action, and (once built, per the separate scheduled-messages TODO) the Scheduled Messages list
 * entry. The 3 [FeatureTourTarget]s below moved here with their rows — they wrap specific
 * composables by key and would silently stop firing if left behind on the old single screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ConsoleSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewingTone by viewModel.previewingTone.collectAsStateWithLifecycle()
    val isTonePreviewPlaying by viewModel.isTonePreviewPlaying.collectAsStateWithLifecycle()
    val previewProgress by viewModel.previewProgress.collectAsStateWithLifecycle()

    // A tone preview shouldn't keep playing in the background once this screen is left — it has
    // no other lifecycle hook of its own to catch that itself.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopTonePreview() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_messages)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        val scrollEnabled = rememberFeatureTourScrollControl(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState, enabled = scrollEnabled)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NotificationSection(
                selectedTier = uiState.notificationTier,
                onSelectTier = viewModel::setNotificationTier,
                selectedImportance = uiState.notificationImportance,
                onSelectImportance = viewModel::setNotificationImportance,
                selectedSoundMode = uiState.notificationSoundMode,
                onSelectSoundMode = viewModel::setNotificationSoundMode,
                selectedSoundThrottleMinutes = uiState.notificationSoundThrottleMinutes,
                onSelectSoundThrottleMinutes = viewModel::setNotificationSoundThrottleMinutes,
                selectedSoundTone = uiState.notificationSoundTone,
                onSelectSoundTone = viewModel::setNotificationSoundTone,
                previewingTone = previewingTone,
                isTonePreviewPlaying = isTonePreviewPlaying,
                previewProgress = previewProgress,
                onPreviewTone = viewModel::previewTone,
            )
            DataDeletionCategorySection(viewModel, DataSection.MESSAGES)
        }
    }
}

@Composable
private fun NotificationSection(
    selectedTier: NotificationTier,
    onSelectTier: (NotificationTier) -> Unit,
    selectedImportance: NotificationImportance,
    onSelectImportance: (NotificationImportance) -> Unit,
    selectedSoundMode: NotificationSoundMode,
    onSelectSoundMode: (NotificationSoundMode) -> Unit,
    selectedSoundThrottleMinutes: Int,
    onSelectSoundThrottleMinutes: (Int) -> Unit,
    selectedSoundTone: NotificationSoundTone,
    onSelectSoundTone: (NotificationSoundTone) -> Unit,
    previewingTone: NotificationSoundTone?,
    isTonePreviewPlaying: Boolean,
    previewProgress: Float,
    onPreviewTone: (NotificationSoundTone) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.console_settings_section_notifications)) {
        NotificationSubsectionLabel(stringResource(R.string.console_settings_notification_wording_label))
        NotificationTierRow(
            title = stringResource(R.string.console_settings_notification_generic),
            subtitle = stringResource(R.string.console_settings_notification_generic_subtitle),
            isSelected = selectedTier == NotificationTier.GENERIC,
            onClick = { onSelectTier(NotificationTier.GENERIC) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        NotificationTierRow(
            title = stringResource(R.string.console_settings_notification_disguised),
            subtitle = stringResource(R.string.console_settings_notification_disguised_subtitle),
            isSelected = selectedTier == NotificationTier.DISGUISED,
            onClick = { onSelectTier(NotificationTier.DISGUISED) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        FeatureTourTarget(
            tourKey = "alert_style",
            description = stringResource(R.string.console_settings_alert_style_tour_description),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NotificationSubsectionLabel(stringResource(R.string.console_settings_notification_importance_label))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_importance_high),
                    subtitle = stringResource(R.string.console_settings_notification_importance_high_subtitle),
                    isSelected = selectedImportance == NotificationImportance.HIGH,
                    onClick = { onSelectImportance(NotificationImportance.HIGH) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_importance_default),
                    subtitle = stringResource(R.string.console_settings_notification_importance_default_subtitle),
                    isSelected = selectedImportance == NotificationImportance.DEFAULT,
                    onClick = { onSelectImportance(NotificationImportance.DEFAULT) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        FeatureTourTarget(
            tourKey = "sound_mode",
            description = stringResource(R.string.console_settings_sound_tour_description),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NotificationSubsectionLabel(stringResource(R.string.console_settings_notification_sound_label))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_sound_always),
                    subtitle = stringResource(R.string.console_settings_notification_sound_always_subtitle),
                    isSelected = selectedSoundMode == NotificationSoundMode.ALWAYS,
                    onClick = { onSelectSoundMode(NotificationSoundMode.ALWAYS) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_sound_throttled, selectedSoundThrottleMinutes),
                    subtitle = stringResource(
                        R.string.console_settings_notification_sound_throttled_subtitle,
                        selectedSoundThrottleMinutes,
                    ),
                    isSelected = selectedSoundMode == NotificationSoundMode.THROTTLED,
                    onClick = { onSelectSoundMode(NotificationSoundMode.THROTTLED) },
                )
                // Choosing a window length is meaningless unless Throttled is actually the
                // selected mode — mirrors how the tone rows below disable themselves under
                // Muted, just as visibility instead of a disabled state, since there's nothing
                // sensible to show while a completely different mode is active.
                if (selectedSoundMode == NotificationSoundMode.THROTTLED) {
                    SoundThrottleMinutesSelector(
                        selectedMinutes = selectedSoundThrottleMinutes,
                        onSelectMinutes = onSelectSoundThrottleMinutes,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_sound_muted),
                    subtitle = stringResource(R.string.console_settings_notification_sound_muted_subtitle),
                    isSelected = selectedSoundMode == NotificationSoundMode.MUTED,
                    onClick = { onSelectSoundMode(NotificationSoundMode.MUTED) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        // Picking a tone is meaningless while Sound is Muted — nothing plays it either way — so
        // this whole subrow disables itself in that state rather than leaving two radio buttons
        // that silently do nothing when tapped.
        val soundToneRowsEnabled = selectedSoundMode != NotificationSoundMode.MUTED
        FeatureTourTarget(
            tourKey = "notification_sounds",
            description = stringResource(R.string.console_settings_notification_sounds_tour_description),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NotificationSubsectionLabel(stringResource(R.string.console_settings_notification_sound_tone_label))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_sound_tone_default),
                    subtitle = stringResource(R.string.console_settings_notification_sound_tone_default_subtitle),
                    isSelected = selectedSoundTone == NotificationSoundTone.DEFAULT,
                    onClick = { onSelectSoundTone(NotificationSoundTone.DEFAULT) },
                    enabled = soundToneRowsEnabled,
                    trailingContent = {
                        TonePreviewButton(
                            isPlaying = previewingTone == NotificationSoundTone.DEFAULT && isTonePreviewPlaying,
                            progress = if (previewingTone == NotificationSoundTone.DEFAULT) previewProgress else 0f,
                            onClick = { onPreviewTone(NotificationSoundTone.DEFAULT) },
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                NotificationTierRow(
                    title = stringResource(R.string.console_settings_notification_sound_tone_chime),
                    subtitle = stringResource(R.string.console_settings_notification_sound_tone_chime_subtitle),
                    isSelected = selectedSoundTone == NotificationSoundTone.CHIME,
                    onClick = { onSelectSoundTone(NotificationSoundTone.CHIME) },
                    enabled = soundToneRowsEnabled,
                    trailingContent = {
                        TonePreviewButton(
                            isPlaying = previewingTone == NotificationSoundTone.CHIME && isTonePreviewPlaying,
                            progress = if (previewingTone == NotificationSoundTone.CHIME) previewProgress else 0f,
                            onClick = { onPreviewTone(NotificationSoundTone.CHIME) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationSubsectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 5/10/15-minute options for [NotificationSoundMode.THROTTLED]'s window — same
 * `SingleChoiceSegmentedButtonRow`/`SegmentedButton` shape as [ProfileScreen]'s Gender picker,
 * indented to roughly align under the Throttled row's own title/subtitle text rather than
 * starting at the row's left edge. */
@Composable
private fun SoundThrottleMinutesSelector(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    ) {
        THROTTLE_MINUTES_OPTIONS.forEachIndexed { index, minutes ->
            SegmentedButton(
                selected = selectedMinutes == minutes,
                onClick = { onSelectMinutes(minutes) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = THROTTLE_MINUTES_OPTIONS.size),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.console_settings_notification_sound_throttle_minutes_option, minutes),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val THROTTLE_MINUTES_OPTIONS = listOf(5, 10, 15)

@Composable
private fun NotificationTierRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    // Standard Material "disabled content" alpha (matches what RadioButton/Switch/etc. already
    // apply to themselves internally when enabled = false) — applied by hand to the two Text
    // labels since they aren't otherwise aware of this row's enabled state.
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        RadioButton(selected = isSelected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        // A separate tap target from this Row's own clickable above — Compose routes a tap
        // inside trailingContent's own IconButton to it directly rather than bubbling up to the
        // Row, so previewing a tone here never also selects it (deliberately independent, see
        // TODOS.md). Left tappable regardless of `enabled`/Muted, unlike the radio button itself
        // — auditioning a tone doesn't depend on whether live notification sound is on right now.
        trailingContent?.invoke()
    }
}

/** Play/pause button for auditioning a [NotificationSoundTone] from Settings, ringed by a
 * determinate progress indicator: [CircularProgressIndicator]'s own track is always drawn as a
 * full circle regardless of progress, which doubles as the "faint outline when idle" state for
 * free — only the colored arc on top needs [progress] to actually be nonzero. [isPlaying] false
 * covers both "nothing loaded" and "loaded but paused" — either way the ring keeps whatever
 * [progress] it was last at (0 in the idle case, wherever it was paused otherwise) and the icon
 * reverts to Play, matching [Icons.Rounded.Pause]/[Icons.Rounded.PlayArrow]'s real pause/resume
 * semantics rather than a stop-and-restart-from-0 toggle. */
@Composable
private fun TonePreviewButton(
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) {
                        R.string.console_settings_notification_sound_tone_pause_preview
                    } else {
                        R.string.console_settings_notification_sound_tone_play_preview
                    },
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
