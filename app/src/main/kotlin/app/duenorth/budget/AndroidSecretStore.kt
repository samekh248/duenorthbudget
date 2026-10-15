package app.duenorth.budget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.duenorth.budget.core.SecretStore
import java.security.KeyStore

/** Keystore-backed prefs. The budget password and the server token never go in a budget file. */
class AndroidSecretStore(
    context: Context,
) : SecretStore {
    private val preferences = open(context)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(
        key: String,
        value: String,
    ) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    companion object {
        const val FILE = "due-north-secrets"
        private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

        /**
         * Opens the store, starting it over when its keyset can't be read (a cut-off write, or a
         * backup restored without its keystore key). The saved sign-in and budget passwords are
         * lost then, and the app asks for them again instead of crashing on every launch.
         */
        internal fun open(
            context: Context,
            create: (Context) -> SharedPreferences = ::encrypted,
            resetKey: () -> Unit = ::deleteMasterKey,
        ): SharedPreferences =
            try {
                create(context)
            } catch (error: Exception) {
                Log.w("DueNorth", "secret store unreadable, starting it over", error)
                context.deleteSharedPreferences(FILE)
                runCatching(resetKey)
                create(context)
            }

        private fun encrypted(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                FILE,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private fun deleteMasterKey() {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }
    }
}
