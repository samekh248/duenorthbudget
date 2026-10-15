package app.duenorth.budget

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidSecretStoreTest {
    @Test
    fun anUnreadableKeysetStartsTheStoreOverInsteadOfCrashing() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(AndroidSecretStore.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "truncated")
            .commit()
        val file = File(context.dataDir, "shared_prefs/${AndroidSecretStore.FILE}.xml")
        assertTrue(file.exists())

        val fresh = context.getSharedPreferences("fresh-secrets", Context.MODE_PRIVATE)
        var attempts = 0
        var keyReset = false
        val opened =
            AndroidSecretStore.open(
                context,
                create = {
                    attempts++
                    if (attempts == 1) {
                        throw IOException("While parsing a protocol message, the input ended unexpectedly")
                    }
                    assertFalse("the broken file is gone before the retry", file.exists())
                    fresh
                },
                resetKey = { keyReset = true },
            )

        assertSame(fresh, opened)
        assertEquals(2, attempts)
        assertTrue(keyReset)
    }

    @Test
    fun aReadableStoreOpensOnceAndKeepsItsFile() {
        val context = RuntimeEnvironment.getApplication()
        val prefs: SharedPreferences = context.getSharedPreferences(AndroidSecretStore.FILE, Context.MODE_PRIVATE)
        prefs.edit().putString("token", "kept").commit()
        var attempts = 0
        val opened =
            AndroidSecretStore.open(context, create = {
                attempts++
                prefs
            }, resetKey = { error("not reset") })
        assertEquals(1, attempts)
        assertEquals("kept", opened.getString("token", null))
    }
}
