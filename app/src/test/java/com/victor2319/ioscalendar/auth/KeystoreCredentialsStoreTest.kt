package com.victor2319.ioscalendar.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeystoreCredentialsStoreTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var cipher: RecordingFakeCipher
    private lateinit var store: KeystoreCredentialsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        cipher = RecordingFakeCipher()
        store = KeystoreCredentialsStore(preferences, cipher)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun saveAndLoadRoundTripsCredentialsWithoutPersistingPlaintext() = runTest {
        // Catches persisting either credential field directly or decoding only part of the encrypted payload.
        val password = "abcd-efgh-ijkl-mnop".toCharArray()
        var loaded: Credentials? = null
        try {
            store.save(Credentials("calendar.owner@example.test", password))

            val persisted = preferences.all.values.joinToString(separator = "|")
            assertFalse(persisted.contains("calendar.owner@example.test"))
            assertFalse(persisted.contains("abcd-efgh-ijkl-mnop"))

            loaded = store.load()
            val result = loaded ?: throw AssertionError("Expected stored credentials")
            assertTrue(result.appleId == "calendar.owner@example.test")
            // CharArray uses reference equality, so content and ownership are asserted explicitly.
            assertArrayEquals(password, result.appSpecificPassword)
            assertFalse(password === result.appSpecificPassword)
        } finally {
            // The caller owns input/output password arrays and clears them at its lifecycle boundary.
            password.fill('\u0000')
            loaded?.appSpecificPassword?.fill('\u0000')
        }
    }

    @Test
    fun saveUsesAFreshTwelveByteInitializationVectorEveryTime() = runTest {
        // Catches reusing a GCM IV or dropping the IV from the versioned persisted envelope.
        val password = "same-password".toCharArray()
        try {
            store.save(Credentials("same@example.test", password))
            val firstEnvelope = preferences.all.values.single() as String

            store.save(Credentials("same@example.test", password))
            val secondEnvelope = preferences.all.values.single() as String

            assertNotEquals(firstEnvelope, secondEnvelope)
            assertTrue(cipher.initializationVectors.all { it.size == 12 })
            assertFalse(cipher.initializationVectors[0].contentEquals(cipher.initializationVectors[1]))
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun clearRemovesTheStoredCredentials() = runTest {
        // Catches clear leaving a decryptable credential envelope behind.
        val password = "clear-this-secret".toCharArray()
        try {
            store.save(Credentials("clear@example.test", password))

            store.clear()

            assertTrue(preferences.all.isEmpty())
            assertNull(store.load())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun corruptCiphertextFailsClosedWithoutReturningPartialCredentials() = runTest {
        // Catches authentication/decode failures leaking an Apple ID or a partial password object.
        val password = "valid-before-corruption".toCharArray()
        try {
            store.save(Credentials("corrupt@example.test", password))
            val key = preferences.all.keys.single()
            val envelope = preferences.getString(key, null)!!
            val parts = envelope.split(':').toMutableList()
            parts[2] = parts[2].reversed()
            preferences.edit().putString(key, parts.joinToString(":")).commit()

            assertNull(store.load())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun unsupportedEnvelopeVersionFailsClosed() = runTest {
        // Catches treating an unknown storage format as the current one and returning misparsed credentials.
        val password = "future-version".toCharArray()
        try {
            store.save(Credentials("future@example.test", password))
            val key = preferences.all.keys.single()
            val envelope = preferences.getString(key, null)!!
            preferences.edit().putString(key, envelope.replaceFirst("v1:", "v2:")).commit()

            assertNull(store.load())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun wrongTypedPreferenceFailsClosed() = runTest {
        // Catches malformed preference state escaping as ClassCastException instead of no credentials.
        val password = "wrong-type-secret".toCharArray()
        try {
            store.save(Credentials("wrong-type@example.test", password))
            val key = preferences.all.keys.single()
            preferences.edit().clear().putInt(key, 1).commit()

            assertNull(store.load())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun credentialsToStringRedactsBothFields() {
        // Catches generated data-class rendering exposing an Apple ID or password characters in logs.
        val password = "render-secret".toCharArray()
        try {
            val rendered = Credentials("render@example.test", password).toString()

            assertFalse(rendered.contains("render@example.test"))
            assertFalse(rendered.contains("r, e, n, d, e, r, -, s, e, c, r, e, t"))
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun keystoreAndPreferenceWorkRunsOffTheCallingThread() = runTest {
        // Catches suspend methods doing synchronous Keystore and preferences work on a UI caller.
        val callerThread = Thread.currentThread()
        val password = "dispatcher-secret".toCharArray()
        var loaded: Credentials? = null
        try {
            store.save(Credentials("dispatcher@example.test", password))
            loaded = store.load()

            assertFalse(cipher.lastEncryptThread === callerThread)
            assertFalse(cipher.lastDecryptThread === callerThread)
        } finally {
            password.fill('\u0000')
            loaded?.appSpecificPassword?.fill('\u0000')
        }
    }

    @Test
    fun sensitiveTemporaryPlaintextArraysAreClearedAfterCipherUse() = runTest {
        // Catches leaving serialized credentials or decrypted plaintext resident in temporary byte arrays.
        val password = "temporary-secret".toCharArray()
        val expectedPassword = password.copyOf()
        var loaded: Credentials? = null
        try {
            store.save(Credentials("temporary@example.test", password))
            assertTrue(cipher.lastPlaintextPassedToEncrypt!!.all { it == 0.toByte() })
            assertArrayEquals(expectedPassword, password)

            loaded = store.load()
            val result = loaded ?: throw AssertionError("Expected stored credentials")
            assertTrue(cipher.lastPlaintextReturnedFromDecrypt!!.all { it == 0.toByte() })
            assertArrayEquals(expectedPassword, result.appSpecificPassword)
        } finally {
            password.fill('\u0000')
            expectedPassword.fill('\u0000')
            loaded?.appSpecificPassword?.fill('\u0000')
        }
    }

    private class RecordingFakeCipher : SecretCipher {
        val initializationVectors = mutableListOf<ByteArray>()
        var lastPlaintextPassedToEncrypt: ByteArray? = null
        var lastPlaintextReturnedFromDecrypt: ByteArray? = null
        var lastEncryptThread: Thread? = null
        var lastDecryptThread: Thread? = null
        private var encryptionCount = 0

        override fun encrypt(plaintext: ByteArray): EncryptedSecret {
            lastEncryptThread = Thread.currentThread()
            lastPlaintextPassedToEncrypt = plaintext
            encryptionCount += 1
            val iv = ByteArray(12) { index -> (encryptionCount + index).toByte() }
            initializationVectors += iv.copyOf()
            return EncryptedSecret(
                initializationVector = iv,
                ciphertext = plaintext.map { byte -> (byte.toInt() xor MASK).toByte() }.toByteArray(),
            )
        }

        override fun decrypt(encryptedSecret: EncryptedSecret): ByteArray {
            lastDecryptThread = Thread.currentThread()
            val plaintext = encryptedSecret.ciphertext
                .map { byte -> (byte.toInt() xor MASK).toByte() }
                .toByteArray()
            lastPlaintextReturnedFromDecrypt = plaintext
            return plaintext
        }

        private companion object {
            const val MASK = 0x5A
        }
    }

    private companion object {
        const val TEST_PREFERENCES = "test-icloud-credentials"
    }
}
