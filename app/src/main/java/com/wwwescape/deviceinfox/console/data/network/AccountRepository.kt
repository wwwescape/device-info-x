package com.wwwescape.deviceinfox.console.data.network

import android.content.Context
import com.wwwescape.deviceinfox.console.data.auth.AuthRepository
import com.wwwescape.deviceinfox.console.data.db.ConsoleDatabase
import com.wwwescape.deviceinfox.console.data.network.dto.ChangePasswordRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.VerifyPasswordRequestDto
import com.wwwescape.deviceinfox.console.data.push.ConsolePushRepository
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "Destroy all data" action in Settings — deletes the account itself server-side
 * (`DELETE /users/me`, cascades every owned row per `user_service.delete_account`), then tears
 * down every trace of it left on this device. Deliberately its own small repository rather than
 * bolted onto [PartnerRepository] (which is specifically about the profile row) or
 * [ServerAuthRepository] (which only ever runs during first-run setup / silent-refresh recovery,
 * never as a user-triggered destructive action).
 */
@Singleton
class AccountRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val usersApi: UsersApi,
    private val authApi: AuthApi,
    private val pushRepository: ConsolePushRepository,
    private val tokenStore: ConsoleAuthTokenStore,
    private val authRepository: AuthRepository,
    private val consoleDatabase: ConsoleDatabase,
    private val sessionManager: ConsoleSessionManager,
    private val serverAuthRepository: ServerAuthRepository,
    private val serverConfig: ConsoleServerConfig,
) {
    /** Unlike [ServerAuthRepository.logout], the server call here is NOT best-effort — nothing
     * local gets torn down unless the account is actually gone server-side. */
    suspend fun destroyAllDataAndAccount() {
        runCatching { pushRepository.unregisterCurrentToken() }
        consoleApiCall { usersApi.deleteMe() }

        wipeLocalDatabaseAndMedia()
        tokenStore.clear()
        authRepository.clear()
        sessionManager.clear()
    }

    /** "Change server" in Settings — same local-wipe steps as [destroyAllDataAndAccount] minus
     * the account-deletion call itself (this device is disconnecting from the account, not
     * deleting it — the account and its data stay intact on the old server). */
    suspend fun changeServer(newServerUrl: String) {
        // Best-effort, against the OLD server (must run before serverConfig.setBaseUrl below) —
        // revokes the refresh token and unregisters the push token, then clears tokenStore.
        runCatching { serverAuthRepository.logout() }

        wipeLocalDatabaseAndMedia()
        authRepository.clear()
        serverConfig.setBaseUrl(newServerUrl)
        sessionManager.clear()
    }

    /** Duress-code trigger (entering the console's unlock code in reverse — see
     * [com.wwwescape.deviceinfox.console.data.auth.AuthRepository.verifyPin]) — same local-wipe
     * steps as [destroyAllDataAndAccount]/[changeServer], but deliberately nothing else: no server
     * call, and [tokenStore]/[authRepository]/[sessionManager] are all left untouched so a genuine
     * code unlock afterward just re-syncs from the server like any other fresh-DB launch, with no
     * re-registration and no visible sign anything happened. */
    suspend fun panicWipeLocalData() {
        wipeLocalDatabaseAndMedia()
    }

    private suspend fun wipeLocalDatabaseAndMedia() {
        consoleDatabase.clearAllTables()
        for (dirName in PRIVATE_MEDIA_DIR_NAMES) {
            File(context.filesDir, dirName).deleteRecursively()
        }
    }

    /** Covers both the mandatory post-OTP-login flow ([com.wwwescape.deviceinfox.console.ui.auth.ChangePasswordRequiredDialog])
     * and a genuinely voluntary change — the server requires [currentPassword] either way (an
     * OTP counts as the "current" one), so this is the one path both flows share. */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        consoleApiCall { usersApi.changePassword(ChangePasswordRequestDto(currentPassword, newPassword)) }
        tokenStore.setMustChangePassword(false)
    }

    /** "Forgot code?" on [com.wwwescape.deviceinfox.console.ui.auth.PinGateDialog] — re-proves
     * identity via the *account* password (this device may already hold a valid server session
     * even while PIN-locked, so skipping this check would let anyone holding the phone bypass the
     * PIN entirely) before wiping the local PIN hash, same end state as
     * [com.wwwescape.deviceinfox.console.data.auth.AuthRepository.clear] gives "Destroy all
     * data," minus the data loss. */
    suspend fun resetPin(accountPassword: String) {
        consoleApiCall { authApi.verifyPassword(VerifyPasswordRequestDto(accountPassword)) }
        authRepository.clear()
    }

    private companion object {
        val PRIVATE_MEDIA_DIR_NAMES = listOf("console_media", "console_vault", "console_downloads")
    }
}
