package com.victor2319.ioscalendar.auth

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCredentialsStoreInstrumentedTest {
    @Test
    fun androidKeystoreRoundTripUsesNonExportableKeyFreshIvAndNoPlaintextPreferences() = runTest {
        // Catches replacing Android Keystore with an exportable key, reusing a GCM IV, or persisting plaintext.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            KeystoreCredentialsStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        preferences.edit().clear().commit()
        val store = KeystoreCredentialsStore(context)
        val password = "device-only-secret".toCharArray()
        var loaded: Credentials? = null
        try {
            store.save(Credentials("device@example.test", password))
            val firstEnvelope = preferences.all.values.single() as String
            val firstIv = Base64.decode(firstEnvelope.split(':')[1], Base64.NO_WRAP)

            loaded = store.load()
            val result = loaded ?: throw AssertionError("Expected stored credentials")
            assertTrue(result.appleId == "device@example.test")
            // CharArray is content-compared explicitly; the returned array is owned by this caller.
            assertArrayEquals(password, result.appSpecificPassword)
            assertFalse(password === result.appSpecificPassword)
            assertTrue(firstIv.size == 12)

            store.save(Credentials("device@example.test", password))
            val secondEnvelope = preferences.all.values.single() as String
            val secondIv = Base64.decode(secondEnvelope.split(':')[1], Base64.NO_WRAP)
            assertNotEquals(firstEnvelope, secondEnvelope)
            assertFalse(firstIv.contentEquals(secondIv))

            val persisted = preferences.all.values.joinToString(separator = "|")
            assertFalse(persisted.contains("device@example.test"))
            assertFalse(persisted.contains("device-only-secret"))

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(AndroidKeystoreSecretCipher.KEY_ALIAS, null)
            assertNotNull(key)
            assertNull(key.encoded)
        } finally {
            password.fill('\u0000')
            loaded?.appSpecificPassword?.fill('\u0000')
            store.clear()
        }
    }
}
