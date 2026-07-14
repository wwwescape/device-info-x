package com.wwwescape.deviceinfox.console.data.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SQLCipher passphrase for [ConsoleDatabase] — a random 256-bit value generated on first
 * use, never derived from the user's PIN (so changing the PIN later doesn't require re-keying
 * the database). It's held at rest in its own Keystore-backed EncryptedSharedPreferences file,
 * separate from [com.wwwescape.deviceinfox.console.data.auth.AuthRepository]'s file — a leak of
 * one shouldn't hand over the other.
 */
@Singleton
class ConsoleDatabaseKeyProvider @Inject constructor(
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

    /** SQLCipher's `SupportFactory` zeroes out the byte array it's given after deriving the
     * database key from it, so callers must not reuse the returned array afterward. */
    fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val generated = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PASSPHRASE, Base64.encodeToString(generated, Base64.NO_WRAP)).apply()
        return generated
    }

    private companion object {
        const val PREFS_NAME = "console_db_key"
        const val KEY_PASSPHRASE = "db_passphrase"
        const val PASSPHRASE_LENGTH_BYTES = 32
    }
}
