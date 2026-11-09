package app.duenorth.budget

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidSecretStoreTest {
    /** AES-GCM with a key in memory, since Robolectric has no Android Keystore. */
    private class TestCipher : AndroidSecretStore.SecretCipher {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var failSeal = false

        override fun seal(plain: String): String {
            if (failSeal) throw IllegalStateException("keystore unavailable")
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return java.util.Base64
                .getEncoder()
                .encodeToString(iv + cipher.doFinal(plain.toByteArray()))
        }

        override fun open(sealed: String): String {
            val bytes =
                java.util.Base64
                    .getDecoder()
                    .decode(sealed)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
            return String(cipher.doFinal(bytes, 12, bytes.size - 12))
        }
    }

    private lateinit var context: Context
    private lateinit var target: SharedPreferences
    private lateinit var legacyFile: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        target = context.getSharedPreferences(AndroidSecretStore.FILE, Context.MODE_PRIVATE)
        target.edit().clear().commit()
        legacyFile = File(context.dataDir, "shared_prefs/${AndroidSecretStore.LEGACY_FILE}.xml")
    }

    /** Writes the old store's file, standing in for EncryptedSharedPreferences with plain values. */
    private fun legacyHolding(vararg values: Pair<String, String>): SharedPreferences {
        val legacy = context.getSharedPreferences(AndroidSecretStore.LEGACY_FILE, Context.MODE_PRIVATE)
        legacy.edit().apply { values.forEach { (k, v) -> putString(k, v) } }.commit()
        assertTrue(legacyFile.exists())
        return legacy
    }

    @Test
    fun valuesAreSealedAtRestAndReadBack() {
        val store = AndroidSecretStore(target, TestCipher())
        store.put("token", "s3cret")
        assertEquals("s3cret", store.get("token"))
        assertFalse("the file never holds the plain value", target.getString("token", null)!!.contains("s3cret"))
        store.remove("token")
        assertNull(store.get("token"))
    }

    @Test
    fun aValueThatCannotBeOpenedReadsAsMissing() {
        target.edit().putString("token", "bm90IHNlYWxlZA==").commit()
        assertNull(AndroidSecretStore(target, TestCipher()).get("token"))
    }

    @Test
    fun theOldStoreMovesOverAndIsDeleted() {
        val legacy = legacyHolding("token" to "from-old", "budget-password" to "pw")
        val cipher = TestCipher()
        var keyReset = false
        AndroidSecretStore.migrate(context, target, cipher, readLegacy = { legacy }, resetLegacyKey = { keyReset = true })
        val store = AndroidSecretStore(target, cipher)
        assertEquals("from-old", store.get("token"))
        assertEquals("pw", store.get("budget-password"))
        assertFalse(legacyFile.exists())
        assertTrue(keyReset)
    }

    @Test
    fun aValueAlreadyInTheNewStoreWins() {
        val cipher = TestCipher()
        AndroidSecretStore(target, cipher).put("token", "newer")
        val legacy = legacyHolding("token" to "older")
        AndroidSecretStore.migrate(context, target, cipher, readLegacy = { legacy }, resetLegacyKey = {})
        assertEquals("newer", AndroidSecretStore(target, cipher).get("token"))
    }

    @Test
    fun anUnreadableOldStoreIsDroppedInsteadOfCrashing() {
        legacyHolding("__androidx_security_crypto_encrypted_prefs_key_keyset__" to "truncated")
        var keyReset = false
        AndroidSecretStore.migrate(
            context,
            target,
            TestCipher(),
            readLegacy = { throw IOException("While parsing a protocol message, the input ended unexpectedly") },
            resetLegacyKey = { keyReset = true },
        )
        assertFalse(legacyFile.exists())
        assertTrue(keyReset)
        assertTrue(target.all.isEmpty())
    }

    @Test
    fun aFailedMoveKeepsTheOldStoreForNextLaunch() {
        val legacy = legacyHolding("token" to "from-old")
        val cipher = TestCipher().apply { failSeal = true }
        AndroidSecretStore.migrate(context, target, cipher, readLegacy = { legacy }, resetLegacyKey = { error("not reset") })
        assertTrue(legacyFile.exists())
        assertTrue(target.all.isEmpty())
    }

    @Test
    fun noOldStoreMeansNothingToDo() {
        AndroidSecretStore.migrate(
            context,
            target,
            TestCipher(),
            readLegacy = { error("not read") },
            resetLegacyKey = { error("not reset") },
        )
        assertTrue(target.all.isEmpty())
    }
}
