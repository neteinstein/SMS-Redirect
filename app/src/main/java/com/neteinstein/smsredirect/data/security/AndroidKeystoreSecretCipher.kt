package com.neteinstein.smsredirect.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.neteinstein.smsredirect.domain.security.SecretCipher
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption backed by the Android Keystore. The key never leaves the keystore, so the
 * ciphertext is useless off the device it was produced on.
 *
 * That last point is load-bearing: `res/xml/data_extraction_rules.xml` opts the Room database
 * into cloud backup and device transfer, but the keystore key does not travel with it. On a new
 * device [decrypt] returns null and the user has to re-enter their app password. Callers must
 * treat a null result as "no credentials" and say so, rather than reporting an auth failure.
 *
 * Deliberately not using androidx.security:security-crypto -- EncryptedSharedPreferences and
 * MasterKey are deprecated, still alpha on the 1.1 line, and pull in Tink.
 */
class AndroidKeystoreSecretCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecretCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(cipher.iv) + SEPARATOR + encoder.encodeToString(ciphertext)
    }

    override fun decrypt(token: String): String? =
        runCatching {
            val separatorIndex = token.indexOf(SEPARATOR)
            if (separatorIndex <= 0) return null
            val iv = decoder.decode(token.substring(0, separatorIndex))
            val ciphertext = decoder.decode(token.substring(separatorIndex + 1))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(keyAlias, null) as? SecretKey

    private fun loadOrCreateKey(): SecretKey =
        existingKey() ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(
                            keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
            }.generateKey()

    companion object {
        const val DEFAULT_KEY_ALIAS = "sms_toolbox_gmail_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val SEPARATOR = ':'

        // java.util.Base64, not android.util.Base64: this module's unit tests run with
        // testOptions.unitTests.isReturnDefaultValues = true, under which every android.jar
        // call silently returns null instead of throwing. A test would pass on a lie.
        private val encoder: Base64.Encoder = Base64.getEncoder()
        private val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
