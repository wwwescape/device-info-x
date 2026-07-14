package com.wwwescape.deviceinfox.console.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2WithHmacSHA256, not a memory-hard KDF (Argon2/bcrypt) — chosen to avoid a native/JNI
 * dependency on Android. The real defense here isn't hash cost, it's that this is one layer
 * behind [AuthRepository]'s persisted lockout policy and an EncryptedSharedPreferences store
 * that's itself Keystore-backed.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH_BYTES = 16

    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    /** Constant-time comparison — a naive `contentEquals` would leak timing information about
     * how many leading bytes matched. */
    fun matches(candidate: ByteArray, expected: ByteArray): Boolean = MessageDigest.isEqual(candidate, expected)
}
