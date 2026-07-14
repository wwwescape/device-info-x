package com.wwwescape.deviceinfox.console.data.push

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last FCM token successfully registered with the server — plain (not Keystore-encrypted)
 * `SharedPreferences`, same tier as [com.wwwescape.deviceinfox.console.data.network.ConsoleServerConfig]:
 * an FCM token identifies this install to Google's push infrastructure, not a secret credential.
 *
 * Exists purely so [ConsolePushRepository.unregisterCurrentToken] can tell the server which
 * token to forget on logout without needing a fresh (and, post-logout, unauthenticated)
 * `FirebaseMessaging` round trip.
 */
@Singleton
class ConsolePushTokenStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastRegisteredToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    private companion object {
        const val PREFS_NAME = "console_push_token"
        const val KEY_TOKEN = "fcm_token"
    }
}
