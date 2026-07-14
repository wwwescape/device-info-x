package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.ui.graphics.Color

/** Fixed literals (not theme-derived) for the Settings gear icon's badge dot, shared by every tab
 * that renders one (Home/Messages, Calendar, Period Tracker, Safe Locker) — see each tab's own
 * `isLiveLocationActive`/`isUpdateAvailable` fields. [LiveLocationDotColor] takes precedence over
 * [UpdateAvailableDotColor] whenever both are true, per the Live Location Sharing TODO's own
 * decided color scheme. */
val LiveLocationDotColor = Color(0xFF155DFC)
val UpdateAvailableDotColor = Color(0xFFC81CDE)

/** [com.wwwescape.deviceinfox.console.ui.settings.UpdateAvailableCard]'s background — same
 * decided color scheme as the dots above. Fixed (not theme-derived), so its icon/text also need a
 * fixed, high-contrast pair rather than `MaterialTheme.colorScheme.onPrimaryContainer` (which
 * assumes a theme-derived container, and isn't guaranteed to read well against this specific
 * literal pink in both light and dark theme). */
val UpdateAvailableCardColor = Color(0xFFE797F2)
val UpdateAvailableCardContentColor = Color(0xFF4A1152)
