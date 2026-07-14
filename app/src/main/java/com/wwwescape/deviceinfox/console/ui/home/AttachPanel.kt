package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R

// Was 140.dp, carried over unchanged from the old single-row panel's own total height — applied
// per-row now that there are 2 rows, that left each row's actual content (icon + label, ~92.dp
// tall) vertically centered inside far more height than it needed, reading as a large dead gap
// between the two rows. 100.dp is close to that natural content height, just enough slack to keep
// the row from feeling cramped.
private val ATTACH_OPTION_ROW_HEIGHT = 100.dp
private val ATTACH_ICON_SIZE = 56.dp

/** Toggled by the "+" icon in [ComposerBar], replacing the software keyboard the same way
 * [EmojiPickerPanel] does — see that composable's own doc comment for why a fixed height matters
 * for that swap. WhatsApp-style: rows of circular icon buttons, centered, each labeled below —
 * 2 rows now that Poll/Location (the 5th/6th options) don't fit alongside Gallery/Camera/Video/
 * Document in one row; they wrap to a new row of their own, aligned under Gallery/Camera.
 * [onGalleryClick] already covers picking a video from the gallery too (the picker itself now
 * accepts both media types) — [onRecordVideoClick] is only the separate in-app "record a new
 * video" capture path, mirroring how [onCameraClick] is "take a new photo" rather than covering
 * gallery photos. */
@Composable
fun AttachPanel(
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRecordVideoClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onPollClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        AttachOptionsRow {
            AttachOption(
                icon = Icons.Rounded.PhotoLibrary,
                label = stringResource(R.string.console_home_attach_gallery_option),
                onClick = onGalleryClick,
            )
            AttachOption(
                icon = Icons.Rounded.PhotoCamera,
                label = stringResource(R.string.console_home_attach_camera_option),
                onClick = onCameraClick,
            )
            AttachOption(
                icon = Icons.Rounded.Videocam,
                label = stringResource(R.string.console_home_attach_video_option),
                onClick = onRecordVideoClick,
            )
            AttachOption(
                icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                label = stringResource(R.string.console_home_attach_document_option),
                onClick = onDocumentClick,
            )
        }
        // 2 invisible placeholders, same footprint as a real option — SpaceEvenly's spacing
        // depends on how many items are actually present in a row, so Poll/Location here
        // wouldn't otherwise land under Gallery/Camera (row 1's own first 2 slots) the way the
        // design calls for; this keeps both rows' slot positions identical regardless of row 2
        // having fewer real options than row 1.
        AttachOptionsRow {
            AttachOption(
                icon = Icons.Rounded.Poll,
                label = stringResource(R.string.console_home_attach_poll_option),
                onClick = onPollClick,
            )
            AttachOption(
                icon = Icons.Rounded.LocationOn,
                label = stringResource(R.string.console_home_attach_location_option),
                onClick = onLocationClick,
            )
            AttachOptionPlaceholder()
            AttachOptionPlaceholder()
        }
    }
}

@Composable
private fun AttachOptionsRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ATTACH_OPTION_ROW_HEIGHT),
        // SpaceEvenly rather than a fixed 48.dp gap — 4 items at a fixed gap that wide doesn't
        // reliably fit typical phone widths (4 × 56.dp icons + 3 × 48.dp gaps alone is already
        // 368.dp, before padding or labels), which was squeezing the last item (Document) enough
        // to wrap its label onto two lines. SpaceEvenly always divides whatever width actually is
        // available, so it can't overflow regardless of screen size.
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** Same footprint as a real [AttachOption] (icon circle + label) but fully invisible — see
 * [AttachOptionsRow]'s call site for why row 2 needs 3 of these. */
@Composable
private fun AttachOptionPlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Box(modifier = Modifier.size(ATTACH_ICON_SIZE))
        Text(text = "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}

/** All options share one color pair — `primaryContainer`/`onPrimaryContainer` (Gallery's
 * original color) — rather than each getting a different Material3 tonal role as before; see
 * the TODOS.md writeup this was built from for why that read as inconsistent. */
@Composable
private fun AttachOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ATTACH_ICON_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
