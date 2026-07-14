package com.wwwescape.deviceinfox.console.data.push

import com.google.firebase.messaging.FirebaseMessaging
import com.wwwescape.deviceinfox.BuildConfig
import com.wwwescape.deviceinfox.console.data.network.ConsoleAuthTokenStore
import com.wwwescape.deviceinfox.console.data.network.DevicesApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.DeviceRegisterRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Registers/unregisters this device's FCM token with `POST /devices`/`DELETE /devices/{token}`
 * (Phase 11's deferred FCM item). Registration is best-effort everywhere it's called from
 * ([com.wwwescape.deviceinfox.console.data.network.ServerAuthRepository]'s `login`/`onNewToken`
 * via [com.wwwescape.deviceinfox.console.push.ConsoleFcmService]) — a push-registration failure
 * must never block login or silently corrupt the session, the same tolerance the existing
 * best-effort `authApi.logout()` call already gets in `ServerAuthRepository.logout`.
 */
@Singleton
class ConsolePushRepository @Inject constructor(
    private val devicesApi: DevicesApi,
    private val tokenStore: ConsolePushTokenStore,
    private val authTokenStore: ConsoleAuthTokenStore,
) {
    /** No-ops quietly if there's no server session yet — [com.google.firebase.messaging.FirebaseMessagingService.onNewToken]
     * can fire before any login has ever happened, and this is also called right after a fresh
     * login where a token may already be cached from an earlier install/session. */
    suspend fun registerCurrentToken() {
        if (!authTokenStore.hasSession.value) return
        val token = runCatching { FirebaseMessaging.getInstance().awaitToken() }.getOrNull() ?: return
        registerToken(token)
    }

    suspend fun onNewToken(token: String) {
        if (!authTokenStore.hasSession.value) return
        registerToken(token)
    }

    private suspend fun registerToken(token: String) {
        consoleApiCall {
            devicesApi.register(DeviceRegisterRequestDto(fcmToken = token, appVersion = BuildConfig.VERSION_NAME))
        }
        tokenStore.lastRegisteredToken = token
    }

    suspend fun unregisterCurrentToken() {
        val token = tokenStore.lastRegisteredToken ?: return
        consoleApiCall { devicesApi.unregister(token) }
        tokenStore.lastRegisteredToken = null
    }
}

private suspend fun FirebaseMessaging.awaitToken(): String = suspendCancellableCoroutine { continuation ->
    token.addOnSuccessListener { continuation.resume(it) }
        .addOnFailureListener { continuation.resumeWithException(it) }
}
