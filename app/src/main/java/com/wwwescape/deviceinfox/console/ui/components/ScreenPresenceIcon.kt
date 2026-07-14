package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.ui.settings.ServerOnlineColor

/** Amber — the "somewhere in the app, but not on this specific screen" middle tier, sitting
 * between [ServerOnlineColor] (green, on-screen) and [MaterialTheme.colorScheme.error] (red, not
 * in the app at all). No existing color in this app filled that role, since every presence icon
 * before this was a plain 2-state red/green boolean. */
val PresenceAwayColor = Color(0xFFF9A825)

/** Red = not in the app at all; amber = in the app, but not on this specific screen; green = on
 * this specific screen right now. Built for the Call Room's own status row (where the distinction
 * is load-bearing: it's what tells you *why* your partner isn't picking up), and also applied to
 * Game Room's pre-existing presence icon — that one already had both signals available
 * server-side (general online + per-screen presence), it just collapsed them into one boolean
 * before this. Deliberately not applied to Live Location's own red/green icon — that one measures
 * *sharing* status, not screen presence, a different question with no natural "amber" to map to. */
enum class ScreenPresenceTier { NOT_IN_APP, IN_APP, ON_SCREEN }

fun screenPresenceTier(isOnline: Boolean, isOnScreen: Boolean): ScreenPresenceTier = when {
    isOnScreen -> ScreenPresenceTier.ON_SCREEN
    isOnline -> ScreenPresenceTier.IN_APP
    else -> ScreenPresenceTier.NOT_IN_APP
}

@Composable
fun screenPresenceTierColor(tier: ScreenPresenceTier): Color = when (tier) {
    ScreenPresenceTier.ON_SCREEN -> ServerOnlineColor
    ScreenPresenceTier.IN_APP -> PresenceAwayColor
    ScreenPresenceTier.NOT_IN_APP -> MaterialTheme.colorScheme.error
}

/** Reuses the same two Live-Location-style glyphs every presence icon in this app already uses
 * (`ic_live_location_online`/`_offline`) rather than adding a third asset for the amber tier —
 * [ScreenPresenceTier.IN_APP] just tints the "online" glyph amber instead of swapping shapes. */
@Composable
fun ScreenPresenceIcon(tier: ScreenPresenceTier, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(
            if (tier == ScreenPresenceTier.NOT_IN_APP) R.drawable.ic_live_location_offline else R.drawable.ic_live_location_online,
        ),
        contentDescription = contentDescription,
        tint = screenPresenceTierColor(tier),
        modifier = modifier,
    )
}
