package com.wwwescape.deviceinfox.console.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.events.HolidayEventType
import com.wwwescape.deviceinfox.console.ui.calendar.CalendarScreen
import com.wwwescape.deviceinfox.console.ui.components.EventPopup
import com.wwwescape.deviceinfox.console.ui.home.COMPOSER_PANEL_ANIMATION_MILLIS
import com.wwwescape.deviceinfox.console.ui.home.HomeScreen
import com.wwwescape.deviceinfox.console.ui.periodtracker.PeriodTrackerScreen
import com.wwwescape.deviceinfox.console.ui.vault.VaultScreen

/** Hosts the console's 4 sibling tabs (3 without a visible partner-gender signal — see
 * [ConsoleTabsViewModel]) behind a bottom [NavigationBar], via a nested [NavHost] so each tab
 * keeps its own back-stack-entry-scoped `ViewModel`/scroll position when switching away and back
 * (the standard Compose Navigation bottom-tab pattern — `saveState`/`restoreState` below). */
@Composable
fun ConsoleTabsScreen(
    onSettingsClick: () -> Unit,
    onStarredMessagesClick: () -> Unit,
    onPinnedMessagesClick: () -> Unit,
    onMediaClick: () -> Unit,
    onInfoClick: (String) -> Unit,
    onCallRoomClick: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConsoleTabsViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val isPeriodTrackerVisible by viewModel.isPeriodTrackerVisible.collectAsStateWithLifecycle()
    val birthdayPopupData by viewModel.birthdayPopupData.collectAsStateWithLifecycle()
    val holidayEventPopup by viewModel.holidayEventPopup.collectAsStateWithLifecycle()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    // Whether the Messages composer currently has the keyboard, emoji picker, or attach panel
    // occupying the "below the composer" slot — reported by HomeScreen via
    // onComposerExpandedChanged below. Drives ConsoleNavigationBar's visibility so it doesn't
    // compete for that space; see ComposerBar's own isExpanded computation for the source signal.
    var isMessagesComposerExpanded by remember { mutableStateOf(false) }
    val isMessagingTabActive = currentDestination?.hierarchy?.any { it.route == ConsoleTab.Messaging.route } == true

    // Defensive — the tab itself is already hidden below in this case, so this only fires if
    // gender config changes to no longer qualify while Period Tracker happens to be selected.
    LaunchedEffect(isPeriodTrackerVisible, currentDestination) {
        val onPeriodTrackerTab = currentDestination?.hierarchy?.any { it.route == ConsoleTab.PeriodTracker.route } == true
        if (!isPeriodTrackerVisible && onPeriodTrackerTab) {
            navController.navigate(ConsoleTab.Messaging.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    // imePadding here (rather than on HomeScreen's own composer, where it lived before) is what
    // makes the keyboard push this whole tab strip — not just the composer — up out of the way.
    // With it only on the composer, this Column's own height allocation (via the weighted Box
    // below) stayed keyboard-unaware, so ConsoleNavigationBar never moved; the composer would end
    // up sitting a whole navigation-bar's height above where the keyboard actually started,
    // leaving a visible gap between them that was really just ConsoleNavigationBar's height,
    // rendered blank because the keyboard wasn't tall enough to cover it.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = ConsoleTab.Messaging.route) {
                composable(ConsoleTab.Messaging.route) {
                    HomeScreen(
                        onSettingsClick = onSettingsClick,
                        onStarredMessagesClick = onStarredMessagesClick,
                        onPinnedMessagesClick = onPinnedMessagesClick,
                        onMediaClick = onMediaClick,
                        onInfoClick = onInfoClick,
                        onCallRoomClick = onCallRoomClick,
                        onLock = onLock,
                        onComposerExpandedChanged = { isMessagesComposerExpanded = it },
                    )
                }
                composable(ConsoleTab.Calendar.route) {
                    CalendarScreen(onSettingsClick = onSettingsClick, onLock = onLock)
                }
                composable(ConsoleTab.PeriodTracker.route) {
                    PeriodTrackerScreen(onSettingsClick = onSettingsClick, onLock = onLock)
                }
                composable(ConsoleTab.Vault.route) {
                    VaultScreen(onSettingsClick = onSettingsClick, onLock = onLock)
                }
            }
        }
        // Hidden while the Messages composer has the keyboard/emoji picker/attach panel open, so
        // it doesn't compete for the "below the composer" space those occupy — see
        // isMessagesComposerExpanded above. Gated on isMessagingTabActive too so switching to
        // another tab can't leave the bar hidden on a stale flag from the Messages screen.
        AnimatedVisibility(
            visible = !(isMessagingTabActive && isMessagesComposerExpanded),
            enter = expandVertically(animationSpec = tween(COMPOSER_PANEL_ANIMATION_MILLIS)),
            exit = shrinkVertically(animationSpec = tween(COMPOSER_PANEL_ANIMATION_MILLIS)),
        ) {
            ConsoleNavigationBar(
                currentDestination = currentDestination,
                isPeriodTrackerVisible = isPeriodTrackerVisible,
                onSelectTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }

    birthdayPopupData?.let { data ->
        EventPopup(
            iconRes = HolidayEventType.BIRTHDAY.iconRes,
            title = stringResource(R.string.console_birthday_popup_title),
            message = stringResource(R.string.console_birthday_popup_message, data.partnerName),
            secondaryMessage = data.customMessage,
            onDismiss = { viewModel.dismissBirthdayPopup() },
        )
    }

    holidayEventPopup?.let { event ->
        EventPopup(
            iconRes = event.type.iconRes,
            title = event.name,
            message = event.wishes,
            onDismiss = { viewModel.dismissHolidayEventPopup() },
        )
    }
}

@Composable
private fun ConsoleNavigationBar(
    currentDestination: NavDestination?,
    isPeriodTrackerVisible: Boolean,
    onSelectTab: (ConsoleTab) -> Unit,
) {
    NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
        ConsoleTabItem(
            tab = ConsoleTab.Messaging,
            icon = Icons.AutoMirrored.Rounded.Chat,
            label = stringResource(R.string.console_home_title_fallback),
            currentDestination = currentDestination,
            onSelectTab = onSelectTab,
        )
        ConsoleTabItem(
            tab = ConsoleTab.Calendar,
            icon = Icons.Rounded.CalendarMonth,
            label = stringResource(R.string.console_calendar_title),
            currentDestination = currentDestination,
            onSelectTab = onSelectTab,
        )
        if (isPeriodTrackerVisible) {
            ConsoleTabItem(
                tab = ConsoleTab.PeriodTracker,
                icon = Icons.Rounded.WaterDrop,
                label = stringResource(R.string.console_period_title),
                currentDestination = currentDestination,
                onSelectTab = onSelectTab,
            )
        }
        ConsoleTabItem(
            tab = ConsoleTab.Vault,
            icon = Icons.Rounded.PhotoLibrary,
            label = stringResource(R.string.console_vault_title),
            currentDestination = currentDestination,
            onSelectTab = onSelectTab,
        )
    }
}

@Composable
private fun RowScope.ConsoleTabItem(
    tab: ConsoleTab,
    icon: ImageVector,
    label: String,
    currentDestination: NavDestination?,
    onSelectTab: (ConsoleTab) -> Unit,
) {
    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
    NavigationBarItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}

/** Insets a tab screen's own `Scaffold` should reserve — top/horizontal only. The bottom
 * safe-drawing inset is already consumed by [NavigationBar] below it in [ConsoleTabsScreen]'s
 * `Column`; since `WindowInsets` are window-relative rather than container-relative, a nested
 * `Scaffold` using the M3 default (which includes bottom) would reserve that space a second
 * time, leaving a visible gap above the nav bar. */
val ConsoleTabContentWindowInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
