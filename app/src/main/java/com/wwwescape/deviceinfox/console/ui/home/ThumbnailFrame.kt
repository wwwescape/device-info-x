package com.wwwescape.deviceinfox.console.ui.home

import androidx.exifinterface.media.ExifInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// How a single image/video bubble is framed. Historically every one was a fixed 200dp square that
// crops to fill, which cuts the ends off a wide image (a screenshot, a panorama, an exported chart)
// and shows only the middle of a tall one. Images that are *clearly* wide or tall now get a frame
// that follows their proportions instead; everything else — near-square and typical camera photos
// (4:3 / 3:4), unknown sizes — keeps the square, so most of the chat looks exactly as before.

private val SQUARE_THUMBNAIL = DpSize(200.dp, 200.dp)

// Width/height ratios inside this band keep the square. It's symmetric (0.67 = 1 / 1.5) on purpose,
// so a 90-degree EXIF rotation, which flips a ratio to its reciprocal, can never move an image
// between "square" and "shaped" — and it keeps ordinary 4:3 / 3:4 camera photos exactly as they were.
private const val SQUARE_BAND_MIN_RATIO = 0.67f
private const val SQUARE_BAND_MAX_RATIO = 1.5f

// Beyond these, the picture is trimmed (Crop) rather than turned into a sliver: about 1:1.8 tall
// and 2:1 wide.
private const val MIN_RATIO = 0.55f
private const val MAX_RATIO = 2.0f

// Fit inside a bubble (max 280dp wide, minus its padding) without dominating the screen.
private val MAX_LANDSCAPE_WIDTH = 240.dp
private val MAX_PORTRAIT_HEIGHT = 300.dp

/** The frame for an image whose *displayed* size is [widthPx] x [heightPx]: the 200dp square when
 * the size is unknown or near-square, otherwise one that follows the proportions (clamped to
 * [MIN_RATIO]..[MAX_RATIO]). */
internal fun thumbnailSizeFor(widthPx: Int?, heightPx: Int?): DpSize {
    if (widthPx == null || heightPx == null || widthPx <= 0 || heightPx <= 0) return SQUARE_THUMBNAIL
    val ratio = widthPx.toFloat() / heightPx
    if (ratio in SQUARE_BAND_MIN_RATIO..SQUARE_BAND_MAX_RATIO) return SQUARE_THUMBNAIL
    val clamped = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
    return if (clamped > 1f) {
        DpSize(MAX_LANDSCAPE_WIDTH, MAX_LANDSCAPE_WIDTH / clamped)
    } else {
        DpSize(MAX_PORTRAIT_HEIGHT * clamped, MAX_PORTRAIT_HEIGHT)
    }
}

/** Whether the image file at [path] is stored rotated by 90 degrees (EXIF orientation 5-8), i.e.
 * shown with its width and height swapped. A phone's portrait photo is saved as landscape pixels
 * plus this flag; the stored `widthPx`/`heightPx` (from `BitmapFactory` bounds) ignore it, while
 * Coil applies it when drawing. False on any failure — including files with no EXIF at all (PNGs,
 * screenshots, exported charts) — which just means "trust the stored size". */
private fun exifSwapsDimensions(path: String): Boolean = try {
    when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_TRANSVERSE,
        ExifInterface.ORIENTATION_ROTATE_270,
        -> true
        else -> false
    }
} catch (e: Exception) {
    false
}

/** The frame for [attachment]'s single-image/video bubble. Decided up front from the stored
 * dimensions, so the bubble has its final height on the first frame and the chat list never
 * reflows when the picture finishes loading. Only an image that lands *outside* the square band
 * (screenshots, charts, panoramas, unusual photos) has its EXIF orientation checked, off the main
 * thread: if the file turns out to be rotated, the frame is corrected to the swapped proportions —
 * a rare, one-off change; the common outcome (no EXIF) leaves the first-frame size untouched. */
@Composable
internal fun rememberThumbnailSize(attachment: MessageAttachment): DpSize {
    val provisional = thumbnailSizeFor(attachment.widthPx, attachment.heightPx)
    val size by produceState(provisional, attachment.filePath, attachment.widthPx, attachment.heightPx) {
        value = provisional
        if (provisional == SQUARE_THUMBNAIL) return@produceState
        val rotated = withContext(Dispatchers.IO) { exifSwapsDimensions(attachment.filePath) }
        if (rotated) value = thumbnailSizeFor(attachment.heightPx, attachment.widthPx)
    }
    return size
}
