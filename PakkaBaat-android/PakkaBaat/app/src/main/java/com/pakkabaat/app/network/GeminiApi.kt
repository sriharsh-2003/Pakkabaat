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
data class GeminiGenerationConfig(val maxOutputTokens: Int = 1200, val temperature: Double = 0.2)

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
