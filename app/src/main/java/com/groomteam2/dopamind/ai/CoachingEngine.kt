package com.groomteam2.dopamind.ai

import com.groomteam2.dopamind.analyzer.VulnerableTimeLearner
import com.groomteam2.dopamind.analyzer.ZombieDetection
import com.groomteam2.dopamind.data.repo.PointRepository
import com.groomteam2.dopamind.data.repo.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * 좀비 판정 → 코치 멘트/챌린지 시간/보상 포인트 결정.
 *
 * 흐름:
 *  1) coach(detection): 사용자 프로필+포인트+취약시간 정보를 모아 CoachingContext 구성
 *  2) Gemini 호출 → JSON 응답을 CoachAdvice 로 파싱
 *  3) 실패(키 미설정/네트워크 오류/형식 오류) 시 fallback 멘트 사용
 *  4) 취약 시간대면 rewardPoints 를 3배로 보정 (모델이 무시했을 경우 안전망)
 */
class CoachingEngine(
    private val gemini: GeminiClient,
    private val userRepo: UserRepository,
    private val pointRepo: PointRepository,
    private val vulnerableTimeLearner: VulnerableTimeLearner,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun coach(detection: ZombieDetection): CoachAdvice {
        val profile = userRepo.currentProfile()
        val balance = runCatching { pointRepo.balance.first() }.getOrDefault(0)

        val ctx = CoachingContext(
            job = profile?.job ?: "직장인",
            schedule = profile?.schedule ?: "일정 미입력",
            hourOfDay = detection.hourOfDay,
            isVulnerableHour = detection.isVulnerableHour,
            packageLabel = friendlyAppName(detection.packageName),
            avgIntervalMs = detection.avgIntervalMs,
            eventCount = detection.eventCount,
            totalPoints = balance,
        )

        // Gemini 호출은 실패해도 사용자 흐름이 끊기면 안 됨 → 항상 try/catch.
        val advice: CoachAdvice = if (gemini.isConfigured) {
            runCatching {
                val raw = gemini.generateJson(
                    systemPrompt = CoachingPrompts.SYSTEM,
                    userPrompt = CoachingPrompts.buildUserPrompt(ctx),
                )
                json.decodeFromString(CoachAdviceDto.serializer(), extractJson(raw)).toAdvice()
            }.getOrElse { fallback(ctx) }
        } else {
            fallback(ctx)
        }

        // 취약 시간대 보상 3배 안전망(모델이 잊은 경우 대비).
        val finalReward = if (detection.isVulnerableHour) advice.rewardPoints * 3 else advice.rewardPoints

        return advice.copy(
            rewardPoints = finalReward.coerceIn(10, 600),
            challengeMinutes = advice.challengeMinutes.coerceIn(5, 60),
        )
    }

    /**
     * Gemini 가 가끔 ```json ... ``` 코드블록을 섞을 때를 대비해 첫 { ... } 만 추출.
     */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }

    private fun fallback(ctx: CoachingContext): CoachAdvice {
        // API 키가 없거나 호출 실패 시에도 시연이 끊기지 않도록 사전 정의 멘트.
        val msg = if (ctx.isVulnerableHour) {
            "지금은 너의 위험 시간대! 30분만 끄고 평소의 3배 받아갈래?"
        } else {
            "도파민 좀비 상태 감지! 지금 끄면 ${BASE_REWARD}p, 콜?"
        }
        val reward = if (ctx.isVulnerableHour) BASE_REWARD * 3 else BASE_REWARD
        return CoachAdvice(message = msg, challengeMinutes = 30, rewardPoints = reward)
    }

    private fun friendlyAppName(pkg: String): String = when (pkg) {
        "com.instagram.android" -> "인스타그램 릴스"
        "com.google.android.youtube" -> "유튜브 숏츠"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "틱톡"
        else -> pkg
    }

    companion object {
        private const val BASE_REWARD = 50
    }
}

/**
 * 코치의 최종 결정.
 * Overlay 가 사용하는 도메인 모델.
 */
data class CoachAdvice(
    val message: String,
    val challengeMinutes: Int,
    val rewardPoints: Int,
)

/** Gemini JSON 응답 매핑용 DTO. */
@Serializable
private data class CoachAdviceDto(
    @SerialName("message") val message: String,
    @SerialName("challengeMinutes") val challengeMinutes: Int = 30,
    @SerialName("rewardPoints") val rewardPoints: Int = 50,
) {
    fun toAdvice() = CoachAdvice(
        message = message.ifBlank { "잠깐 쉴 시간이야 🤝" },
        challengeMinutes = max(5, challengeMinutes),
        rewardPoints = max(10, rewardPoints),
    )
}
