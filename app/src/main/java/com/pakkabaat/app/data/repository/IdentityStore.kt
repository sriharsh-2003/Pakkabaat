package com.pakkabaat.app.data.repository

import android.content.Context
import java.util.UUID

/**
 * Persists the user's own identity (userId, display name, preferred language) across app
 * restarts. Before this existed, SessionViewModel only ever held this in in-memory
 * StateFlow state set from OnboardingScreen/SplashLanguageScreen — so every process
 * restart lost it and sent the user back through the splash + onboarding flow, and
 * there was no way to edit the name/language afterwards (SettingsScreen only ever
 * touched the Gemini API key).
 *
 * Not sensitive data, so plain SharedPreferences is enough here (no need for the
 * EncryptedSharedPreferences overhead used for the Gemini key in ApiKeyStore).
 */
class IdentityStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasIdentity(): Boolean = prefs.contains(KEY_NAME)

    fun getUserId(): String {
        val existing = prefs.getString(KEY_USER_ID, null)
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, generated).apply()
        return generated
    }

    fun getName(): String? = prefs.getString(KEY_NAME, null)

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, null) ?: "hi"

    /** Called once, from onboarding, the first time the user sets up the app. */
    fun saveIdentity(name: String, language: String) {
        prefs.edit()
            .putString(KEY_NAME, name.trim())
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /** Called from Settings, any time after onboarding, to edit name/language. */
    fun updateProfile(name: String, language: String) = saveIdentity(name, language)

    companion object {
        private const val PREFS_NAME = "pakkabaat_identity_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_LANGUAGE = "language"
    }
}
