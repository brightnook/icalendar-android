package com.victor2319.ioscalendar.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedSecret(
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
)

interface SecretCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSecret

    fun decrypt(encryptedSecret: EncryptedSecret): ByteArray
}

class AndroidKeystoreSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val initializationVector = cipher.iv.copyOf()
        require(initializationVector.size == IV_SIZE_BYTES) {
            "Android Keystore returned an unsupported GCM IV length"
        }
        return EncryptedSecret(initializationVector, cipher.doFinal(plaintext))
    }

    override fun decrypt(encryptedSecret: EncryptedSecret): ByteArray {
        require(encryptedSecret.initializationVector.size == IV_SIZE_BYTES) {
            "Unsupported GCM IV length"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, encryptedSecret.initializationVector),
        )
        return cipher.doFinal(encryptedSecret.ciphertext)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "ioscalendar.credentials.v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_SIZE_BITS = 128
        private const val IV_SIZE_BYTES = 12
    }
}
