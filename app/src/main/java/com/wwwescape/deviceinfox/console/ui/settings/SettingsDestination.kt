package com.wwwescape.deviceinfox.console.ui.settings

/** Routes for [ConsoleSettingsNavHost]'s own nested `NavHost` — mirrors
 * [com.wwwescape.deviceinfox.console.ui.nav.ConsoleDestination]'s shape, just one level down.
 * [Main] is the WhatsApp/Signal-style top-level menu (profile row, Update card, category rows);
 * every other destination here is a single dedicated sub-page reached by tapping one of those
 * rows. */
sealed class SettingsDestination(val route: String) {
    data object Main : SettingsDestination("settings_main")
    data object Profile : SettingsDestination("settings_profile")
    data object General : SettingsDestination("settings_general")
    data object Birthday : SettingsDestination("settings_birthday")
    data object Messages : SettingsDestination("settings_messages")
    data object Calendar : SettingsDestination("settings_calendar")
    data object PeriodTracker : SettingsDestination("settings_period_tracker")
    data object SafeLocker : SettingsDestination("settings_safe_locker")
    data object Dangerous : SettingsDestination("settings_dangerous")
    data object LiveLocation : SettingsDestination("settings_live_location")
    data object Notepad : SettingsDestination("settings_notepad")
    data object ScheduledMessages : SettingsDestination("settings_scheduled_messages")
    data object GameRoom : SettingsDestination("settings_game_room")
}
