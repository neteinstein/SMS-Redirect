package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.security.SecretCipher

/**
 * Reversible marker "encryption". The `enc:` prefix lets a test assert that a value was actually
 * passed through the cipher rather than stored in the clear.
 */
class FakeSecretCipher : SecretCipher {
    /** Simulates the keystore key being unavailable, as after a restore onto a new device. */
    var failDecrypt: Boolean = false

    override fun encrypt(plaintext: String): String = PREFIX + plaintext

    override fun decrypt(token: String): String? =
        when {
            failDecrypt -> null
            token.startsWith(PREFIX) -> token.removePrefix(PREFIX)
            else -> null
        }

    companion object {
        const val PREFIX = "enc:"
    }
}
