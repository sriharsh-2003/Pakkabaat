package com.pakkabaat.app.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * BYOK (bring your own key) for the Gemini structuring call. For a small testing
 * group, this sidesteps two real problems with shipping a developer-owned key in a
 * public build: (1) any string in the APK is trivially extractable, and (2) a free-tier
 * quota is shared across every user of a single key. Each person pastes in their own
 * free key from aistudio.google.com/apikey; it's stored encrypted, locally, on their
 * own device only, and used only for that device's own Gemini calls.
 *
 * For a real public release beyond a testing group, swap this for the backend-proxy
 * approach instead — a device-stored key still means each user needs to know what an
 * API key is, which doesn't fit this app's actual target users (spec section 3).
 */
class ApiKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pakkabaat_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getGeminiKey(): String? = prefs.getString(KEY_GEMINI, null)

    fun setGeminiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun clearGeminiKey() {
        prefs.edit().remove(KEY_GEMINI).apply()
    }

    companion object {
        private const val KEY_GEMINI = "gemini_api_key"
    }
}
