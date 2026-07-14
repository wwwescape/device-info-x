package com.wwwescape.deviceinfox.console.data.location

import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.LocationApi
import com.wwwescape.deviceinfox.console.data.network.WsEvent
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.LocationUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateTimeToEpochMillis
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The partner's live position, as last reported — a single current value, not a history (see
 * `location_service.py`'s own "ephemeral relay, not persisted" design). All-default/not-sharing
 * until either a fresh [LiveLocationRepository.refreshStatus] pull or a live `location.update`
 * event says otherwise. */
data class PartnerLiveLocation(
    val isSharing: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val updatedAtEpochMillis: Long? = null,
)

/** This device's own last-known position while sharing — populated directly by
 * [LiveLocationService]'s location callback (the same value it pushes to the server), not
 * round-tripped through a network call, so the map screen's own marker/ETA math has it the
 * instant a location fix comes in rather than waiting on a server response. */
data class SelfLiveLocation(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val updatedAtEpochMillis: Long,
)

/** Live Location Sharing's realtime half — same WS-event-consumer shape as
 * [com.wwwescape.deviceinfox.console.data.presence.ServerPresenceRepository], with two
 * deliberate differences: (1) state changes go out over plain REST ([LocationApi]) rather than
 * `webSocketClient.send()`, since a backgrounded sender's [LiveLocationService] has no live
 * socket to send on — see `ConsoleWebSocketClient`'s own "nothing runs once it's not open"
 * design; (2) there's no server-pushed "on-connect snapshot" the way presence gets
 * (`presence_service.send_snapshot`), so [refreshStatus] pulls one instead, on every reconnect. */
@Singleton
class LiveLocationRepository @Inject constructor(
    private val locationApi: LocationApi,
    private val webSocketClient: ConsoleWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selfSharing = MutableStateFlow(false)
    val selfSharing: Flow<Boolean> = _selfSharing.asStateFlow()

    private val _partnerLocation = MutableStateFlow(PartnerLiveLocation())
    val partnerLocation: Flow<PartnerLiveLocation> = _partnerLocation.asStateFlow()

    private val _selfLocation = MutableStateFlow<SelfLiveLocation?>(null)
    val selfLocation: Flow<SelfLiveLocation?> = _selfLocation.asStateFlow()

    /** Called by [LiveLocationService]'s location callback — see [SelfLiveLocation]'s own doc
     * comment for why this is a local set rather than something read back off the network. */
    fun updateSelfLocationLocally(lat: Double, lng: Double, accuracyM: Float?) {
        _selfLocation.value = SelfLiveLocation(lat, lng, accuracyM, System.currentTimeMillis())
    }

    /** Fires with a `reason` ("manual"/"auto_timeout") whenever sharing ends, for either side —
     * disabling is mutual server-side, so this is the one signal both the map screen and
     * [LiveLocationService] need to know "stop now," regardless of which side (or the server's own
     * 2-hour sweep) actually triggered it. */
    private val _sharingEndedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sharingEndedEvent: SharedFlow<String> = _sharingEndedEvent.asSharedFlow()

    /** [LiveLocationService]'s 30-min-then-every-15-min check-in timer fires this — the map
     * screen, if it happens to be open, shows the in-app "keep sharing?" dialog when it does (see
     * `LiveLocationScreen`'s own collector). The service *also* posts a system notification with
     * the same two options unconditionally, since this device's console might not be foregrounded
     * at all when a reminder fires — this event only covers the "already looking at the map"
     * case. */
    private val _checkInReminderEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val checkInReminderEvent: SharedFlow<Unit> = _checkInReminderEvent.asSharedFlow()

    fun emitCheckInReminder() {
        _checkInReminderEvent.tryEmit(Unit)
    }

    init {
        scope.launch {
            webSocketClient.events.collect { event -> runCatching { handleEvent(event) } }
        }
        scope.launch {
            webSocketClient.connected.collect { runCatching { refreshStatus() } }
        }
    }

    private fun handleEvent(event: WsEvent) {
        when (event.type) {
            // Only ever pushed about the *partner* — location_service.enable_sharing never
            // notifies the caller about their own action.
            "location.enabled" -> {
                _partnerLocation.value = _partnerLocation.value.copy(isSharing = true)
            }
            // Pushed to *both* sides (location_service.disable_sharing) — disabling is mutual, so
            // this always means sharing just ended for this device too, regardless of which side
            // (or the server's 2-hour auto-disable sweep) actually triggered it.
            "location.disabled" -> {
                val reason = event.data["reason"]?.jsonPrimitive?.contentOrNull ?: "manual"
                _selfSharing.value = false
                _partnerLocation.value = PartnerLiveLocation()
                _selfLocation.value = null
                _sharingEndedEvent.tryEmit(reason)
            }
            "location.update" -> {
                val lat = event.data["lat"]?.jsonPrimitive?.doubleOrNull ?: return
                val lng = event.data["lng"]?.jsonPrimitive?.doubleOrNull ?: return
                val accuracyM = event.data["accuracy_m"]?.jsonPrimitive?.doubleOrNull
                val updatedAt = event.data["updated_at"]?.jsonPrimitive?.contentOrNull
                _partnerLocation.value = _partnerLocation.value.copy(
                    isSharing = true,
                    lat = lat,
                    lng = lng,
                    accuracyM = accuracyM,
                    updatedAtEpochMillis = updatedAt?.isoDateTimeToEpochMillis(),
                )
            }
        }
    }

    /** Pulled on every WS (re)connect above, and callable on demand (e.g. when the map screen or
     * the Settings dot indicator first appears) to seed current state rather than waiting on the
     * next live delta. */
    suspend fun refreshStatus() {
        val status = consoleApiCall { locationApi.status() }
        _selfSharing.value = status.selfStatus.sharing
        val partner = status.partnerStatus
        _partnerLocation.value = if (partner == null) {
            PartnerLiveLocation()
        } else {
            PartnerLiveLocation(
                isSharing = partner.sharing,
                lat = partner.lat,
                lng = partner.lng,
                accuracyM = partner.accuracyM,
                updatedAtEpochMillis = partner.updatedAt?.isoDateTimeToEpochMillis(),
            )
        }
    }

    suspend fun enableSharing() {
        consoleApiCall { locationApi.enable() }
        _selfSharing.value = true
    }

    /** Mutual — also ends the partner's side server-side, which is what the `location.disabled`
     * WS event handled above reflects back to this device too (harmless double-clear). */
    suspend fun disableSharing() {
        consoleApiCall { locationApi.disable() }
        _selfSharing.value = false
        _partnerLocation.value = PartnerLiveLocation()
        _selfLocation.value = null
    }

    suspend fun pushLocationUpdate(lat: Double, lng: Double, accuracyM: Float?) {
        consoleApiCall { locationApi.update(LocationUpdateDto(lat = lat, lng = lng, accuracyM = accuracyM?.toDouble())) }
    }
}
