package com.pakkabaat.app.data.model

data class SupportedLanguage(val code: String, val displayName: String)

object LanguageCatalog {
    // This list originated from Bhashini's 22-language coverage (spec 6.3), kept here as
    // the language menu for onboarding + Gemini prompt naming. whisper.cpp's own language
    // coverage differs slightly (it's a generalist multilingual model, not India-specific) —
    // language names shown here just tell the user what to expect; recognition quality per
    // language depends on whisper.cpp's own training data, not this list.
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
