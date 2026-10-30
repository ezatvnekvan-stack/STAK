package app.stak.nativeapp.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context, namespace:String = "") {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "${namespace}stak_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putSecret(key: String, value: String?) {
        if (value == null) prefs.edit().remove(key).apply() else prefs.edit().putString(key, value).apply()
    }

    fun putSecretChecked(key:String,value:String) {
        check(prefs.edit().putString(key,value).commit()) { "Encrypted settings could not be saved." }
        check(prefs.getString(key,null)==value) { "Encrypted settings verification failed." }
    }

    fun getSecret(key: String): String? = prefs.getString(key, null)
}
