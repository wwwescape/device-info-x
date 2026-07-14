package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draws a diagonally-hatched circle, clipped to [radius] (defaults to the draw area's own
 * inscribed circle) around [center] — the shared "this is Today" fill used by both Calendar's
 * `DayCell` and Period Tracker's `PeriodDayCell`, in place of whatever solid circle a given state
 * would otherwise draw (Selected's `primaryContainer`, a period day's red, an intimate day's
 * blue, or a plain neutral color when nothing else applies). Callers keep every other stroke-only
 * marker (predicted/fertile's dashed rings, the logged-no-flow dot, etc.) drawing exactly as they
 * already do, layered on top of this instead of on top of a solid fill. */
fun DrawScope.drawHatchedCircle(
    color: Color,
    radius: Float = size.minDimension / 2f,
    center: Offset = this.center,
    lineSpacing: Dp = 3.dp,
    strokeWidth: Dp = 1.dp,
) {
    val circlePath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center = center, radius = radius))
    }
    clipPath(circlePath) {
        val spacingPx = lineSpacing.toPx()
        val strokeWidthPx = strokeWidth.toPx()
        val diameter = radius * 2f
        // Rotate the coordinate system 45° and sweep vertical lines across a square generously
        // larger than the circle's bounding box, so the hatch still fully covers the circle once
        // rotated back — simpler than computing each line's rotated endpoints by hand.
        rotate(degrees = 45f, pivot = center) {
            var x = center.x - diameter
            while (x <= center.x + diameter) {
                drawLine(
                    color = color,
                    start = Offset(x, center.y - diameter),
                    end = Offset(x, center.y + diameter),
                    strokeWidth = strokeWidthPx,
                )
                x += spacingPx
            }
        }
    }
}
