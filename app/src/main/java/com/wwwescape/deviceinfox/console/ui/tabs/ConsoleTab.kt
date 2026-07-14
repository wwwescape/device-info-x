package com.wwwescape.deviceinfox.console.ui.tabs

/** The console's bottom-tab routes — a separate, nested `NavHost` graph hosted by
 * [ConsoleTabsScreen], distinct from the outer graph's [com.wwwescape.deviceinfox.console.ui.nav.ConsoleDestination]
 * (Tabs/Settings). Kept as a sealed class mirroring `ConsoleDestination`'s own style. */
sealed class ConsoleTab(val route: String) {
    data object Messaging : ConsoleTab("console_tab_messaging")
    data object Calendar : ConsoleTab("console_tab_calendar")
    data object PeriodTracker : ConsoleTab("console_tab_period_tracker")
    data object Vault : ConsoleTab("console_tab_vault")
}
