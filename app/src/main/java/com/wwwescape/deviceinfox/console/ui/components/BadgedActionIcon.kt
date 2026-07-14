package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A [base] icon with a smaller [badge] icon overlaid at its bottom-right, notched into the base
 * by a transparent gap that follows the badge's own outline (like Android's "Auto Delete" icon)
 * — built at draw time from the stock Material vectors rather than as bespoke drawables, so it
 * follows [tint] (the menu's own content color in light and dark themes) and stays crisp at any
 * size. The gap is genuinely erased inside an offscreen layer, so it works on any background. */
@Composable
fun BadgedActionIcon(
    base: ImageVector,
    badge: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val basePainter = rememberVectorPainter(base)
    val badgePainter = rememberVectorPainter(badge)
    val cutoutPaint = remember { Paint().apply { blendMode = BlendMode.DstOut } }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Canvas(
        modifier = modifier
            .then(semanticsModifier)
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val tintFilter = ColorFilter.tint(tint)
        with(basePainter) { draw(this@Canvas.size, colorFilter = tintFilter) }

        val badgeSize = this.size.minDimension * BADGE_FRACTION
        val badgeTopLeft = Offset(this.size.width - badgeSize, this.size.height - badgeSize)
        val badgeDrawSize = Size(badgeSize, badgeSize)

        // Outline-following cut-out: the badge glyph stamped at CUTOUT_SAMPLES points around a
        // ring of radius [gap] (plus once in place) inside a DstOut layer, which erases the bin
        // wherever it lands — an even gap that hugs the badge's own shape, not a circle.
        val gap = CUTOUT_GAP.toPx()
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, this.size), cutoutPaint) }
        translate(badgeTopLeft.x, badgeTopLeft.y) { with(badgePainter) { draw(badgeDrawSize) } }
        repeat(CUTOUT_SAMPLES) { i ->
            val angle = 2 * PI * i / CUTOUT_SAMPLES
            translate(badgeTopLeft.x + gap * cos(angle).toFloat(), badgeTopLeft.y + gap * sin(angle).toFloat()) {
                with(badgePainter) { draw(badgeDrawSize) }
            }
        }
        drawIntoCanvas { it.restore() }

        translate(badgeTopLeft.x, badgeTopLeft.y) {
            with(badgePainter) { draw(badgeDrawSize, colorFilter = tintFilter) }
        }
    }
}

/** "Undo Delete" — the bin with an undo arrow badge. */
@Composable
fun UndoDeleteIcon(contentDescription: String? = null, modifier: Modifier = Modifier) {
    BadgedActionIcon(Icons.Rounded.Delete, Icons.AutoMirrored.Rounded.Undo, contentDescription, modifier)
}

/** "Undo Delete (Soft)" — the bin with an eye badge: visible again, to this device only. */
@Composable
fun SoftUndoDeleteIcon(contentDescription: String? = null, modifier: Modifier = Modifier) {
    BadgedActionIcon(Icons.Rounded.Delete, Icons.Rounded.Visibility, contentDescription, modifier)
}

/** Badge edge as a fraction of the icon — large enough to read at 24dp, small enough that the bin
 * stays recognisable. */
private const val BADGE_FRACTION = 0.6f

/** Width of the transparent gap between the badge glyph and the bin. */
private val CUTOUT_GAP = 1.5.dp

/** Stamps around the gap ring — at 24dp, 16 keeps neighbouring stamps well under a pixel apart
 * at the gap's edge, so the cut reads as one smooth outline. */
private const val CUTOUT_SAMPLES = 16
