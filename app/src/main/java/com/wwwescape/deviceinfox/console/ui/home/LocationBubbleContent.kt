package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.BuildConfig
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageLocation

private const val MAP_IMAGE_WIDTH_PX = 400
private const val MAP_IMAGE_HEIGHT_PX = 240
private const val MAP_ZOOM = 15

/** Builds a Google Static Maps API request URL — deliberately a flat image, not a live embedded
 * `GoogleMap` view, matching what WhatsApp itself does for the exact same reason: rendering a real
 * map SDK view per bubble in a scrolling message list is a known perf/memory cost a static image
 * avoids entirely. Loaded through the same [AsyncImage]/Coil path every other image attachment
 * already uses (`HomeScreen.kt`'s `MessageBubbleContent`) — Coil has no custom `ImageLoader`/auth
 * interceptor wired in anywhere in this app, so a remote `maps.googleapis.com` URL is safe to load
 * directly, same reasoning `DirectionsModule.kt` documents for keeping its own Retrofit client
 * separate from the console's authenticated one. */
private fun staticMapUrl(lat: Double, lng: Double): String =
    "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lng&zoom=$MAP_ZOOM" +
        "&size=${MAP_IMAGE_WIDTH_PX}x$MAP_IMAGE_HEIGHT_PX&markers=color:red%7C$lat,$lng&key=${BuildConfig.MAPS_API_KEY}"

@Composable
private fun MapThumbnail(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = staticMapUrl(lat, lng),
            contentDescription = stringResource(R.string.console_home_location_bubble_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** The bubble content for a location message — sits alongside `message.attachment?.let {}` /
 * `message.voiceNote?.let {}` in `MessageBubbleContent`. Tapping opens the system Maps app via
 * [onClick] (a `geo:` intent), same "tap for the real interactive thing" behavior WhatsApp's own
 * static-snapshot bubble has. */
@Composable
fun LocationBubbleContent(location: MessageLocation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MapThumbnail(
        lat = location.lat,
        lng = location.lng,
        modifier = modifier.size(200.dp, 120.dp).clickable(onClick = onClick),
    )
}

/** Confirmation step before actually sending an exact GPS pin — deliberate friction given how
 * sensitive a precise location share is, unlike the other attach types (Camera/Gallery/Document),
 * which send as soon as a file is picked. Reuses [MapThumbnail] so what's confirmed is exactly
 * what will be sent. */
@Composable
fun LocationConfirmDialog(lat: Double, lng: Double, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_home_location_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.console_home_location_confirm_body))
                Spacer(modifier = Modifier.size(0.dp, 8.dp))
                MapThumbnail(lat = lat, lng = lng, modifier = Modifier.size(240.dp, 140.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.console_home_location_confirm_send_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.console_home_location_confirm_cancel_action))
            }
        },
    )
}
