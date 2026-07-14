package com.wwwescape.deviceinfox.console.data.calling

import com.wwwescape.deviceinfox.console.data.network.TurnApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton
import org.webrtc.PeerConnection

/** Fetches fresh short-lived coturn credentials (`GET /turn/credentials`, already scaffolded —
 * see `app/api/v1/routers/turn.py`) and maps them straight to the `org.webrtc` shape a
 * `PeerConnection` actually wants, so `CallRoomViewModel` never has to touch the raw DTO. Never
 * cached — `ttl` is deliberately short, and a fresh fetch per call keeps this simple. */
@Singleton
class TurnCredentialsRepository @Inject constructor(private val turnApi: TurnApi) {

    suspend fun fetchIceServers(): List<PeerConnection.IceServer> {
        val creds = consoleApiCall { turnApi.credentials() }
        return creds.urls.map { url ->
            PeerConnection.IceServer.builder(url)
                .setUsername(creds.username)
                .setPassword(creds.credential)
                .createIceServer()
        }
    }
}
