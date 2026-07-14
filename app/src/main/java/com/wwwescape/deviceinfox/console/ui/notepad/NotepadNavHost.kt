package com.wwwescape.deviceinfox.console.ui.notepad

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/** Own nested `NavHost`, one level down from [com.wwwescape.deviceinfox.console.ui.settings.SettingsDestination.Notepad] —
 * mirrors [com.wwwescape.deviceinfox.console.ui.settings.ConsoleSettingsNavHost]'s own shape,
 * needed here (unlike Live Location, a single screen) because Notepad has real List → Editor
 * navigation of its own. */
sealed class NotepadDestination(val route: String) {
    data object List : NotepadDestination("notepad_list")

    /** [entryId] pulled straight off `NoteEditorViewModel`'s own `SavedStateHandle` — same
     * pattern [com.wwwescape.deviceinfox.console.ui.nav.ConsoleDestination.MessageInfo] already
     * established as this app's convention for a parameterized route. */
    data object Editor : NotepadDestination("notepad_editor/{entryId}") {
        fun routeFor(entryId: String) = "notepad_editor/$entryId"
    }
}

@Composable
fun NotepadNavHost(onBack: () -> Unit, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = NotepadDestination.List.route) {
        composable(NotepadDestination.List.route) {
            NotepadListScreen(
                onBack = onBack,
                onOpenEntry = { entryId -> navController.navigate(NotepadDestination.Editor.routeFor(entryId)) },
            )
        }
        composable(
            route = NotepadDestination.Editor.route,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) {
            NoteEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
