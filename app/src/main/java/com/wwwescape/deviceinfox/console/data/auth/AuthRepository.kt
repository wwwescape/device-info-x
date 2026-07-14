package com.wwwescape.deviceinfox.console.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PIN storage and lockout policy for the console (private-mode) gate. Never stores the PIN
 * itself, only a salted [PinHasher] digest, in an EncryptedSharedPreferences file separate from
 * every other DataStore/SharedPreferences file in the app.
 *
 * Lockout state is persisted (not in-memory), so a force-close/relaunch can't be used to reset
 * failed-attempt counters — the check happens before the candidate PIN is even hashed.
 */
@Singleton
class AuthRepository @Inject constructor(
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

    suspend fun isPinSet(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(KEY_HASH)
    }

    suspend fun setPin(pin: String): Unit = withContext(Dispatchers.IO) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        // Stored alongside the real hash so a later [verifyPin] can recognize the code entered in
        // reverse without ever having plaintext to reverse at that point.
        val duressHash = PinHasher.hash(pin.reversed(), salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_DURESS_HASH, Base64.encodeToString(duressHash, Base64.NO_WRAP))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_UNTIL_MILLIS, 0L)
            .apply()
    }

    suspend fun verifyPin(pin: String): AuthResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL_MILLIS, 0L)
        if (now < lockedUntil) {
            return@withContext AuthResult.LockedOut(retryAfterSeconds = secondsUntil(lockedUntil, now))
        }

        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val expectedHash = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val duressHash = prefs.getString(KEY_DURESS_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val matches = salt != null && expectedHash != null && PinHasher.matches(PinHasher.hash(pin, salt), expectedHash)

        if (matches) {
            // Accounts whose code was set before duress hashing existed have no KEY_DURESS_HASH yet
            // — there's no plaintext to backfill it from except right here, on the one real code
            // entry we're ever handed. Silent and one-time; every later real unlock is a no-op
            // against this same check.
            if (duressHash == null) {
                val backfill = PinHasher.hash(pin.reversed(), checkNotNull(salt))
                prefs.edit().putString(KEY_DURESS_HASH, Base64.encodeToString(backfill, Base64.NO_WRAP)).apply()
            }
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL_MILLIS, 0L)
                .apply()
            AuthResult.Success
        } else {
            val isDuress = salt != null && duressHash != null && PinHasher.matches(PinHasher.hash(pin, salt), duressHash)
            val visibleOutcome = recordFailedAttempt(now)
            if (isDuress) AuthResult.Duress(visibleOutcome) else visibleOutcome
        }
    }

    /** Whether entering the code in reverse would actually trigger a wipe right now — a palindrome
     * code (real hash == duress hash) can never satisfy that, and an account migrated from before
     * duress hashing existed hasn't been backfilled yet (see [verifyPin]). Purely a passive
     * Settings readout; nothing is ever gated on it. */
    suspend fun duressStatus(): DuressStatus = withContext(Dispatchers.IO) {
        val expectedHash = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val duressHash = prefs.getString(KEY_DURESS_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        when {
            expectedHash == null || duressHash == null -> DuressStatus.PENDING
            PinHasher.matches(duressHash, expectedHash) -> DuressStatus.INACTIVE
            else -> DuressStatus.ACTIVE
        }
    }

    /** Wipes the PIN hash/salt/lockout state entirely — used by "Destroy all data" so a fresh
     * account setup afterward prompts for a genuinely new PIN instead of [isPinSet] staying true
     * and silently keeping the destroyed account's old hash. */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private fun recordFailedAttempt(now: Long): AuthResult {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        return if (attempts >= LOCKOUT_THRESHOLD) {
            val lockoutSeconds = lockoutDurationSeconds(attempts)
            val until = now + lockoutSeconds * 1_000
            editor.putLong(KEY_LOCKED_UNTIL_MILLIS, until).apply()
            AuthResult.LockedOut(retryAfterSeconds = lockoutSeconds)
        } else {
            editor.apply()
            AuthResult.IncorrectCode
        }
    }

    /** Exponential backoff once the threshold is crossed: 30s, 60s, 120s, 240s, capped at 300s. */
    private fun lockoutDurationSeconds(attempts: Int): Long {
        val stepsPastThreshold = (attempts - LOCKOUT_THRESHOLD).coerceAtMost(4)
        return (BASE_LOCKOUT_SECONDS * (1L shl stepsPastThreshold)).coerceAtMost(MAX_LOCKOUT_SECONDS)
    }

    private fun secondsUntil(untilMillis: Long, nowMillis: Long): Long =
        ((untilMillis - nowMillis) / 1_000) + 1

    private companion object {
        const val PREFS_NAME = "console_auth"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_DURESS_HASH = "duress_hash"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKED_UNTIL_MILLIS = "locked_until_millis"
        const val LOCKOUT_THRESHOLD = 5
        const val BASE_LOCKOUT_SECONDS = 30L
        const val MAX_LOCKOUT_SECONDS = 300L
    }
}

/** [AuthRepository.duressStatus] readout for Settings — never used to gate anything, only shown. */
enum class DuressStatus {
    /** Code is set but the duress hash hasn't been computed yet — only possible for an account
     * that had a code before duress hashing existed, until its next successful real-code unlock. */
    PENDING,
    ACTIVE,

    /** The stored code is a palindrome, so its reverse is itself — entering it "in reverse" is
     * just entering it, which can only ever unlock, never wipe. */
    INACTIVE,
}
