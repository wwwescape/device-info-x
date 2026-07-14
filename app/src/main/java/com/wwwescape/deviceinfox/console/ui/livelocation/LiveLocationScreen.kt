package com.wwwescape.deviceinfox.console.ui.livelocation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
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
import com.wwwescape.deviceinfox.console.ui.components.PresenceAvatar
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
    val isTogglingSharing by viewModel.isTogglingSharing.collectAsStateWithLifecycle()
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
                        enabled = !isTogglingSharing,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val bothSharing = selfSharing && partnerLocation.isSharing
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Only shown pre-map now — once both are sharing, the same name+status info floats as
            // compact chips over the map itself instead (LiveLocationMapView), freeing the vertical
            // space this full-width row used to take above it.
            if (!bothSharing) {
                PartnerStatusRow(
                    selfDisplayName = selfDisplayName,
                    selfSharing = selfSharing,
                    partnerDisplayName = partnerDisplayName,
                    partnerSharing = partnerLocation.isSharing,
                )
            }

            if (!hasFineLocation || !hasBackgroundLocation) {
                Text(
                    text = stringResource(R.string.console_live_location_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            if (bothSharing) {
                LiveLocationMapView(
                    selfLocation = selfLocation,
                    selfInitials = selfInitials,
                    selfDisplayName = selfDisplayName,
                    partnerLocation = partnerLocation,
                    partnerInitials = partnerInitials,
                    partnerDisplayName = partnerDisplayName,
                    routeState = routeState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.console_live_location_waiting_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        // A second, more discoverable way into the same onToggleChanged(true) path
                        // the top-bar Switch already drives — only offered while self hasn't
                        // already flipped it on, since past that point there's nothing left for
                        // this device to do but wait on the partner.
                        if (!selfSharing) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = { onToggleChanged(true) }, enabled = !isTogglingSharing) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.console_live_location_start_sharing_action))
                            }
                        }
                    }
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
 * layout) — now shown only pre-map (both states fold into `LiveLocationMapView`'s own compact
 * chip overlay once sharing is actually active, see the Stitch-mockup-inspired redesign in
 * TODOS.md), so every remaining render of this row is inherently a "not yet connected on this"
 * moment — the small connector glyph between the two entries leans into that directly. Each entry
 * is now the same avatar-circle/name/status-line stack Game Room's own `PlayerAvatarColumn` and
 * Call Room's `PartnerStatusColumn` use, via the shared [PresenceAvatar] — self solid violet,
 * partner neutral gray, same self/partner color language as those two. The status icon reflects
 * Live Location sharing specifically ([selfSharing]/[partnerSharing], this screen's own existing
 * data), not the separate app-wide online/offline presence Messages shows — a deliberate scope
 * decision, not an oversight. */
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
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PartnerStatusEntry(
            name = selfDisplayName ?: stringResource(R.string.console_home_title_fallback),
            sharing = selfSharing,
            isSelf = true,
        )
        ConnectionConnector(selfSharing = selfSharing, partnerSharing = partnerSharing)
        PartnerStatusEntry(
            name = partnerDisplayName ?: stringResource(R.string.console_home_title_fallback),
            sharing = partnerSharing,
            isSelf = false,
        )
    }
}

/** Each half-line reflects that side's own sharing state independently (self half = [selfSharing],
 * partner half = [partnerSharing]), so a one-sided share shows as a mixed green/red line rather
 * than collapsing to a single overall color — the center glyph is the only thing that summarizes
 * both into one connected/not-connected verdict. */
@Composable
private fun ConnectionConnector(selfSharing: Boolean, partnerSharing: Boolean) {
    val selfColor = if (selfSharing) ServerOnlineColor else MaterialTheme.colorScheme.error
    val partnerColor = if (partnerSharing) ServerOnlineColor else MaterialTheme.colorScheme.error
    val connected = selfSharing && partnerSharing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(16.dp).height(2.dp).background(selfColor))
        Icon(
            imageVector = if (connected) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (connected) ServerOnlineColor else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp).padding(horizontal = 2.dp),
        )
        Box(modifier = Modifier.width(16.dp).height(2.dp).background(partnerColor))
    }
}

@Composable
private fun PartnerStatusEntry(name: String, sharing: Boolean, isSelf: Boolean) {
    // Same "no fixed success role in Material3" reasoning as the Settings server-status dot
    // (ServerOnlineColor) — reused verbatim rather than picking a new literal for the same
    // meaning; not-sharing uses the theme's own error red, also matching that precedent.
    val statusColor = if (sharing) ServerOnlineColor else MaterialTheme.colorScheme.error
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PresenceAvatar(
            name = name,
            isSelf = isSelf,
            presenceBadge = {
                Icon(
                    painter = painterResource(if (sharing) R.drawable.ic_live_location_online else R.drawable.ic_live_location_offline),
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp),
                )
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(
                if (sharing) R.string.console_live_location_sharing_content_description else R.string.console_live_location_not_sharing_content_description,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
        )
    }
}
