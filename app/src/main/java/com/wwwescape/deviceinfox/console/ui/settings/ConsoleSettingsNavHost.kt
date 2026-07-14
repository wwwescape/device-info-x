package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wwwescape.deviceinfox.console.ui.game.GameRoomScreen
import com.wwwescape.deviceinfox.console.ui.livelocation.LiveLocationScreen
import com.wwwescape.deviceinfox.console.ui.messaging.ScheduledMessagesScreen
import com.wwwescape.deviceinfox.console.ui.notepad.NotepadNavHost

/** Owns Settings' own nested `NavHost` — mirrors how
 * [com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabsScreen] hosts the bottom tabs' nested
 * graph — plus a single [ConsoleSettingsViewModel] instance shared across every destination in
 * it, obtained once here rather than separately per screen. Sharing one instance (rather than
 * each sub-page grabbing its own via `hiltViewModel()`) is what lets state started on one page
 * (e.g. the server-status poll) keep running across navigation to another, and is why every
 * `*SettingsScreen` below takes `viewModel` as a plain parameter instead of defaulting to its own
 * `hiltViewModel()` call when reached through this NavHost. [onBack] is only ever invoked from
 * [ConsoleSettingsMainScreen] — every other destination's back arrow just pops this nested graph
 * via [navController], not the outer one. */
@Composable
fun ConsoleSettingsNavHost(
    onBack: () -> Unit,
    onLock: () -> Unit,
    viewModel: ConsoleSettingsViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = SettingsDestination.Main.route) {
        composable(SettingsDestination.Main.route) {
            ConsoleSettingsMainScreen(
                onBack = onBack,
                onProfileClick = { navController.navigate(SettingsDestination.Profile.route) },
                onGeneralClick = { navController.navigate(SettingsDestination.General.route) },
                onBirthdayClick = { navController.navigate(SettingsDestination.Birthday.route) },
                onMessagesClick = { navController.navigate(SettingsDestination.Messages.route) },
                onCalendarClick = { navController.navigate(SettingsDestination.Calendar.route) },
                onPeriodTrackerClick = { navController.navigate(SettingsDestination.PeriodTracker.route) },
                onSafeLockerClick = { navController.navigate(SettingsDestination.SafeLocker.route) },
                onDixAiClick = { navController.navigate(SettingsDestination.DixAi.route) },
                onDangerZoneClick = { navController.navigate(SettingsDestination.DangerZone.route) },
                onLiveLocationClick = { navController.navigate(SettingsDestination.LiveLocation.route) },
                onNotepadClick = { navController.navigate(SettingsDestination.Notepad.route) },
                onScheduledMessagesClick = { navController.navigate(SettingsDestination.ScheduledMessages.route) },
                onGameRoomClick = { navController.navigate(SettingsDestination.GameRoom.route) },
                viewModel = viewModel,
            )
        }
        composable(SettingsDestination.Profile.route) {
            ProfileScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.General.route) {
            GeneralSettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.Birthday.route) {
            BirthdaySettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.Messages.route) {
            MessagesSettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.Calendar.route) {
            CalendarSettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.PeriodTracker.route) {
            PeriodTrackerSettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.SafeLocker.route) {
            SafeLockerSettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
        composable(SettingsDestination.DixAi.route) {
            DixAiSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsDestination.DangerZone.route) {
            DangerZoneSettingsScreen(onBack = { navController.popBackStack() }, onLock = onLock, viewModel = viewModel)
        }
        composable(SettingsDestination.LiveLocation.route) {
            LiveLocationScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsDestination.Notepad.route) {
            NotepadNavHost(onBack = { navController.popBackStack() })
        }
        composable(SettingsDestination.ScheduledMessages.route) {
            ScheduledMessagesScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsDestination.GameRoom.route) {
            GameRoomScreen(onBack = { navController.popBackStack() })
        }
    }
}
