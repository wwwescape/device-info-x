package com.wwwescape.deviceinfox.console.data.release

import com.wwwescape.deviceinfox.BuildConfig
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.ReleasesApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** In-app update check — see the TODOS.md writeup this was built from for the full design and
 * why each piece landed where it did. Checked once per WS connect (every console open), same
 * "nothing runs once it's not open" cadence as presence — deliberately no separate re-check on a
 * Settings-screen visit; the one gap that leaves (a new release published while an old build is
 * already open on a live connection) isn't worth building around for a two-person app.
 *
 * [BuildConfig.BUILD_TIMESTAMP] is compared against the server's `latest_build` by plain string
 * equality, never parsed or ordered — both are hand-set, human-readable strings
 * (`"2026-08-15-2144"`), not real timestamps with any guaranteed format beyond that. */
@Singleton
class ReleaseRepository @Inject constructor(
    private val releasesApi: ReleasesApi,
    webSocketClient: ConsoleWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    private val _downloadUrl = MutableStateFlow<String?>(null)
    val downloadUrl: StateFlow<String?> = _downloadUrl.asStateFlow()

    init {
        scope.launch {
            webSocketClient.connected.collect { checkForUpdate() }
        }
    }

    /** Best-effort, same as every other background check in this app — a failed request here
     * just means the badge/card don't show this time, not a user-facing error; the next WS
     * connect (the next console open) tries again. */
    private suspend fun checkForUpdate() {
        runCatching { consoleApiCall { releasesApi.latest() } }
            .onSuccess { release ->
                _downloadUrl.value = release.downloadUrl
                _isUpdateAvailable.value = release.latestBuild != null && release.latestBuild != BuildConfig.BUILD_TIMESTAMP
            }
    }
}
