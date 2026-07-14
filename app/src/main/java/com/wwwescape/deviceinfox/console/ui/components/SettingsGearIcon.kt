package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R

/** The Settings gear icon shared by every tab (Home/Messages, Calendar, Period Tracker, Safe
 * Locker) — a boolean dot, never a count, so [BadgedBox]'s default empty [Badge] is exactly
 * right. Was duplicated as an inline `when` block in all 4 screens; factored out once cycling
 * (below) needed the same animation logic in each of those four places. */
@Composable
fun SettingsGearIcon(isLiveLocationActive: Boolean, isUpdateAvailable: Boolean, modifier: Modifier = Modifier) {
    val contentDescription = stringResource(R.string.console_settings_title)
    when {
        // Both true — cycles between the two colors rather than picking one, so neither signal
        // is silently hidden behind the other (previously live-location unconditionally won).
        isLiveLocationActive && isUpdateAvailable -> {
            BadgedBox(badge = { Badge(containerColor = rememberCyclingDotColor(LiveLocationDotColor, UpdateAvailableDotColor)) }) {
                Icon(Icons.Rounded.Settings, contentDescription = contentDescription, modifier = modifier)
            }
        }
        isLiveLocationActive -> {
            BadgedBox(badge = { Badge(containerColor = LiveLocationDotColor) }) {
                Icon(Icons.Rounded.Settings, contentDescription = contentDescription, modifier = modifier)
            }
        }
        isUpdateAvailable -> {
            BadgedBox(badge = { Badge(containerColor = UpdateAvailableDotColor) }) {
                Icon(Icons.Rounded.Settings, contentDescription = contentDescription, modifier = modifier)
            }
        }
        else -> Icon(Icons.Rounded.Settings, contentDescription = contentDescription, modifier = modifier)
    }
}

/** Smooth back-and-forth blend between the two dot colors — same [rememberInfiniteTransition]
 * mechanism [PulsatingDot][com.wwwescape.deviceinfox.console.ui.settings.PulsatingDot] and
 * `UpdateAvailableCard`'s own alpha pulse already use elsewhere in Settings, just animating a
 * `Color` interpolation instead of scale/alpha. `RepeatMode.Reverse` means it crossfades A→B→A
 * continuously rather than snapping back. */
@Composable
private fun rememberCyclingDotColor(colorA: Color, colorB: Color): Color {
    val transition = rememberInfiniteTransition(label = "settingsDotCycle")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "settingsDotCycleFraction",
    )
    return lerp(colorA, colorB, fraction)
}
