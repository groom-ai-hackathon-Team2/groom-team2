package com.groomteam2.dopamind.ai

import com.groomteam2.dopamind.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 인스턴스 + Gemini 호출 래퍼.
 *
 * - API 키가 없거나(BuildConfig.GEMINI_API_KEY 비어있음) 호출이 실패하면 예외를 던진다.
 *   상위 레이어(CoachingEngine)에서 fallback 멘트로 대체.
 * - 타임아웃 15초: 코치 팝업 응답성을 해치지 않도록 짧게.
 */
class GeminiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            // 디버그 빌드에서만 BODY 까지 로깅. 릴리스 빌드에서는 NONE 으로 두어 키 노출 방지.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: GeminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeminiApi::class.java)

    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * 시스템 프롬프트 + 사용자 프롬프트 한 쌍으로 호출.
     * 응답 candidates[0].content.parts[0].text 의 raw 문자열을 반환.
     */
    suspend fun generateJson(systemPrompt: String, userPrompt: String): String {
        check(isConfigured) { "GEMINI_API_KEY 가 설정되지 않았습니다." }

        val req = GeminiRequest(
            contents = listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(userPrompt))),
            ),
            systemInstruction = GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(systemPrompt)),
            ),
        )

        val resp = api.generate(
            model = BuildConfig.GEMINI_MODEL,
            apiKey = BuildConfig.GEMINI_API_KEY,
            request = req,
        )

        return resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: error("Gemini 응답에 텍스트가 없습니다.")
    }
}
