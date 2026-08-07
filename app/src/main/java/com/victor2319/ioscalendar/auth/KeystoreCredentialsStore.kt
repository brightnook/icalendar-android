package com.victor2319.ioscalendar.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeystoreCredentialsStore internal constructor(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialsStore {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        AndroidKeystoreSecretCipher(),
    )

    override suspend fun save(credentials: Credentials) = withContext(ioDispatcher) {
        var plaintext = ByteArray(0)
        var encrypted: EncryptedSecret? = null
        try {
            plaintext = serialize(credentials)
            encrypted = cipher.encrypt(plaintext)
            require(encrypted.initializationVector.size == IV_SIZE_BYTES) {
                "Unsupported GCM IV length"
            }
            val envelope = listOf(
                ENVELOPE_VERSION,
                Base64.encodeToString(encrypted.initializationVector, Base64.NO_WRAP),
                Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
            ).joinToString(ENVELOPE_SEPARATOR)
            check(preferences.edit().clear().putString(ENVELOPE_KEY, envelope).commit()) {
                "Unable to persist encrypted credentials"
            }
        } finally {
            plaintext.fill(0)
            encrypted?.initializationVector?.fill(0)
            encrypted?.ciphertext?.fill(0)
        }
    }

    override suspend fun load(): Credentials? = withContext(ioDispatcher) {
        var initializationVector = ByteArray(0)
        var ciphertext = ByteArray(0)
        var plaintext = ByteArray(0)
        return@withContext try {
            val envelope = preferences.getString(ENVELOPE_KEY, null) ?: return@withContext null
            val parts = envelope.split(ENVELOPE_SEPARATOR, limit = ENVELOPE_PARTS + 1)
            if (parts.size != ENVELOPE_PARTS || parts[0] != ENVELOPE_VERSION) {
                return@withContext null
            }

            initializationVector = Base64.decode(parts[1], Base64.NO_WRAP)
            ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            if (initializationVector.size != IV_SIZE_BYTES || ciphertext.isEmpty()) {
                return@withContext null
            }

            plaintext = cipher.decrypt(EncryptedSecret(initializationVector, ciphertext))
            deserialize(plaintext)
        } catch (_: Exception) {
            null
        } finally {
            plaintext.fill(0)
            initializationVector.fill(0)
            ciphertext.fill(0)
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        check(preferences.edit().clear().commit()) { "Unable to clear encrypted credentials" }
    }

    private fun serialize(credentials: Credentials): ByteArray {
        var appleIdBytes = ByteArray(0)
        var passwordBytes = ByteArray(0)
        val passwordChars = credentials.appSpecificPassword.copyOf()
        try {
            appleIdBytes = credentials.appleId.toByteArray(StandardCharsets.UTF_8)
            passwordBytes = encodeUtf8(passwordChars)
            require(appleIdBytes.size <= MAX_FIELD_BYTES && passwordBytes.size <= MAX_FIELD_BYTES) {
                "Credential field is too large"
            }
            return ByteBuffer.allocate(HEADER_SIZE_BYTES + appleIdBytes.size + passwordBytes.size)
                .putInt(appleIdBytes.size)
                .putInt(passwordBytes.size)
                .put(appleIdBytes)
                .put(passwordBytes)
                .array()
        } finally {
            passwordChars.fill('\u0000')
            appleIdBytes.fill(0)
            passwordBytes.fill(0)
        }
    }

    private fun deserialize(plaintext: ByteArray): Credentials {
        var appleIdBytes = ByteArray(0)
        var passwordBytes = ByteArray(0)
        var appleIdChars: CharArray? = null
        var passwordChars: CharArray? = null
        try {
            require(plaintext.size >= HEADER_SIZE_BYTES) { "Credential payload is truncated" }
            val buffer = ByteBuffer.wrap(plaintext)
            val appleIdLength = buffer.int
            val passwordLength = buffer.int
            require(appleIdLength in 0..MAX_FIELD_BYTES && passwordLength in 0..MAX_FIELD_BYTES) {
                "Credential field length is invalid"
            }
            require(buffer.remaining() == appleIdLength + passwordLength) {
                "Credential payload length is invalid"
            }

            appleIdBytes = ByteArray(appleIdLength).also(buffer::get)
            passwordBytes = ByteArray(passwordLength).also(buffer::get)
            appleIdChars = decodeUtf8(appleIdBytes)
            passwordChars = decodeUtf8(passwordBytes)
            val credentials = Credentials(String(appleIdChars), passwordChars)
            passwordChars = null // Ownership transfers to the Credentials caller.
            return credentials
        } finally {
            appleIdBytes.fill(0)
            passwordBytes.fill(0)
            appleIdChars?.fill('\u0000')
            passwordChars?.fill('\u0000')
        }
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(chars))
        return try {
            ByteArray(encoded.remaining()).also(encoded::get)
        } finally {
            if (encoded.hasArray()) encoded.array().fill(0)
        }
    }

    private fun decodeUtf8(bytes: ByteArray): CharArray {
        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        return try {
            CharArray(decoded.remaining()).also(decoded::get)
        } finally {
            if (decoded.hasArray()) decoded.array().fill('\u0000')
        }
    }

    companion object {
        const val PREFERENCES_NAME = "icloud_credentials"

        private const val ENVELOPE_KEY = "encrypted_credentials"
        private const val ENVELOPE_VERSION = "v1"
        private const val ENVELOPE_SEPARATOR = ":"
        private const val ENVELOPE_PARTS = 3
        private const val IV_SIZE_BYTES = 12
        private const val HEADER_SIZE_BYTES = Int.SIZE_BYTES * 2
        private const val MAX_FIELD_BYTES = 1024 * 1024
    }
}
