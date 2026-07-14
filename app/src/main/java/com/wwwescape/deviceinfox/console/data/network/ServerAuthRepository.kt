package com.wwwescape.deviceinfox.console.data.network

import android.os.Build
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.network.dto.LoginRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.LogoutRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.RegisterRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.toGenderDto
import com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateString
import com.wwwescape.deviceinfox.console.data.push.ConsolePushRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * The server account session — separate from [com.wwwescape.deviceinfox.console.data.auth.AuthRepository],
 * which is the local PIN. Per the Phase 11 design, this only runs during first-run setup (or the
 * rare "refresh token was revoked/expired" recovery case) — once [ConsoleAuthTokenStore.hasSession]
 * is true, the PIN alone gates every later console entry, and [AuthAuthenticator] keeps the
 * server session alive silently in the background.
 */
@Singleton
class ServerAuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: ConsoleAuthTokenStore,
    private val serverConfig: ConsoleServerConfig,
    private val partnerRepository: PartnerRepository,
    private val pushRepository: ConsolePushRepository,
) {
    val hasSession: StateFlow<Boolean> = tokenStore.hasSession
    val mustChangePasswordRequired: StateFlow<Boolean> = tokenStore.mustChangePassword

    fun setServerUrl(url: String) {
        serverConfig.setBaseUrl(url)
    }

    /** The server only returns the new account's profile, not a session — every registration
     * is immediately followed by a real login so the caller ends up with tokens either way. */
    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        firstName: String,
        lastName: String,
        gender: PartnerGender,
        birthdayEpochMillis: Long,
        setupToken: String,
    ) {
        consoleApiCall {
            authApi.register(
                RegisterRequestDto(
                    username,
                    password,
                    displayName,
                    firstName,
                    lastName,
                    gender.toGenderDto(),
                    birthdayEpochMillis.toIsoDateString(),
                    setupToken,
                ),
            )
        }
        login(username, password)
    }

    suspend fun login(username: String, password: String) {
        val tokens = consoleApiCall {
            authApi.login(
                LoginRequestDto(
                    username,
                    password,
                    deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    timezone = java.time.ZoneId.systemDefault().id,
                ),
            )
        }
        tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken)
        // The server's real user UUID becomes this device's self PartnerEntity.id from here on
        // — see the doc comment on PartnerRepository.syncSelfProfileFromServer for why that has
        // to happen before anything else (messaging, pairing) touches PartnerEntity.
        val profile = consoleApiCall { authApi.me() }
        partnerRepository.syncSelfProfileFromServer(profile)
        // Reflects whatever the server says on every login — clears a stale true from an
        // interrupted previous flow just as reliably as it sets a fresh one.
        tokenStore.setMustChangePassword(profile.mustChangePassword)
        // Best-effort, same tolerance as logout()'s server call below — a push-registration
        // failure must never fail login itself.
        runCatching { pushRepository.registerCurrentToken() }
    }

    /** Best-effort — the refresh token is invalidated server-side, but the local session is
     * cleared regardless of whether that call actually reaches the server. */
    suspend fun logout() {
        val refreshToken = tokenStore.refreshToken
        if (refreshToken != null) {
            runCatching { consoleApiCall { authApi.logout(LogoutRequestDto(refreshToken)) } }
        }
        runCatching { pushRepository.unregisterCurrentToken() }
        tokenStore.clear()
    }
}
