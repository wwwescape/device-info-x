package com.wwwescape.deviceinfox.console.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The server session's JWT access + opaque refresh token, Keystore-backed
 * `EncryptedSharedPreferences` — same pattern as [com.wwwescape.deviceinfox.console.data.auth.AuthRepository]'s
 * PIN-hash file and [com.wwwescape.deviceinfox.console.data.db.ConsoleDatabaseKeyProvider]'s DB
 * passphrase file, in its own file so a leak of one doesn't hand over the others.
 *
 * Per the locked-in Phase 11 decision, account login happens once during first-run setup —
 * these tokens, once set, are what let the PIN alone gate every later console entry; the access
 * token is refreshed silently in the background by [AuthAuthenticator] as it expires.
 */
@Singleton
class ConsoleAuthTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    /** A session exists once both tokens have been set at least once — cleared together by
     * [clear], never independently, so this can't observe a half-written state. Exposed as a
     * `StateFlow` (not a plain computed property) so [com.wwwescape.deviceinfox.console.ui.auth.PinGateDialog]
     * recomposes from [com.wwwescape.deviceinfox.console.ui.auth.AccountSetupDialog] into the PIN
     * flow the moment login/registration completes, without needing its own event plumbing. */
    private val _hasSession = MutableStateFlow(accessToken != null && refreshToken != null)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    /** Set from `UserPublic.must_change_password` right after login (see
     * [com.wwwescape.deviceinfox.console.data.network.ServerAuthRepository.login]) — persisted
     * (not in-memory) so an interrupted forced-change flow (app killed mid-flow) still blocks
     * console entry on the next launch instead of silently letting the OTP stand in as a real
     * password forever. [com.wwwescape.deviceinfox.console.ui.auth.PinGateDialog] gates on this
     * the same way it gates on [hasSession]. */
    private val _mustChangePassword = MutableStateFlow(prefs.getBoolean(KEY_MUST_CHANGE_PASSWORD, false))
    val mustChangePassword: StateFlow<Boolean> = _mustChangePassword.asStateFlow()

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        _hasSession.value = true
    }

    fun setMustChangePassword(value: Boolean) {
        prefs.edit { putBoolean(KEY_MUST_CHANGE_PASSWORD, value) }
        _mustChangePassword.value = value
    }

    /** Called after a successful silent refresh — the refresh token itself is typically
     * unchanged, but callers pass whatever the server returned since some backends rotate it
     * on every use. */
    fun updateAccessToken(accessToken: String, refreshToken: String) {
        saveTokens(accessToken, refreshToken)
    }

    fun clear() {
        prefs.edit { clear() }
        _hasSession.value = false
        _mustChangePassword.value = false
    }

    private companion object {
        const val PREFS_NAME = "console_server_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_MUST_CHANGE_PASSWORD = "must_change_password"
    }
}
