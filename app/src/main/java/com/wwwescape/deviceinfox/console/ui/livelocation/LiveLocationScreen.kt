package com.wwwescape.deviceinfox.console.ui.livelocation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.location.PartnerLiveLocation
import com.wwwescape.deviceinfox.console.data.location.SelfLiveLocation
import com.wwwescape.deviceinfox.console.ui.settings.ServerOnlineColor

private fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

/** Entry point is the map-pin icon on
 * [com.wwwescape.deviceinfox.console.ui.settings.ConsoleSettingsMainScreen]'s top bar. Owns the
 * permission flow and the enable/disable toggle; the actual map rendering is
 * [LiveLocationMapView], only shown once both partners are confirmed sharing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationScreen(onBack: () -> Unit, viewModel: LiveLocationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val selfSharing by viewModel.selfSharing.collectAsStateWithLifecycle(initialValue = false)
    val selfLocation by viewModel.selfLocation.collectAsStateWithLifecycle(initialValue = null as SelfLiveLocation?)
    val selfInitials by viewModel.selfInitials.collectAsStateWithLifecycle(initialValue = "?")
    val selfDisplayName by viewModel.selfDisplayName.collectAsStateWithLifecycle(initialValue = null)
    val partnerLocation by viewModel.partnerLocation.collectAsStateWithLifecycle(initialValue = PartnerLiveLocation())
    val partnerInitials by viewModel.partnerInitials.collectAsStateWithLifecycle(initialValue = "?")
    val partnerDisplayName by viewModel.partnerDisplayName.collectAsStateWithLifecycle(initialValue = null)
    val routeState by viewModel.routeState.collectAsStateWithLifecycle()

    var hasFineLocation by remember { mutableStateOf(hasFineLocationPermission(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(hasBackgroundLocationPermission(context)) }

    // Re-checked on every resume, not just right after a permission callback — Android often
    // forces the background grant through the app-info Settings screen rather than an in-app
    // dialog (see AndroidManifest.xml's own comment on this), which this screen has no callback
    // for; catching it on resume is the only reliable way back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasFineLocation = hasFineLocationPermission(context)
                hasBackgroundLocation = hasBackgroundLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasBackgroundLocation = granted }

    // Android 11+ (R) refuses to show "Allow all the time" in the same dialog as the foreground
    // grant — it must be requested in a separate, subsequent call, only after FINE is confirmed
    // granted (see AndroidManifest.xml's own permission comment for the full reasoning).
    val finePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasFineLocation = granted
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val genericErrorMessage = stringResource(R.string.console_error_network)
    val endedManualMessage = stringResource(R.string.console_live_location_ended_manual)
    val endedAutoTimeoutMessage = stringResource(R.string.console_live_location_ended_auto_timeout)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail ->
            Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.sharingEndedEvent.collect { reason ->
            val message = if (reason == "auto_timeout") endedAutoTimeoutMessage else endedManualMessage
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // The in-app half of the 30-min-then-15-min check-in reminder — only reachable while this
    // screen is actually open to react to it; LiveLocationService posts a system notification
    // with the same two options unconditionally, covering every other case (backgrounded, or
    // just on a different tab).
    var showCheckInDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.checkInReminderEvent.collect { showCheckInDialog = true }
    }

    fun onToggleChanged(checked: Boolean) {
        when {
            !checked -> viewModel.disableSharing()
            !hasFineLocation -> finePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            !hasBackgroundLocation -> backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> viewModel.enableSharing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_live_location_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Every other action on this screen is an IconButton, which gets a 48dp
                    // centered touch target and free breathing room for free — a bare Switch has
                    // almost no margin around its own track, so without this it renders flush
                    // against the row's edge inset instead of matching that same visual weight.
                    Switch(
                        checked = selfSharing,
                        onCheckedChange = ::onToggleChanged,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val bothSharing = selfSharing && partnerLocation.isSharing
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PartnerStatusRow(
                selfDisplayName = selfDisplayName,
                selfSharing = selfSharing,
                partnerDisplayName = partnerDisplayName,
                partnerSharing = partnerLocation.isSharing,
            )

            if (!hasFineLocation || !hasBackgroundLocation) {
                Text(
                    text = stringResource(R.string.console_live_location_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (bothSharing) {
                LiveLocationMapView(
                    selfLocation = selfLocation,
                    selfInitials = selfInitials,
                    partnerLocation = partnerLocation,
                    partnerInitials = partnerInitials,
                    routeState = routeState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.console_live_location_waiting_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        }
    }

    if (showCheckInDialog) {
        AlertDialog(
            onDismissRequest = { showCheckInDialog = false },
            title = { Text(stringResource(R.string.console_live_location_checkin_title)) },
            text = { Text(stringResource(R.string.console_live_location_checkin_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCheckInDialog = false
                        viewModel.disableSharing()
                    },
                ) {
                    Text(stringResource(R.string.console_live_location_checkin_disable_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckInDialog = false }) {
                    Text(stringResource(R.string.console_live_location_checkin_ignore_action))
                }
            },
        )
    }
}

/** Replaces the old plain "You are sharing"/"partner is sharing" status text (per the redesigned
 * layout) — shown directly under the top bar in both the waiting state and the active-map state.
 * The status icon reflects Live Location sharing specifically ([selfSharing]/[partnerSharing],
 * this screen's own existing data), not the separate app-wide online/offline presence Messages
 * shows — a deliberate scope decision, not an oversight. */
@Composable
private fun PartnerStatusRow(
    selfDisplayName: String?,
    selfSharing: Boolean,
    partnerDisplayName: String?,
    partnerSharing: Boolean,
) {
    // Grouped in the center with a fixed gap, not spread edge-to-edge — SpaceBetween with only 2
    // children pushed both entries to the far corners and dumped all the leftover space in the
    // middle, which read worse in practice (confirmed on-device) than in the original mockup.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
    ) {
        PartnerStatusEntry(name = selfDisplayName ?: stringResource(R.string.console_home_title_fallback), sharing = selfSharing)
        PartnerStatusEntry(name = partnerDisplayName ?: stringResource(R.string.console_home_title_fallback), sharing = partnerSharing)
    }
}

@Composable
private fun PartnerStatusEntry(name: String, sharing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Icon(
            painter = painterResource(if (sharing) R.drawable.ic_live_location_online else R.drawable.ic_live_location_offline),
            contentDescription = stringResource(
                if (sharing) R.string.console_live_location_sharing_content_description else R.string.console_live_location_not_sharing_content_description,
            ),
            // Same "no fixed success role in Material3" reasoning as the Settings server-status
            // dot (ServerOnlineColor) — reused verbatim rather than picking a new literal for the
            // same meaning; offline uses the theme's own error red, also matching that precedent.
            tint = if (sharing) ServerOnlineColor else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}
