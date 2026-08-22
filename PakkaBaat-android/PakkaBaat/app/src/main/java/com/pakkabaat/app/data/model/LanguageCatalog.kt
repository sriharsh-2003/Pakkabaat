package com.pakkabaat.app.data.model

data class SupportedLanguage(val code: String, val displayName: String)

object LanguageCatalog {
    // Bhashini's ASR/NMT/TTS coverage per spec section 6.3. Not all 22 languages have
    // equally mature ASR service coverage yet — this is the full advertised set;
    // Pipeline Config calls will surface which serviceId is actually available per language.
    val all = listOf(
        SupportedLanguage("hi", "हिन्दी (Hindi)"),
        SupportedLanguage("en", "English"),
        SupportedLanguage("mr", "मराठी (Marathi)"),
        SupportedLanguage("ta", "தமிழ் (Tamil)"),
        SupportedLanguage("te", "తెలుగు (Telugu)"),
        SupportedLanguage("bn", "বাংলা (Bengali)"),
        SupportedLanguage("gu", "ગુજરાતી (Gujarati)"),
        SupportedLanguage("kn", "ಕನ್ನಡ (Kannada)"),
        SupportedLanguage("ml", "മലയാളം (Malayalam)"),
        SupportedLanguage("pa", "ਪੰਜਾਬੀ (Punjabi)"),
        SupportedLanguage("or", "ଓଡ଼ିଆ (Odia)"),
        SupportedLanguage("as", "অসমীয়া (Assamese)"),
        SupportedLanguage("ur", "اردو (Urdu)")
    )

    fun nameFor(code: String): String = all.firstOrNull { it.code == code }?.displayName ?: code
}
