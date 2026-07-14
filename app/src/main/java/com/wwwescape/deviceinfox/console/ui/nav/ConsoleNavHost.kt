package com.wwwescape.deviceinfox.console.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wwwescape.deviceinfox.console.ui.home.MessageInfoScreen
import com.wwwescape.deviceinfox.console.ui.home.StarredPinnedMessagesScreen
import com.wwwescape.deviceinfox.console.ui.home.StarredPinnedMode
import com.wwwescape.deviceinfox.console.ui.media.ConversationMediaScreen
import com.wwwescape.deviceinfox.console.ui.settings.ConsoleSettingsNavHost
import com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabsScreen
import com.wwwescape.deviceinfox.console.ui.tour.FeatureTourOverlay
import com.wwwescape.deviceinfox.console.ui.whatsnew.WhatsNewOverlay

@Composable
fun ConsoleNavHost(onLock: () -> Unit, navController: NavHostController = rememberNavController()) {
    // FeatureTourOverlay is mounted once here, above the entire outer nav graph (Tabs/Settings/
    // Starred/Pinned) and by extension the bottom-tab NavHost nested inside Tabs too — the one
    // point in the tree above every screen a coach mark could ever target. See
    // FeatureTourCoordinator's class doc for why it's reached via a Singleton + EntryPoint rather
    // than a hiltViewModel() scoped to whichever NavHost destination happens to be showing.
    // WhatsNewOverlay is mounted alongside it, same "once per console open, regardless of which
    // tab" reasoning — unlike FeatureTourOverlay it has no cross-screen target registration to
    // coordinate, so a plain hiltViewModel() here (scoped to whatever ViewModelStoreOwner hosts
    // this whole NavHost) is enough, no Singleton+EntryPoint escape hatch needed.
    Box {
        NavHost(navController = navController, startDestination = ConsoleDestination.Tabs.route) {
            composable(ConsoleDestination.Tabs.route) {
                ConsoleTabsScreen(
                    onSettingsClick = { navController.navigate(ConsoleDestination.Settings.route) },
                    onStarredMessagesClick = { navController.navigate(ConsoleDestination.StarredMessages.route) },
                    onPinnedMessagesClick = { navController.navigate(ConsoleDestination.PinnedMessages.route) },
                    onMediaClick = { navController.navigate(ConsoleDestination.ConversationMedia.route) },
                    onInfoClick = { messageId -> navController.navigate(ConsoleDestination.MessageInfo.routeFor(messageId)) },
                    onLock = onLock,
                )
            }
            composable(ConsoleDestination.Settings.route) {
                ConsoleSettingsNavHost(onBack = { navController.popBackStack() }, onLock = onLock)
            }
            composable(ConsoleDestination.StarredMessages.route) {
                StarredPinnedMessagesScreen(mode = StarredPinnedMode.STARRED, onBack = { navController.popBackStack() })
            }
            composable(ConsoleDestination.PinnedMessages.route) {
                StarredPinnedMessagesScreen(mode = StarredPinnedMode.PINNED, onBack = { navController.popBackStack() })
            }
            composable(ConsoleDestination.ConversationMedia.route) {
                ConversationMediaScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ConsoleDestination.MessageInfo.route,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
            ) {
                // messageId itself isn't read here — MessageInfoViewModel pulls it straight off
                // its own injected SavedStateHandle (the standard Hilt-nav-compose mechanism for
                // this) rather than it being threaded through as an explicit screen param — the
                // first nav-arg-backed screen anywhere in this app, no existing precedent to match.
                MessageInfoScreen(onBack = { navController.popBackStack() })
            }
        }
        FeatureTourOverlay()
        WhatsNewOverlay()
    }
}
