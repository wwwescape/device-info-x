package com.wwwescape.deviceinfox.console.ui.components

import android.graphics.Bitmap
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_BITMAP_DIMENSION_PX = 2048

/** Renders a local PDF file page-by-page via the platform's own [PdfRenderer] — no third-party
 * library. Same lazy-download shape as [VideoPlayerPage] ([parentId]/[initialLocalPath]/
 * [ensureDownloaded]), shared verbatim by the standalone `PdfPreviewDialog` (Messages/Calendar)
 * and inline inside `VaultItemViewerDialog` (Safe Locker) — same "content composable shared,
 * dialog shell isn't" split [VideoPlayerPage]/[ZoomableImage] already use.
 *
 * Deliberately renders only the current page, never prefetches neighbors: [PdfRenderer] only
 * allows one open [PdfRenderer.Page] at a time for the whole renderer, and isn't safe for
 * concurrent access — keying the render effect on the current page index means Compose cancels
 * any in-flight render before starting the next, which is what actually prevents two coroutines
 * ever holding a page open at once (no explicit lock needed, since open/render/close never
 * suspends mid-way). Page navigation is Prev/Next arrows + a counter, not swipeable —
 * deliberate, so this never has to fight the outer swipe-between-items `HorizontalPager` Safe
 * Locker's viewer already has. */
@Composable
fun PdfPageViewer(parentId: String, initialLocalPath: String?, ensureDownloaded: suspend () -> String?) {
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

    var renderer by remember(currentLocalPath) { mutableStateOf<PdfRenderer?>(null) }
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
            renderer = opened
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
        pageBitmap = withContext(Dispatchers.IO) {
            val page = currentRenderer.openPage(currentPageIndex)
            try {
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
            } finally {
                page.close()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bitmap = pageBitmap
        if (bitmap == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
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
