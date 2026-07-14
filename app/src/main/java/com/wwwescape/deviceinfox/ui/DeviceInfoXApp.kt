package com.wwwescape.deviceinfox.ui

import com.wwwescape.deviceinfox.util.isConsoleSupported
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.navigation.NavGraphBuilder
import com.wwwescape.deviceinfox.ui.tv.LocalIsTv
import com.wwwescape.deviceinfox.ui.tv.focusHighlight
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.ui.auth.PinGateDialog
import com.wwwescape.deviceinfox.console.ui.nav.ConsoleActivity
import com.wwwescape.deviceinfox.data.notifications.fakeNotificationCardStateEntryPoint
import com.wwwescape.deviceinfox.ui.components.CenteredCollapsingTopBar
import com.wwwescape.deviceinfox.ui.navigation.Destination
import com.wwwescape.deviceinfox.ui.screens.OverviewScreen
import com.wwwescape.deviceinfox.ui.screens.battery.BatteryScreen
import com.wwwescape.deviceinfox.ui.screens.camera.CameraScreen
import com.wwwescape.deviceinfox.ui.screens.cpu.CpuScreen
import com.wwwescape.deviceinfox.ui.screens.deviceos.DeviceOsScreen
import com.wwwescape.deviceinfox.ui.screens.display.DisplayScreen
import com.wwwescape.deviceinfox.ui.screens.memory.MemoryScreen
import com.wwwescape.deviceinfox.ui.screens.network.NetworkScreen
import com.wwwescape.deviceinfox.ui.screens.sensors.SensorsScreen
import com.wwwescape.deviceinfox.ui.screens.settings.LicensesScreen
import com.wwwescape.deviceinfox.ui.screens.settings.SettingsScreen

private const val LICENSES_ROUTE = "licenses"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceInfoXApp(
    navController: NavHostController = rememberNavController(),
    startDestinationRoute: String? = null,
    onStartDestinationConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = Destination.fromRoute(currentRoute)
    val isOverview = currentRoute == Destination.Overview.route
    val isSettingsFamily = currentRoute == Destination.Settings.route ||
        currentRoute == LICENSES_ROUTE
    var showConsolePinGate by remember { mutableStateOf(false) }
    val consoleSupported = remember { context.isConsoleSupported() }

    // A widget tap arrives with a target route — navigate there once, then clear it so backing
    // out doesn't re-trigger the jump (and so a later widget tap on the same route still fires).
    LaunchedEffect(startDestinationRoute) {
        if (startDestinationRoute != null) {
            navController.navigate(startDestinationRoute) { launchSingleTop = true }
            onStartDestinationConsumed()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val titleText = when {
        isOverview -> stringResource(R.string.title_overview)
        currentRoute == LICENSES_ROUTE -> stringResource(R.string.section_open_source_licenses)
        else -> stringResource(currentDestination.titleRes)
    }

    val isTv = LocalIsTv.current

    Scaffold(
        // TV: keep everything inside the title-safe area — some TVs still overscan the edges.
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .then(if (isTv) Modifier.padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical) else Modifier),
        topBar = {
            CenteredCollapsingTopBar(
                title = titleText,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (isOverview) {
                        // Long-press is the private-console trigger — deliberately undocumented
                        // in the UI itself. Short tap keeps the logo's prior no-op/branding
                        // behavior unchanged. Phones only: on tablets and TVs the logo is a plain,
                        // non-interactive image, so a long-press does nothing at all.
                        Image(
                            painter = painterResource(R.drawable.ic_logo_mark),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(28.dp)
                                .then(
                                    if (consoleSupported) {
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = { showConsolePinGate = true },
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    } else {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.focusHighlight(CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (!isSettingsFamily) {
                        IconButton(
                            onClick = { navController.navigate(Destination.Settings.route) },
                            modifier = Modifier.focusHighlight(CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.title_settings),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Overview.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Detail screens are single-column lists of label/value rows; across a 16:9 TV a row
            // stretched edge to edge leaves label and value a whole screen-width apart. Center
            // them in a comfortable reading width instead. The dashboard grid keeps the full
            // width — it adapts its column count to fill it.
            fun NavGraphBuilder.detail(route: String, content: @Composable () -> Unit) =
                composable(route) { ReadableWidth(enabled = isTv) { content() } }

            composable(Destination.Overview.route) {
                OverviewScreen(onCategoryClick = { destination -> navController.navigate(destination.route) })
            }
            detail(Destination.DeviceOs.route) {
                DeviceOsScreen()
            }
            detail(Destination.Cpu.route) {
                CpuScreen()
            }
            detail(Destination.Memory.route) {
                MemoryScreen()
            }
            detail(Destination.Battery.route) {
                BatteryScreen()
            }
            detail(Destination.Display.route) {
                DisplayScreen()
            }
            detail(Destination.Network.route) {
                NetworkScreen()
            }
            detail(Destination.Sensors.route) {
                SensorsScreen()
            }
            detail(Destination.Camera.route) {
                CameraScreen()
            }
            detail(Destination.Settings.route) {
                SettingsScreen(
                    onNavigateToLicenses = { navController.navigate(LICENSES_ROUTE) },
                )
            }
            detail(LICENSES_ROUTE) {
                LicensesScreen()
            }
        }
    }

    if (showConsolePinGate && consoleSupported) {
        PinGateDialog(
            onDismiss = { showConsolePinGate = false },
            onAuthenticated = {
                // The fake notification card (if one is showing) has to close the instant the
                // real console is entered — it's a stealth artifact for the *public* app, and
                // would otherwise sit there stale behind the private console for as long as it
                // stays open. See FakeNotificationCardState's own doc comment for the other two
                // close triggers.
                context.fakeNotificationCardStateEntryPoint().dismiss()
                // Deliberately no FLAG_ACTIVITY_NEW_TASK — ConsoleActivity stacks onto
                // MainActivity's existing task on purpose, so Recents only ever shows one
                // "Device Info X" card. It used to launch into its own task (distinct
                // taskAffinity), which left a second, permanently-blank (FLAG_SECURE-redacted)
                // card sitting in Recents whenever the console was open — itself a giveaway that
                // something was being hidden, independent of whether real content ever leaked.
                context.startActivity(Intent(context, ConsoleActivity::class.java))
            },
        )
    }
}

private val TvOverscanHorizontal = 32.dp
private val TvOverscanVertical = 16.dp
private val ReadableMaxWidth = 760.dp

@Composable
private fun ReadableWidth(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = ReadableMaxWidth).fillMaxSize()) { content() }
    }
}
