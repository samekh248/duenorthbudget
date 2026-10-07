package app.duenorth.budget

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.duenorth.budget.core.SecretStore

/** Keystore-backed prefs. The budget password and the server token never go in a budget file. */
class AndroidSecretStore(
    context: Context,
) : SecretStore {
    private val preferences =
        EncryptedSharedPreferences.create(
            "due-north-secrets",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

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
}
