package app.duenorth.budget

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.duenorth.budget.core.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The server token and budget passwords, each sealed with an AES key that lives in the Android Keystore and
 * never leaves it. The budget password and the server token never go in a budget file.
 *
 * This replaces androidx.security's EncryptedSharedPreferences, which is deprecated. The first open moves
 * whatever the old store held into this one and deletes the old file and its key. The old library stays in
 * the app only to read that file once; it can go once every phone has opened this version.
 */
class AndroidSecretStore internal constructor(
    private val preferences: SharedPreferences,
    private val cipher: SecretCipher,
) : SecretStore {
    constructor(context: Context) : this(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
        KeystoreCipher(),
    ) {
        migrate(context, preferences, cipher)
    }

    /** The value, or null when there is none or it can't be opened (its key is gone); the app then asks again. */
    override fun get(key: String): String? {
        val sealed = preferences.getString(key, null) ?: return null
        return runCatching { cipher.open(sealed) }
            .onFailure { Log.w("DueNorth", "a saved secret could not be opened", it) }
            .getOrNull()
    }

    override fun put(
        key: String,
        value: String,
    ) {
        preferences.edit().putString(key, cipher.seal(value)).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    /** Seals and opens one value; the sealed form is text, so it fits in shared preferences. */
    interface SecretCipher {
        fun seal(plain: String): String

        fun open(sealed: String): String
    }

    /** AES-256-GCM under a Keystore key; the sealed form is base64 of the 12-byte IV and the ciphertext. */
    class KeystoreCipher(
        private val alias: String = KEY_ALIAS,
    ) : SecretCipher {
        override fun seal(plain: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(iv + body, Base64.NO_WRAP)
        }

        override fun open(sealed: String): String {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            require(bytes.size > IV_BYTES) { "sealed value too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }

        private fun key(): SecretKey {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }

        private companion object {
            const val KEYSTORE = "AndroidKeyStore"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val IV_BYTES = 12
            const val TAG_BITS = 128
        }
    }

    companion object {
        const val FILE = "due-north-secrets-v2"
        private const val KEY_ALIAS = "due-north-secrets-v2"

        /** The deprecated EncryptedSharedPreferences file, read once to move its values here. */
        const val LEGACY_FILE = "due-north-secrets"
        private const val LEGACY_MASTER_KEY_ALIAS = "_androidx_security_master_key_"

        /**
         * Moves every value from the old store into [target], sealed with [cipher], then deletes the old file and
         * its key. A value [target] already has is kept. When the old store can't be read (a cut-off write, or a
         * keyset whose key is gone) its values are lost, as they were before, and the app asks for them again.
         * When sealing fails the old file is kept, so the next launch tries again.
         */
        internal fun migrate(
            context: Context,
            target: SharedPreferences,
            cipher: SecretCipher,
            readLegacy: (Context) -> SharedPreferences = ::legacy,
            resetLegacyKey: () -> Unit = ::deleteLegacyKey,
        ) {
            if (!legacyExists(context)) return
            val old =
                try {
                    readLegacy(context).all
                } catch (error: Exception) {
                    Log.w("DueNorth", "old secret store unreadable, starting over", error)
                    emptyMap<String, Any?>()
                }
            try {
                val edit = target.edit()
                for ((key, value) in old) {
                    if (value is String && !target.contains(key)) edit.putString(key, cipher.seal(value))
                }
                if (!edit.commit()) return
            } catch (error: Exception) {
                Log.w("DueNorth", "could not move the old secret store yet", error)
                return
            }
            context.deleteSharedPreferences(LEGACY_FILE)
            runCatching(resetLegacyKey)
        }

        private fun legacyExists(context: Context): Boolean = context.dataDir.resolve("shared_prefs/$LEGACY_FILE.xml").exists()

        private fun legacy(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                LEGACY_FILE,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private fun deleteLegacyKey() {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(LEGACY_MASTER_KEY_ALIAS)
        }
    }
}
