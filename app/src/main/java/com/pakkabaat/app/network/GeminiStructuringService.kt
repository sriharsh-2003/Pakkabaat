package com.pakkabaat.app.network

import com.pakkabaat.app.BuildConfig
import com.pakkabaat.app.data.model.AgreementType
import org.json.JSONObject

data class StructuringResult(
    val humanReadableText: String,
    val agreementType: AgreementType,
    val amount: Double?,
    val currency: String,
    val dateOfConversationIso: String?,
    val terms: List<String>,
    val unclearItems: List<String>,
    val modelUsed: String
)

class GeminiStructuringService(
    private val api: GeminiApiService = NetworkModule.geminiApi,
    private val model: String = "gemini-2.5-flash", // free-tier model; swap to flash-lite for even higher free rate limits
    /** BYOK: pass the tester's own key from ApiKeyStore. Falls back to BuildConfig's key, if set, for convenience during solo dev testing. */
    private val apiKey: String? = null
) {

    suspend fun structure(
        transcript: String,
        languageCode: String,
        languageName: String,
        partyAName: String,
        partyBName: String
    ): StructuringResult {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        check(key.isNotBlank()) {
            "No Gemini API key available. Enter one in Settings (get a free key at " +
                "aistudio.google.com/apikey), or set PAKKABAAT_GEMINI_API_KEY in gradle.properties."
        }

        val request = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(StructuringPrompts.buildSystemPrompt()))),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(
                        StructuringPrompts.buildUserPrompt(transcript, languageCode, languageName, partyAName, partyBName)
                    ))
                )
            )
        )

        val response = api.generateContent(model = model, apiKey = key, body = request)
        val fullText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        // With thinkingBudget=0 this should never come back blank anymore, but keep a
        // clear, specific error instead of an obscure NPE further down if it ever does
        // (e.g. the call was blocked by a safety filter).
        if (fullText.isNullOrBlank()) {
            val finishReason = response.candidates?.firstOrNull()?.finishReason
            error("Gemini returned no text content (finishReason=$finishReason).")
        }

        return parse(fullText)
    }

    private fun parse(fullText: String): StructuringResult {
        val marker = "###JSON###"
        val markerIndex = fullText.indexOf(marker)

        val humanText = if (markerIndex >= 0) fullText.substring(0, markerIndex).trim() else fullText.trim()
        var jsonText = if (markerIndex >= 0) fullText.substring(markerIndex + marker.length).trim() else "{}"
        // Gemini sometimes wraps JSON in ```json fences despite instructions not to — strip them defensively.
        jsonText = jsonText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val json = runCatching { JSONObject(jsonText) }.getOrElse { JSONObject() }

        val agreementType = runCatching {
            AgreementType.valueOf(json.optString("agreementType", "other").uppercase())
        }.getOrDefault(AgreementType.OTHER)

        val amount = if (json.has("amount") && !json.isNull("amount")) json.optDouble("amount") else null
        val terms = json.optJSONArray("terms")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()
        val unclear = json.optJSONArray("unclearItems")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()

        return StructuringResult(
            humanReadableText = humanText,
            agreementType = agreementType,
            amount = amount,
            currency = json.optString("currency", "INR"),
            dateOfConversationIso = json.optString("dateOfConversationIso").takeIf { it.isNotBlank() },
            terms = terms,
            unclearItems = unclear,
            modelUsed = "gemini-2.5-flash"
        )
    }
}
