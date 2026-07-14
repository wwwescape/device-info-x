package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val MAX_ZOOM_SCALE = 5f
private const val DOUBLE_TAP_ZOOM_SCALE = 3f
private const val ZOOM_ANIMATION_DURATION_MS = 250

/** Zoom/pan state for [ZoomableBox] — shared by [ZoomableImage] and `PdfPageViewer` so every
 * viewer zooms identically. [scale]/[offset] are plain snapshot state written directly on every
 * gesture frame (1:1 with the finger, no animation in the way); the double-tap toggle and the
 * post-release fling run as a single cancellable [animationJob] that any new touch cancels, so
 * grabbing the content mid-animation just stops it where it is.
 *
 * The transform is applied with `graphicsLayer`'s default center transform origin, so a content
 * point `x` is drawn at `center + offset + (x - center) * scale`. Pan is clamped to the *fitted
 * content's* edges (not the container's), so a letterboxed photo or PDF page can't be dragged
 * into its own black bars — [contentIntrinsicSize] is what makes that possible; until it's known
 * the whole container is treated as content. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    private var contentIntrinsicSizeState by mutableStateOf(Size.Unspecified)
    private var containerSizeState by mutableStateOf(IntSize.Zero)

    /** The content's natural size (any unit — only the aspect ratio matters, since it's fitted
     * into the container the same way `ContentScale.Fit` does). Re-clamps the pan when it changes
     * (e.g. the image finishes loading after the user already zoomed), so the content's edges
     * are respected from then on. */
    var contentIntrinsicSize: Size
        get() = contentIntrinsicSizeState
        set(value) {
            if (value == contentIntrinsicSizeState) return
            contentIntrinsicSizeState = value
            offset = clamp(scale, offset)
        }

    /** The zoomable area's size in px — also the size of the viewport a caller may want to
     * re-render at full resolution (see `PdfPageViewer`'s sharp tile). Re-clamps the pan when it
     * changes (e.g. rotation while zoomed), so no black strip is left until the next touch. */
    var containerSize: IntSize
        get() = containerSizeState
        internal set(value) {
            if (value == containerSizeState) return
            containerSizeState = value
            offset = clamp(scale, offset)
        }

    private var isInteracting by mutableStateOf(false)
    private var isAnimating by mutableStateOf(false)
    private var animationJob: Job? = null

    /** No finger down and no double-tap/fling animation running — the transform is at rest. */
    val isSettled: Boolean
        get() = !isInteracting && !isAnimating

    private val center: Offset
        get() = Offset(containerSize.width / 2f, containerSize.height / 2f)

    private fun fittedContentSize(): Size {
        val container = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
        val intrinsic = contentIntrinsicSize
        if (intrinsic == Size.Unspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) return container
        val fit = min(container.width / intrinsic.width, container.height / intrinsic.height)
        return Size(intrinsic.width * fit, intrinsic.height * fit)
    }

    private fun clamp(targetScale: Float, desired: Offset): Offset {
        if (targetScale <= 1f) return Offset.Zero
        val content = fittedContentSize()
        val maxX = max(0f, (content.width * targetScale - containerSize.width) / 2f)
        val maxY = max(0f, (content.height * targetScale - containerSize.height) / 2f)
        return Offset(desired.x.coerceIn(-maxX, maxX), desired.y.coerceIn(-maxY, maxY))
    }

    /** Offset that keeps the content point under [centroid] fixed while scaling by
     * [newScale]/[scale] — i.e. pinch zooms around the fingers, not the view center. */
    private fun offsetForZoomAround(centroid: Offset, newScale: Float, pan: Offset): Offset {
        val fromCenter = centroid - center
        return (offset - fromCenter) * (newScale / scale) + fromCenter + pan
    }

    internal fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
        isAnimating = false
    }

    private fun launchAnimation(scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> Unit) {
        stopAnimation()
        isAnimating = true
        val job = scope.launch { block() }
        animationJob = job
        job.invokeOnCompletion { if (animationJob === job) isAnimating = false }
    }

    internal fun onGestureStart() {
        stopAnimation()
        isInteracting = true
    }

    /** [velocity] is null when the touch never engaged as a zoom/pan (a tap, or a 1x swipe the
     * pager took). */
    internal fun onGestureEnd(scope: kotlinx.coroutines.CoroutineScope, velocity: Offset?) {
        isInteracting = false
        if (velocity != null) fling(scope, velocity)
    }

    internal fun applyGesture(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM_SCALE)
        val newOffset = offsetForZoomAround(centroid, newScale, pan)
        scale = newScale
        offset = clamp(newScale, newOffset)
    }

    /** Lets a flick keep gliding after release, decelerating to rest and stopping at the edges. */
    private fun fling(scope: kotlinx.coroutines.CoroutineScope, velocity: Offset) {
        if (scale <= 1f) return
        launchAnimation(scope) {
            AnimationState(Offset.VectorConverter, offset, velocity)
                .animateDecay(exponentialDecay(frictionMultiplier = 1.5f)) {
                    val clamped = clamp(scale, value)
                    offset = clamped
                    // Both axes pinned against an edge — nothing left to glide.
                    if (clamped.x != value.x && clamped.y != value.y) cancelAnimation()
                }
        }
    }

    /** 1x → [DOUBLE_TAP_ZOOM_SCALE] centered on [tapPoint], or anything zoomed → back to 1x. */
    internal fun toggleDoubleTapZoom(scope: kotlinx.coroutines.CoroutineScope, tapPoint: Offset) {
        val startScale = scale
        val startOffset = offset
        val targetScale: Float
        val targetOffset: Offset
        if (scale > 1f) {
            targetScale = 1f
            targetOffset = Offset.Zero
        } else {
            targetScale = DOUBLE_TAP_ZOOM_SCALE
            targetOffset = clamp(targetScale, offsetForZoomAround(tapPoint, targetScale, Offset.Zero))
        }
        launchAnimation(scope) {
            animate(0f, 1f, animationSpec = tween(ZOOM_ANIMATION_DURATION_MS)) { t, _ ->
                scale = lerp(startScale, targetScale, t)
                offset = lerp(startOffset, targetOffset, t)
            }
        }
    }
}

@Composable
fun rememberZoomState(vararg keys: Any?): ZoomState = remember(*keys) { ZoomState() }

/** Pinch-to-zoom, double-tap to toggle 1x ↔ [DOUBLE_TAP_ZOOM_SCALE] around the tap point, and
 * pan with fling once zoomed — Google Photos-style. Shared by [ZoomableImage] and
 * `PdfPageViewer`.
 *
 * **Gestures are read on this outer, untransformed Box; the transform is only applied to the
 * inner content Box.** That split is the whole fix for the old "panning feels slow" bug: the
 * previous implementation put `pointerInput` *after* `graphicsLayer` on the same element, so
 * touch deltas arrived in the scaled coordinate space — divided by the zoom level — and at 3x the
 * image moved a third as far as the finger. The transform is also applied in the `graphicsLayer`
 * *lambda*, so a gesture frame only re-draws the layer instead of recomposing.
 *
 * Deliberately not built on the stock `detectTransformGestures`: that detector consumes every
 * single-finger drag once past touch slop with no way to opt out, which would pan the content at
 * 1x and starve an ancestor `HorizontalPager` of the swipe it needs for next/previous.
 * [detectZoomAndConditionalPan] only consumes a single-finger drag once already zoomed in; a real
 * pinch (2+ fingers) always engages so it can zoom up from 1x. Double-tap lives in its own
 * `pointerInput` — a tap never crosses that detector's touch-slop gate, so they never fight. */
@Composable
fun ZoomableBox(
    state: ZoomState,
    modifier: Modifier = Modifier,
    onZoomedChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnZoomedChanged by rememberUpdatedState(onZoomedChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.scale > 1f }.distinctUntilChanged().collect { currentOnZoomedChanged(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.containerSize = it }
            .pointerInput(state) {
                detectZoomAndConditionalPan(
                    getScale = { state.scale },
                    onGestureStart = state::onGestureStart,
                    onGesture = state::applyGesture,
                    onGestureEnd = { velocity -> state.onGestureEnd(scope, velocity) },
                )
            }
            .pointerInput(state) {
                detectTapGestures(onDoubleTap = { tapPoint -> state.toggleDoubleTapZoom(scope, tapPoint) })
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
            content = content,
        )
    }
}

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomedChanged: (Boolean) -> Unit = {},
) {
    val state = rememberZoomState(model)
    ZoomableBox(state = state, modifier = modifier, onZoomedChanged = onZoomedChanged) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { success -> state.contentIntrinsicSize = success.painter.intrinsicSize },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Same shape as [androidx.compose.foundation.gestures.detectTransformGestures], but a
 * single-finger drag only "engages" (and is consumed) once [getScale] is already above 1x; a
 * genuine multi-finger pinch always engages so it can zoom up from 1x in the first place. Once
 * engaged, stays engaged for the rest of that one continuous gesture (matches the stock
 * detector's own latch-until-release behavior) — an unconsumed drag falls through untouched to
 * whatever ancestor wants it, e.g. a `HorizontalPager`'s own swipe-to-next/previous.
 *
 * Also tracks the centroid's velocity so [onGestureEnd] can fling. The tracker resets whenever
 * the finger count changes, since the centroid jumps when a finger is added or lifted.
 * [onGestureStart]/[onGestureEnd] bracket *every* touch (engaged or not, and even if this
 * coroutine is cancelled mid-touch) so [ZoomState.isSettled] is always accurate. */
private suspend fun PointerInputScope.detectZoomAndConditionalPan(
    getScale: () -> Float,
    onGestureStart: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: (velocity: Offset?) -> Unit,
) {
    awaitEachGesture {
        var zoomAccum = 1f
        var panAccum = Offset.Zero
        var engaged = false
        val touchSlop = viewConfiguration.touchSlop
        val velocityTracker = VelocityTracker()
        var lastPointerCount = 0

        awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        var ended = false
        try {
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val pressedCount = event.changes.count { it.pressed }
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
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (pressedCount != lastPointerCount) {
                            velocityTracker.resetTracking()
                            lastPointerCount = pressedCount
                        }
                        if (pressedCount > 0) {
                            val time = event.changes.first().uptimeMillis
                            velocityTracker.addPosition(time, event.calculateCentroid(useCurrent = true))
                        }
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            onGesture(centroid, panChange, zoomChange)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (!canceled && event.changes.any { it.pressed })

            ended = true
            onGestureEnd(
                if (engaged) {
                    val velocity = velocityTracker.calculateVelocity()
                    Offset(velocity.x, velocity.y)
                } else {
                    null
                },
            )
        } finally {
            if (!ended) onGestureEnd(null)
        }
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
