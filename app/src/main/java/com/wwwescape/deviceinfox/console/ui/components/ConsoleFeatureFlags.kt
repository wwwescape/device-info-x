package com.wwwescape.deviceinfox.console.ui.components

/** Hides the manual lock icon (`Icons.Rounded.Lock`, wired to `onLock`) from every Console tab's
 * top bar — `HomeScreen`/`CalendarScreen`/`VaultScreen`/`PeriodTrackerScreen` all gate their own
 * `IconButton(onClick = onLock)` on this flag rather than having it removed. Explicit product
 * decision: for now, Home/power-button is the only way to exit the console, matching how a real
 * decoy app would behave — no dedicated "lock" affordance to draw attention to itself. The
 * `onLock` callback chain, `ConsoleSessionManager`, and everything downstream of it are untouched
 * and still fully functional; flipping this back to `true` restores the icon everywhere with no
 * other changes needed. */
const val SHOW_LOCK_ICON = false
