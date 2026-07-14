package com.wwwescape.deviceinfox.console.ui.livelocation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Date
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.location.EtaState
import com.wwwescape.deviceinfox.console.data.location.PartnerLiveLocation
import com.wwwescape.deviceinfox.console.data.location.SelfLiveLocation
import kotlinx.coroutines.launch

private val SelfMarkerColor = Color(0xFF155DFC)
private val PartnerMarkerColor = Color(0xFFEA580C)
private const val FIT_BOUNDS_PADDING_PX = 150

/** The actual map — two markers (initials, per the confirmed design), the Directions-API road
 * path between them, and pan/zoom/reset-to-center only (no rotate/tilt, no default "my location"/
 * toolbar/compass UI — a custom reset button replaces Google's own recenter button, since "center"
 * here means "fit both partners," not "center on this device"). [routeState]'s ETA text sits in a
 * fixed card at the bottom, per the confirmed design. */
@Composable
fun LiveLocationMapView(
    selfLocation: SelfLiveLocation?,
    selfInitials: String,
    partnerLocation: PartnerLiveLocation,
    partnerInitials: String,
    routeState: LiveLocationRouteState,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    var hasAutoFitted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val selfLatLng = selfLocation?.let { LatLng(it.lat, it.lng) }
    val partnerLatLng = partnerLocation.lat?.let { lat -> partnerLocation.lng?.let { lng -> LatLng(lat, lng) } }

    suspend fun fitToBothMarkers() {
        val points = listOfNotNull(selfLatLng, partnerLatLng)
        if (points.isEmpty()) return
        val update = if (points.size == 1) {
            CameraUpdateFactory.newLatLngZoom(points[0], 15f)
        } else {
            val bounds = LatLngBounds.Builder().apply { points.forEach(::include) }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, FIT_BOUNDS_PADDING_PX)
        }
        cameraPositionState.animate(update)
    }

    // Auto-fits once, the first moment both markers exist — after that, the user's own pan/zoom
    // is left alone until they explicitly tap the reset button, matching how a "reset" control is
    // expected to behave (not fighting the user's own camera adjustments on every position tick).
    LaunchedEffect(selfLatLng != null && partnerLatLng != null) {
        if (!hasAutoFitted && selfLatLng != null && partnerLatLng != null) {
            hasAutoFitted = true
            fitToBothMarkers()
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = remember {
                MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    compassEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                )
            },
        ) {
            selfLatLng?.let { latLng ->
                Marker(
                    state = remember(latLng) { MarkerState(position = latLng) },
                    icon = rememberInitialsMarkerIcon(selfInitials, SelfMarkerColor),
                )
            }
            partnerLatLng?.let { latLng ->
                Marker(
                    state = remember(latLng) { MarkerState(position = latLng) },
                    icon = rememberInitialsMarkerIcon(partnerInitials, PartnerMarkerColor),
                )
            }
            if (routeState.polylinePoints.size >= 2) {
                Polyline(points = routeState.polylinePoints, color = MaterialTheme.colorScheme.primary, width = 10f)
            }
        }

        FloatingActionButton(
            onClick = { coroutineScope.launch { fitToBothMarkers() } },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.console_live_location_reset_center_action))
        }

        // Edge-to-edge solid bar (not a floating/margined Card) — per the redesigned layout's
        // mockup. Second line (clock-time estimate) only renders once an ETA is actually known —
        // Unknown/NotClosing have no meaningful "around X" moment to show.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
        ) {
            Text(
                text = etaText(routeState.etaState),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            etaClockTimeText(routeState.etaState)?.let { clockText ->
                Text(
                    text = clockText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun etaText(etaState: EtaState): String = when (etaState) {
    EtaState.Unknown -> stringResource(R.string.console_live_location_eta_calculating)
    EtaState.NotClosing -> stringResource(R.string.console_live_location_eta_not_closing)
    is EtaState.Estimated -> {
        val totalMinutes = (etaState.etaSeconds / 60).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (hours > 0) {
            stringResource(R.string.console_live_location_eta_hours_minutes, hours, minutes)
        } else {
            stringResource(R.string.console_live_location_eta_minutes, minutes)
        }
    }
}

/** The second ETA-card line — a real clock-time estimate ("You will meet your partner at around
 * 5:15pm"), computed client-side from [EtaState.Estimated.etaSeconds] (no new data needed, the
 * route fetch already carries this). Null for [EtaState.Unknown]/[EtaState.NotClosing] — neither
 * has a meaningful moment to point at. */
@Composable
private fun etaClockTimeText(etaState: EtaState): String? {
    if (etaState !is EtaState.Estimated) return null
    val context = LocalContext.current
    val meetingTimeMillis = System.currentTimeMillis() + etaState.etaSeconds * 1000
    val formatted = DateFormat.getTimeFormat(context).format(Date(meetingTimeMillis))
    return stringResource(R.string.console_live_location_eta_clock_time, formatted)
}

/** A plain colored circle + centered initials, rasterized once per (initials, color) pair — the
 * "automatic fit, no manual crop/clip" pattern this app already established elsewhere doesn't
 * apply to marker icons specifically (there's no user-picked photo here to crop), so this is
 * simpler: draw directly rather than loading/cropping an image. */
@Composable
private fun rememberInitialsMarkerIcon(initials: String, backgroundColor: Color): BitmapDescriptor {
    val density = LocalDensity.current
    return remember(initials, backgroundColor, density) {
        val sizePx = with(density) { 40.dp.toPx() }.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor.toArgb() }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, circlePaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.4f
            isFakeBoldText = true
        }
        val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initials, sizePx / 2f, textY, textPaint)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
