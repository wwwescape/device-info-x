package com.wwwescape.deviceinfox.console.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog

/** Danger Zone: the two escalating account-wide destructive actions (Delete all data / Destroy all
 * data, unchanged from the original [DangerZoneSection]), plus Unpair and Change Server — both
 * moved in from General since both are already styled destructively there (Unpair ends the whole
 * partnership, Change Server wipes local data as a side effect) and fit this page's scope better,
 * per the Settings-restructure TODO writeup's own decision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DangerZoneSettingsScreen(
    onBack: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsoleSettingsViewModel = hiltViewModel(),
) {
    var showDeleteAllDataConfirm by remember { mutableStateOf(false) }
    var showDestroyAccountDialog by remember { mutableStateOf(false) }
    var showChangeServerDialog by remember { mutableStateOf(false) }
    var showUnpairConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val genericErrorMessage = stringResource(R.string.console_error_network)
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleteSuccessMessage = stringResource(R.string.console_settings_delete_success)
    LaunchedEffect(Unit) {
        viewModel.deleteSucceeded.collect {
            Toast.makeText(context, deleteSuccessMessage, Toast.LENGTH_SHORT).show()
            showDeleteAllDataConfirm = false
        }
    }
    LaunchedEffect(Unit) {
        viewModel.deleteFailed.collect {
            Toast.makeText(context, genericErrorMessage, Toast.LENGTH_LONG).show()
            showDeleteAllDataConfirm = false
        }
    }

    val serverChanged by viewModel.serverChanged.collectAsStateWithLifecycle()
    LaunchedEffect(serverChanged) {
        if (serverChanged) onLock()
    }
    val isChangingServer by viewModel.isChangingServer.collectAsStateWithLifecycle()
    val changeServerFailed by viewModel.changeServerFailed.collectAsStateWithLifecycle()

    val unpairSucceeded by viewModel.unpairSucceeded.collectAsStateWithLifecycle()
    LaunchedEffect(unpairSucceeded) {
        if (unpairSucceeded) onLock()
    }
    val isUnpairing by viewModel.isUnpairing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.unpairFailed.collect { detail ->
            Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_danger_zone)) },
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
            SettingsSectionCard(title = null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { showUnpairConfirm = true },
                ) {
                    Text(
                        text = stringResource(R.string.console_pairing_unpair_action),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { showChangeServerDialog = true }),
                ) {
                    Text(
                        text = stringResource(R.string.console_settings_change_server_action),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            DangerZoneSection(
                onDeleteAllDataClick = { showDeleteAllDataConfirm = true },
                onDestroyAllDataClick = { showDestroyAccountDialog = true },
            )
        }
    }

    if (showDeleteAllDataConfirm) {
        DataDeletionConfirmationDialog(
            title = stringResource(R.string.console_settings_delete_all_confirm_title),
            body = stringResource(R.string.console_settings_delete_all_confirm_body),
            isPending = isDeleting,
            onConfirm = viewModel::deleteAllData,
            onDismiss = { showDeleteAllDataConfirm = false },
        )
    }
    if (showDestroyAccountDialog) {
        DestroyAccountDialog(
            onDismiss = { showDestroyAccountDialog = false },
            onDestroyed = onLock,
        )
    }
    if (showChangeServerDialog) {
        ChangeServerDialog(
            currentServerUrl = viewModel.serverUrl,
            isPending = isChangingServer,
            failed = changeServerFailed,
            onConfirm = viewModel::changeServer,
            onDismiss = { showChangeServerDialog = false },
        )
    }
    if (showUnpairConfirm) {
        UnpairConfirmationDialog(isPending = isUnpairing, onConfirm = viewModel::unpair, onDismiss = { showUnpairConfirm = false })
    }
}
