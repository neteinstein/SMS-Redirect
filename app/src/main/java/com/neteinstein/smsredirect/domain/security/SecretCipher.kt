package com.neteinstein.smsredirect.domain.security

/**
 * Symmetric encryption for secrets that must be stored on device, currently just the Gmail
 * app password.
 *
 * An interface rather than a concrete class so the data layer stays unit-testable: the real
 * implementation is backed by the Android Keystore, which does not exist on the JVM.
 */
interface SecretCipher {
    fun encrypt(plaintext: String): String

    /** Returns null when the token cannot be decrypted -- typically because the key is gone. */
    fun decrypt(token: String): String?
}
