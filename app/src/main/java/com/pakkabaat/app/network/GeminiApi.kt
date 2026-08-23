package com.pakkabaat.app.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/*
 * Google's Gemini API (via Google AI Studio, aistudio.google.com) — chosen because the
 * spec itself lists "Claude API or Gemini API" as interchangeable options for the
 * structuring LLM (sections 10, 12), and Gemini's Flash tier is genuinely free with no
 * card required (unlike Anthropic's pay-as-you-go API).
 *
 * Endpoint shape: POST /v1beta/models/{model}:generateContent, key sent as a header.
 */

data class GeminiPart(val text: String)
data class GeminiContent(val role: String? = null, val parts: List<GeminiPart>)
data class GeminiSystemInstruction(val parts: List<GeminiPart>)

// gemini-2.5-flash has "thinking" turned on by default, and thinking tokens are billed
// out of the SAME maxOutputTokens budget as the visible answer. With the old 1200-token
// cap, the model could burn the entire budget on internal thinking and return an EMPTY
// candidate — no text, no error — which is exactly what made structuring silently
// "not work": parse() got an empty string, produced a garbage/blank document, and the
// worker had nothing useful to show. thinkingBudget=0 turns thinking off for this
// deterministic extraction task (we don't need reasoning, just faithful extraction),
// which both fixes the empty-response bug and uses noticeably fewer tokens/credits per
// call. maxOutputTokens is also raised so a longer transcript's structured output can't
// get cut off mid-JSON either.
data class GeminiThinkingConfig(val thinkingBudget: Int = 0)
data class GeminiGenerationConfig(
    val maxOutputTokens: Int = 4096,
    val temperature: Double = 0.2,
    val thinkingConfig: GeminiThinkingConfig = GeminiThinkingConfig()
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

data class GeminiCandidate(val content: GeminiContent?, val finishReason: String?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GeminiRequest
    ): GeminiResponse
}
