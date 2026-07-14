package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Initials-in-a-circle + a small corner presence badge — the same underlying "who is this, and
 * are they around" visual that recurred independently across all three Stitch mockup batches
 * reviewed for the Live Location/Call/Game Room redesigns (TODOS.md), replacing three separate
 * one-off implementations with one shared component. Self renders as a solid primary-filled
 * circle, partner as a neutral `surfaceContainerHighest`-filled one — the same violet/gray self/
 * partner distinction Game Room's own avatar originally established, kept as the shared default.
 * Initials, not a photo — this app shows no profile pictures anywhere in these three features.
 * [presenceBadge] is left entirely to the caller (a plain boolean icon for a simple on/off signal,
 * [ScreenPresenceIcon] for the three-tier green/amber/red one) rather than baked in here, since
 * each existing status row already picks its own presence representation. */
@Composable
fun PresenceAvatar(
    name: String,
    isSelf: Boolean,
    presenceBadge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    badgeSize: Dp = 18.dp,
    badgeBackgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.initialsFromDisplayName(),
                style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(badgeSize)
                .clip(CircleShape)
                .background(badgeBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            presenceBadge()
        }
    }
}

/** First letter of the first two words in a display name ("Alex Rivera" -> "AR") — derived from a
 * plain display-name string since none of [PresenceAvatar]'s three call sites (Live Location, Call
 * Room, Game Room) have separate first/last name fields available at the UI-state layer, only a
 * resolved display name. */
fun String.initialsFromDisplayName(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val first = words.getOrNull(0)?.firstOrNull()?.uppercaseChar()
    val second = words.getOrNull(1)?.firstOrNull()?.uppercaseChar()
    return listOfNotNull(first, second).joinToString("").ifEmpty { "?" }
}
