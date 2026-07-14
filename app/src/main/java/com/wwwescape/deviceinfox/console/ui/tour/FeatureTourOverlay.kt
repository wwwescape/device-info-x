package com.wwwescape.deviceinfox.console.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.tour.featureTourCoordinatorEntryPoint

private val CUTOUT_PADDING = 6.dp
private val CUTOUT_CORNER_RADIUS = 12.dp
private val TOOLTIP_MARGIN = 16.dp
private val TOOLTIP_MAX_WIDTH = 260.dp
private val ARROW_SIZE = 8.dp

/**
 * Single full-screen coach-mark overlay, mounted once (see `ConsoleNavHost`) above every screen
 * the console can show — renders nothing when [com.wwwescape.deviceinfox.console.data.tour.FeatureTourCoordinator.activeTour]
 * is null, otherwise a scrim with a hole punched around the active target, a tooltip bubble with
 * one line of copy and a close button, and a small arrow pointing at the target.
 *
 * **Dismissible only via the tooltip's own close (×) button, and nothing else on screen is
 * interactive while a tour is up** — not the scrim, not the highlighted target itself. Five
 * [ScrimBlockRegion]s cover the entire screen between them: four tiling everywhere *except* the
 * cutout (so the rest of the screen stays inert, matching the scroll-lock
 * `rememberFeatureTourScrollControl` already applies) plus a fifth sized to the cutout itself —
 * unlike the visual scrim, which is punched with an actual hole there, this fifth region draws
 * nothing at all, so the target stays exactly as visible as it already is; it only blocks input,
 * preventing a tap on the spotlighted target from firing its real action (toggling a setting,
 * sending the online ping, …) during the tour. None of the five ever call [dismiss] — only
 * [TourTooltip]'s own close button does.
 */
@Composable
fun FeatureTourOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coordinator = remember(context) { context.featureTourCoordinatorEntryPoint() }
    val activeTour by coordinator.activeTour.collectAsStateWithLifecycle()
    val tour = activeTour ?: return
    val dismiss = { coordinator.dismiss(tour.tourKey) }

    val density = LocalDensity.current
    val cutoutPaddingPx = with(density) { CUTOUT_PADDING.toPx() }
    val cutout = Rect(
        left = tour.boundsInRoot.left - cutoutPaddingPx,
        top = tour.boundsInRoot.top - cutoutPaddingPx,
        right = tour.boundsInRoot.right + cutoutPaddingPx,
        bottom = tour.boundsInRoot.bottom + cutoutPaddingPx,
    )
    val cornerRadiusPx = with(density) { CUTOUT_CORNER_RADIUS.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val cutoutTopDp = with(density) { cutout.top.toDp() }
        val cutoutBottomDp = with(density) { cutout.bottom.toDp() }
        val cutoutLeftDp = with(density) { cutout.left.toDp() }
        val cutoutRightDp = with(density) { cutout.right.toDp() }

        // Visual scrim with a punched hole — draw-only, no pointer input of its own, so it never
        // blocks anything on its own. graphicsLayer(alpha < 1f) forces its own compositing layer
        // so BlendMode.Clear only clears within this Canvas, not the whole screen behind it.
        Canvas(modifier = Modifier.matchParentSize().graphicsLayer(alpha = 0.99f)) {
            drawRect(color = Color.Black.copy(alpha = 0.6f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(cutout.left, cutout.top),
                size = Size(cutout.width, cutout.height),
                cornerRadius = CornerRadius(cornerRadiusPx),
                blendMode = BlendMode.Clear,
            )
        }

        // Four regions tiling everywhere EXCEPT the cutout, plus a fifth over the cutout itself
        // (below) — together the five cover the whole screen, blocking every tap from reaching
        // whatever's behind them, none of them dismissing on tap.
        ScrimBlockRegion(
            Modifier.fillMaxWidth().height(cutoutTopDp.coerceAtLeast(0.dp)).align(Alignment.TopStart),
            tour.tourKey,
        )
        ScrimBlockRegion(
            Modifier.fillMaxWidth().height((screenHeightDp - cutoutBottomDp).coerceAtLeast(0.dp)).align(Alignment.BottomStart),
            tour.tourKey,
        )
        ScrimBlockRegion(
            Modifier
                .offset(x = 0.dp, y = cutoutTopDp)
                .width(cutoutLeftDp.coerceAtLeast(0.dp))
                .height((cutoutBottomDp - cutoutTopDp).coerceAtLeast(0.dp)),
            tour.tourKey,
        )
        ScrimBlockRegion(
            Modifier
                .offset(x = cutoutRightDp, y = cutoutTopDp)
                .width((screenWidthDp - cutoutRightDp).coerceAtLeast(0.dp))
                .height((cutoutBottomDp - cutoutTopDp).coerceAtLeast(0.dp)),
            tour.tourKey,
        )
        // The cutout itself — draws nothing (the visual scrim already punches a real hole here
        // via BlendMode.Clear above), so the spotlighted target stays exactly as visible as it
        // already was; this only exists to intercept taps that would otherwise reach the real
        // target underneath and fire its actual action mid-tour.
        ScrimBlockRegion(
            Modifier
                .offset(x = cutoutLeftDp, y = cutoutTopDp)
                .width((cutoutRightDp - cutoutLeftDp).coerceAtLeast(0.dp))
                .height((cutoutBottomDp - cutoutTopDp).coerceAtLeast(0.dp)),
            tour.tourKey,
        )

        // Tooltip — below the cutout if there's more room there, else above.
        val tooltipWidth = (screenWidthDp - TOOLTIP_MARGIN * 2).coerceAtMost(TOOLTIP_MAX_WIDTH)
        val cutoutCenterX = (cutoutLeftDp + cutoutRightDp) / 2
        val tooltipX = (cutoutCenterX - tooltipWidth / 2)
            .coerceIn(TOOLTIP_MARGIN, (screenWidthDp - tooltipWidth - TOOLTIP_MARGIN).coerceAtLeast(TOOLTIP_MARGIN))
        val arrowX = (cutoutCenterX - ARROW_SIZE)
            .coerceIn(tooltipX, (tooltipX + tooltipWidth - ARROW_SIZE * 2).coerceAtLeast(tooltipX))
        val placeBelow = (screenHeightDp - cutoutBottomDp) >= cutoutTopDp

        if (placeBelow) {
            TourArrow(pointingUp = true, modifier = Modifier.offset(x = arrowX, y = cutoutBottomDp))
            Box(Modifier.offset(x = tooltipX, y = cutoutBottomDp + ARROW_SIZE).width(tooltipWidth)) {
                TourTooltip(text = tour.description, onDismiss = dismiss)
            }
        } else {
            TourArrow(pointingUp = false, modifier = Modifier.offset(x = arrowX, y = cutoutTopDp - ARROW_SIZE))
            // `.height(...)`, not `.heightIn(max = ...)` — a `Box` with only a max-height cap
            // shrinks to wrap its (small) content instead of actually occupying the full region,
            // which left `contentAlignment = BottomStart` with no real space to align within —
            // the tooltip rendered pinned at this Box's `offset(y = 0.dp)`, i.e. the very top of
            // the screen, regardless of where the target actually was. Forcing the real height
            // gives BottomStart something to anchor against, landing the tooltip just above the
            // cutout as intended.
            Box(
                modifier = Modifier
                    .offset(x = tooltipX, y = 0.dp)
                    .width(tooltipWidth)
                    .height((cutoutTopDp - ARROW_SIZE).coerceAtLeast(0.dp)),
                contentAlignment = Alignment.BottomStart,
            ) {
                TourTooltip(text = tour.description, onDismiss = dismiss)
            }
        }
    }
}

/** Consumes taps without acting on them — purely to keep the rest of the screen inert while a
 * tour is showing, since the tour is dismissible only via [TourTooltip]'s own close button. */
@Composable
private fun ScrimBlockRegion(modifier: Modifier, tourKey: String) {
    Box(modifier.pointerInput(tourKey) { detectTapGestures { } })
}

@Composable
private fun TourTooltip(text: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.console_tour_dismiss_action),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

@Composable
private fun TourArrow(pointingUp: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.inverseSurface
    Canvas(modifier = modifier.size(width = ARROW_SIZE * 2, height = ARROW_SIZE)) {
        val path = Path().apply {
            if (pointingUp) {
                moveTo(0f, size.height)
                lineTo(size.width / 2, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width / 2, size.height)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, color = color)
    }
}
