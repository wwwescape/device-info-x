package com.wwwescape.deviceinfox.console.ui.settings

import android.content.ClipData
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.auth.DuressStatus
import com.wwwescape.deviceinfox.console.data.pairing.PairingStatus
import com.wwwescape.deviceinfox.console.ui.auth.ChangePinDialog
import com.wwwescape.deviceinfox.console.ui.auth.DuressCodeInfoDialog
import kotlinx.coroutines.launch

/** General: identity/account-level settings that don't depend on which app areas (messages,
 * calendar, etc.) are even in use — Change PIN, pairing status/code/redeem, and server
 * address/status display. Unpair and Change Server both moved out to [DangerZoneSettingsScreen]
 * (see the Settings-restructure TODO writeup) since both are already styled destructively and fit
 * that page's existing scope better than this one's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ConsoleSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showChangePinDialog by remember { mutableStateOf(false) }
    val isRegeneratingCode by viewModel.isRegeneratingCode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val genericErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.regenerateCodeFailed.collect { detail ->
            Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val duressStatus by viewModel.duressStatus.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_general)) },
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
            SecuritySection(duressStatus = duressStatus, onChangePinClick = { showChangePinDialog = true })
            GeneralPairingSection(
                partnerCode = uiState.partnerCode,
                pairingStatus = uiState.pairingStatus,
                isRegeneratingCode = isRegeneratingCode,
                onRegenerate = viewModel::regeneratePartnerCode,
            )
            SettingsSectionCard(title = stringResource(R.string.console_settings_section_server)) {
                StatusRow(
                    label = stringResource(R.string.console_settings_server_address_label),
                    value = viewModel.serverUrl ?: stringResource(R.string.console_settings_server_status_placeholder),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ServerStatusRow(label = stringResource(R.string.console_settings_server_status_label), status = serverStatus)
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(onDismiss = { showChangePinDialog = false })
    }
}

@Composable
private fun SecuritySection(duressStatus: DuressStatus, onChangePinClick: () -> Unit) {
    var showDuressInfo by remember { mutableStateOf(false) }

    SettingsSectionCard(title = stringResource(R.string.console_settings_section_security)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onChangePinClick),
        ) {
            Text(stringResource(R.string.console_settings_change_pin), style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().clickable { showDuressInfo = true },
        ) {
            Text(
                text = stringResource(R.string.console_settings_duress_learn),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.console_settings_duress_status_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (duressStatus) {
                        DuressStatus.PENDING -> stringResource(R.string.console_settings_duress_status_pending)
                        DuressStatus.ACTIVE -> stringResource(R.string.console_settings_duress_status_active)
                        DuressStatus.INACTIVE -> stringResource(R.string.console_settings_duress_status_inactive)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (duressStatus == DuressStatus.INACTIVE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (duressStatus == DuressStatus.INACTIVE) {
                Text(
                    text = stringResource(R.string.console_settings_duress_status_inactive_reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (showDuressInfo) {
        DuressCodeInfoDialog(onDismiss = { showDuressInfo = false })
    }
}

/** [PairingSection] minus the Unpair row — Unpair now lives on [DangerZoneSettingsScreen] since it
 * ends the whole partnership and is already styled destructively, unlike the rest of this card. */
@Composable
private fun GeneralPairingSection(
    partnerCode: String?,
    pairingStatus: PairingStatus,
    isRegeneratingCode: Boolean,
    onRegenerate: () -> Unit,
) {
    var showRedeemDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    SettingsSectionCard(title = stringResource(R.string.console_settings_section_pairing)) {
        val pairingStatusText = when (pairingStatus) {
            PairingStatus.Unpaired -> stringResource(R.string.console_settings_pairing_status_not_paired)
            PairingStatus.Pending -> stringResource(R.string.console_pairing_status_pending)
            is PairingStatus.Paired -> stringResource(
                R.string.console_pairing_status_paired_with,
                pairingStatus.partnerDisplayName,
            )
        }
        StatusRow(label = stringResource(R.string.console_settings_pairing_status_label), value = pairingStatusText)

        // Once paired, this code no longer does anything (it's only good for an unpaired partner
        // to redeem) — showing it, and letting it be regenerated, invited the wrong idea that
        // it's still a live invite.
        if (pairingStatus !is PairingStatus.Paired) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(stringResource(R.string.console_settings_partner_code_label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = partnerCode.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row {
                    if (!partnerCode.isNullOrEmpty()) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("partner code", partnerCode)))
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.console_home_action_copy))
                        }
                    }
                    IconButton(onClick = onRegenerate, enabled = !isRegeneratingCode) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.console_settings_regenerate_code))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { showRedeemDialog = true },
            ) {
                Text(
                    text = stringResource(R.string.console_pairing_redeem_action),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showRedeemDialog) {
        RedeemCodeDialog(onDismiss = { showRedeemDialog = false })
    }
}
