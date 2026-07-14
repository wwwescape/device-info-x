package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import java.io.File
import kotlin.math.abs

/** Pinch-to-zoom image. Deliberately not built on the stock `detectTransformGestures` the 3
 * separate zoom implementations this replaces (`ImagePreviewDialog`, `VaultItemViewerDialog`,
 * and Messages' now-removed inline copy) all used verbatim — that detector consumes every
 * single-finger drag once past touch slop with no way to opt out, which is exactly what let a
 * plain hold-and-drag pan the image off-frame at 1x zoom, and also starved any ancestor
 * `HorizontalPager` of the same drag it needed for swipe-to-next/previous. [detectZoomAndConditionalPan]
 * below only consumes a single-finger drag once already zoomed in — a real pinch (2+ fingers)
 * always engages regardless, so it can zoom up from 1x. Pan is clamped to the image's own bounds
 * at the current scale so it can never be dragged out of frame, and snaps back to centered the
 * moment the pinch releases back to 1x. */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomedChanged: (Boolean) -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale > 1f) { onZoomedChanged(scale > 1f) }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .pointerInput(Unit) {
                detectZoomAndConditionalPan(getScale = { scale }) { pan, zoom ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale <= 1f) {
                        Offset.Zero
                    } else {
                        val maxX = containerSize.width * (newScale - 1f) / 2f
                        val maxY = containerSize.height * (newScale - 1f) / 2f
                        Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                }
            },
    )
}

/** Same shape as [androidx.compose.foundation.gestures.detectTransformGestures], but a
 * single-finger drag only "engages" (and is consumed) once [getScale] is already above 1x; a
 * genuine multi-finger pinch always engages so it can zoom up from 1x in the first place. Once
 * engaged, stays engaged for the rest of that one continuous gesture (matches the stock
 * detector's own latch-until-release behavior) — an unconsumed drag falls through untouched to
 * whatever ancestor wants it, e.g. a `HorizontalPager`'s own swipe-to-next/previous. */
private suspend fun PointerInputScope.detectZoomAndConditionalPan(
    getScale: () -> Float,
    onGesture: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var zoomAccum = 1f
        var panAccum = Offset.Zero
        var engaged = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val isMultiTouch = event.changes.size > 1
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!engaged) {
                    zoomAccum *= zoomChange
                    panAccum += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val pastSlop = abs(1 - zoomAccum) * centroidSize > touchSlop || panAccum.getDistance() > touchSlop
                    engaged = pastSlop && (isMultiTouch || getScale() > 1f)
                }

                if (engaged) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

/** The video half of a media page — loading spinner until [ensureDownloaded] resolves, then a
 * plain Media3 [PlayerView]. No zoom concept (unlike [ZoomableImage]), so it never blocks an
 * ancestor `HorizontalPager`'s swipe. Decoupled from any particular attachment/item type (just an
 * id to key the download-once effect on, and a path/downloader) so it's shared verbatim by
 * `MediaPagerDialog` (Messages/Shared Media) and `VaultItemViewerDialog` (Safe Locker) rather than
 * each keeping its own copy of the same ExoPlayer setup/teardown dance. */
@Composable
fun VideoPlayerPage(parentId: String, initialLocalPath: String?, ensureDownloaded: suspend () -> String?) {
    var localPath by remember(parentId) { mutableStateOf(initialLocalPath) }
    LaunchedEffect(parentId) {
        if (localPath == null) localPath = ensureDownloaded()
    }
    val context = LocalContext.current
    val currentLocalPath = localPath
    if (currentLocalPath == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.console_home_loading_label),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        val exoPlayer = remember(currentLocalPath) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(currentLocalPath))))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
