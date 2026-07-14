package com.wwwescape.deviceinfox.console.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_BITMAP_DIMENSION_PX = 2048

/** How long a zoom must stay still before the sharp tile is rendered — long enough not to burn
 * renders on brief pauses mid-gesture, short enough to feel immediate. */
private const val SHARP_TILE_SETTLE_DELAY_MS = 150L

/** Serializes every [PdfRenderer] access. [PdfRenderer] only allows one open [PdfRenderer.Page]
 * at a time for the whole renderer and isn't thread-safe, and with two render paths (the base
 * page and the sharp tile) plus closing, a lock is the only way to guarantee that. Rendering runs
 * on [Dispatchers.IO]; `page.render()` can't be interrupted, so cancelling a caller only takes
 * effect once the current render returns — callers drop stale results via normal coroutine
 * cancellation (`withContext` throws on return when the caller was cancelled meanwhile). */
private class SerialPdfRenderer(private val renderer: PdfRenderer) {
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    /** Null once [close] has run. */
    suspend fun <T> withPage(index: Int, block: (PdfRenderer.Page) -> T): T? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock null
            val page = renderer.openPage(index)
            try {
                block(page)
            } finally {
                page.close()
            }
        }
    }

    /** Non-blocking: waits for any in-flight render to finish under the lock, then closes — never
     * closes the renderer out from under a render still running on another thread. */
    fun close() {
        CoroutineScope(Dispatchers.IO).launch(NonCancellable) {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    renderer.close()
                }
            }
        }
    }
}

/** The zoom snapshot a sharp tile is rendered for. */
private data class SharpTileRequest(val settled: Boolean, val scale: Float, val offset: Offset, val container: IntSize)

/** Renders exactly what's on screen at [request]'s zoom into a container-sized bitmap, at full
 * resolution — page points map straight to screen pixels via [Matrix], so memory stays at one
 * screen's worth however far in the user zooms. The mapping mirrors [ZoomableBox]'s layout of the
 * base bitmap ([ContentScale.Fit] of [baseWidth]×[baseHeight], then `graphicsLayer` scale around
 * the center plus offset), so the tile lines up with the base page pixel-for-pixel. Outside the
 * page stays transparent; the page area gets a white background first, like the base render. */
private fun renderVisibleRegion(page: PdfRenderer.Page, baseWidth: Int, baseHeight: Int, request: SharpTileRequest): Bitmap {
    val containerWidth = request.container.width.toFloat()
    val containerHeight = request.container.height.toFloat()
    val fit = min(containerWidth / baseWidth, containerHeight / baseHeight)
    val fittedWidth = baseWidth * fit
    val fittedHeight = baseHeight * fit
    val left = (containerWidth - fittedWidth) / 2f
    val top = (containerHeight - fittedHeight) / 2f
    val centerX = containerWidth / 2f
    val centerY = containerHeight / 2f
    val scale = request.scale

    val matrix = Matrix().apply {
        setScale(fittedWidth / page.width * scale, fittedHeight / page.height * scale)
        postTranslate(
            centerX + request.offset.x + (left - centerX) * scale,
            centerY + request.offset.y + (top - centerY) * scale,
        )
    }
    val bitmap = Bitmap.createBitmap(request.container.width, request.container.height, Bitmap.Config.ARGB_8888)
    val pageRect = RectF(0f, 0f, page.width.toFloat(), page.height.toFloat())
    matrix.mapRect(pageRect)
    Canvas(bitmap).drawRect(pageRect, Paint().apply { color = android.graphics.Color.WHITE })
    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bitmap
}

/** Renders a local PDF file page-by-page via the platform's own [PdfRenderer] — no third-party
 * library. Same lazy-download shape as [VideoPlayerPage] ([parentId]/[initialLocalPath]/
 * [ensureDownloaded]), shared verbatim by the standalone `PdfPreviewDialog` (Messages/Calendar)
 * and inline inside `VaultItemViewerDialog` (Safe Locker) — same "content composable shared,
 * dialog shell isn't" split [VideoPlayerPage]/[ZoomableImage] already use.
 *
 * Deliberately renders only the current page, never prefetches neighbors. Every renderer access
 * goes through [SerialPdfRenderer]'s lock (see its doc comment). Pinch/double-tap zoom and pan come
 * from the shared [ZoomableBox] (reset to 1x on every page turn; [onZoomedChanged] lets an outer
 * pager stop swiping while zoomed, the same way [ZoomableImage] does).
 *
 * **Two layers, so zooming stays smooth *and* ends up sharp**: the base layer is one fit-to-screen
 * bitmap (capped at [MAX_BITMAP_DIMENSION_PX]) that [ZoomableBox] scales during gestures — always
 * ready, slightly soft when zoomed far in. Once the zoom settles (no finger down, no fling or
 * double-tap animation, then [SHARP_TILE_SETTLE_DELAY_MS]), a screen-sized sharp tile of just the
 * visible region is rendered and drawn untransformed on top ([renderVisibleRegion]). Any new
 * gesture hides it immediately and cancels a render in flight; skipped entirely while the base
 * bitmap is already at least as dense as the screen.
 *
 * Page navigation is Prev/Next arrows + a counter, not swipeable — deliberate, so this never has
 * to fight the outer swipe-between-items `HorizontalPager` Safe Locker's viewer already has. */
@Composable
fun PdfPageViewer(
    parentId: String,
    initialLocalPath: String?,
    ensureDownloaded: suspend () -> String?,
    onZoomedChanged: (Boolean) -> Unit = {},
) {
    var localPath by remember(parentId) { mutableStateOf(initialLocalPath) }
    LaunchedEffect(parentId) {
        if (localPath == null) localPath = ensureDownloaded()
    }
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
        return
    }

    var renderer by remember(currentLocalPath) { mutableStateOf<SerialPdfRenderer?>(null) }
    var openError by remember(currentLocalPath) { mutableStateOf(false) }
    var currentPageIndex by remember(currentLocalPath) { mutableIntStateOf(0) }

    DisposableEffect(currentLocalPath) {
        var pfd: ParcelFileDescriptor? = null
        val opened = try {
            pfd = ParcelFileDescriptor.open(File(currentLocalPath), ParcelFileDescriptor.MODE_READ_ONLY)
            // PdfRenderer takes ownership of pfd once construction succeeds — its own close()
            // closes the fd too, so pfd is only ever closed manually below, on failure.
            PdfRenderer(pfd)
        } catch (e: IOException) {
            pfd?.close()
            null
        }
        if (opened == null || opened.pageCount == 0) {
            opened?.close()
            openError = true
        } else {
            renderer = SerialPdfRenderer(opened)
        }
        onDispose { renderer?.close() }
    }

    if (openError) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.console_home_pdf_open_error),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val currentRenderer = renderer
    if (currentRenderer == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val density = LocalDensity.current
    var pageBitmap by remember(currentLocalPath, currentPageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentLocalPath, currentPageIndex, currentRenderer) {
        pageBitmap = null
        pageBitmap = currentRenderer.withPage(currentPageIndex) { page ->
            // Density is a multiple of the 160dpi baseline; PDF points are 72dpi — converting
            // through the device's actual dpi gives a device-sharp render.
            val scale = (density.density * 160f) / 72f
            var targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
            var targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
            val longestSide = maxOf(targetWidth, targetHeight)
            if (longestSide > MAX_BITMAP_DIMENSION_PX) {
                val capScale = MAX_BITMAP_DIMENSION_PX.toFloat() / longestSide
                targetWidth = (targetWidth * capScale).toInt().coerceAtLeast(1)
                targetHeight = (targetHeight * capScale).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bitmap = pageBitmap
        if (bitmap == null) {
            // A page still rendering has no zoom — keeps an outer pager's swipe enabled meanwhile.
            LaunchedEffect(Unit) { onZoomedChanged(false) }
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            // Keyed on the page, so turning the page always starts back at 1x.
            val zoomState = rememberZoomState(currentLocalPath, currentPageIndex)
            zoomState.contentIntrinsicSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
            ZoomableBox(state = zoomState, onZoomedChanged = onZoomedChanged) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            var sharpTile by remember(zoomState) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(zoomState, bitmap, currentRenderer) {
                // Observed via snapshotFlow rather than read in composition, so gesture frames
                // still don't recompose this viewer. collectLatest cancels a pending/in-flight
                // render the moment anything changes, so a stale tile is never shown.
                snapshotFlow {
                    SharpTileRequest(zoomState.isSettled, zoomState.scale, zoomState.offset, zoomState.containerSize)
                }.collectLatest { request ->
                    sharpTile = null
                    if (!request.settled || request.container.width <= 0 || request.container.height <= 0) return@collectLatest
                    // Base bitmap already at least as dense as the screen at this zoom — nothing to gain.
                    val fit = min(request.container.width.toFloat() / bitmap.width, request.container.height.toFloat() / bitmap.height)
                    if (fit * request.scale <= 1f) return@collectLatest
                    delay(SHARP_TILE_SETTLE_DELAY_MS)
                    sharpTile = currentRenderer.withPage(currentPageIndex) { page ->
                        renderVisibleRegion(page, bitmap.width, bitmap.height, request)
                    }?.asImageBitmap()
                }
            }
            // No pointer input of its own, so touches pass straight through to ZoomableBox below.
            sharpTile?.let { tile ->
                Image(
                    bitmap = tile,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = { currentPageIndex-- }, enabled = currentPageIndex > 0) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.console_pdf_previous_page), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.console_pdf_page_counter, currentPageIndex + 1, currentRenderer.pageCount),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = { currentPageIndex++ },
                enabled = currentPageIndex < currentRenderer.pageCount - 1,
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.console_pdf_next_page), tint = Color.White)
            }
        }
    }
}
