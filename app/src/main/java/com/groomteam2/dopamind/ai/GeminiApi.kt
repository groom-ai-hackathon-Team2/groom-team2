package com.groomteam2.dopamind.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gemini Generative Language API (v1beta) 를 호출하는 Retrofit 인터페이스.
 *
 * 엔드포인트: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=...
 * 모델은 BuildConfig.GEMINI_MODEL 에서 주입(기본: gemini-1.5-flash).
 *
 * 응답에서 우리가 사용하는 부분은 candidates[0].content.parts[0].text 한 줄.
 * 그 안에 우리가 원하는 JSON 이 담겨 오도록 프롬프트로 강제.
 */
interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest,
    ): GeminiResponse
}

// ── 요청 ──────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
    @SerialName("systemInstruction")
    val systemInstruction: GeminiContent? = null,
)

@Serializable
data class GeminiContent(
    val role: String = "user",        // "user" | "model" | "system"(systemInstruction 으로 별도 전달)
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    val text: String,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.9f,    // 코칭 멘트는 살짝 캐주얼한 변형이 보이도록 약간 높게
    val maxOutputTokens: Int = 256,
    val responseMimeType: String = "application/json",
)

// ── 응답 ──────────────────────────────────────────────────────────

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)
