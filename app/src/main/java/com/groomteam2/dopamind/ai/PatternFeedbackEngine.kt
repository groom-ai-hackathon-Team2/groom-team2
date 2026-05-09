package com.groomteam2.dopamind.ai

import com.groomteam2.dopamind.analyzer.VulnerableTimeLearner
import com.groomteam2.dopamind.data.repo.ChallengeRepository
import com.groomteam2.dopamind.data.repo.PointRepository
import com.groomteam2.dopamind.data.repo.UserRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 홈 화면 'AI 피드백' 카드를 위한 분석/요약 엔진.
 *
 * CoachingEngine 과 분리한 이유:
 *  - 출력 형식이 다름 (JSON 강제가 아닌 자연어 2~3문장)
 *  - 입력 컨텍스트가 다름 (현재 좀비 판정값 X, 누적 통계 위주)
 *  - 호출 빈도가 다름 (사용자가 명시적으로 버튼 누를 때만)
 *
 * 첫 사용자도 의미있는 응답을 받도록 isFirstTime 분기.
 * 실패 시 fallback 자연어 메시지 반환 — 시연이 끊기지 않도록.
 */
class PatternFeedbackEngine(
    private val gemini: GeminiClient,
    private val userRepo: UserRepository,
    private val pointRepo: PointRepository,
    private val challengeRepo: ChallengeRepository,
    private val learner: VulnerableTimeLearner,
) {

    suspend fun generate(): String {
        val profile = userRepo.currentProfile()
        val totalPoints = runCatching { pointRepo.balance.first() }.getOrDefault(0)
        val earnedToday = runCatching { pointRepo.earnedToday().first() }.getOrDefault(0)
        val successToday = runCatching { challengeRepo.successCountToday().first() }.getOrDefault(0)
        val scores = runCatching { learner.scoresFlow.first() }.getOrDefault(FloatArray(24) { 0f })
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 첫 사용자 판정: 누적 데이터가 사실상 없을 때.
        val noUsageData = totalPoints == 0 && earnedToday == 0 && successToday == 0 &&
                scores.all { it == 0f }

        val ctx = FeedbackContext(
            job = profile?.job ?: "직장인",
            schedule = profile?.schedule ?: "일정 미입력",
            totalPoints = totalPoints,
            earnedToday = earnedToday,
            successToday = successToday,
            currentHour = now,
            isVulnerableHourNow = learner.isVulnerableNow(now),
            vulnerableScores = scores.toList(),
            isFirstTime = noUsageData,
        )

        if (!gemini.isConfigured) return fallback(ctx)

        return runCatching {
            gemini.generateText(
                systemPrompt = FeedbackPrompts.SYSTEM,
                userPrompt = FeedbackPrompts.buildUserPrompt(ctx),
            ).trim().take(MAX_LEN)
        }.getOrElse { fallback(ctx) }
    }

    private fun fallback(ctx: FeedbackContext): String {
        if (ctx.isFirstTime) {
            // 첫 사용자: 직업/일정에서 흔한 함정 시간대를 추측.
            val (hint, action) = firstTimeHint(ctx.job, ctx.schedule)
            return "도파민드에 온 걸 환영해! ${ctx.job}이라면 보통 $hint $action"
        }
        val peakHour = ctx.vulnerableScores
            .withIndex()
            .filter { it.value > 0f }
            .maxByOrNull { it.value }
            ?.index
        val peakHint = peakHour?.let { "${it}시 즈음에 숏폼을 자주 봤어." }
            ?: "아직 패턴이 충분히 쌓이진 않았어."
        val today = if (ctx.successToday > 0) "오늘 ${ctx.successToday}번 잘 참았어. 👏 "
        else "오늘은 아직 챌린지 성공이 없어. "
        return "$today$peakHint 내일은 그 시간 5분 전에 폰을 잠깐 멀리 둬보자."
    }

    /**
     * 직업 키워드별로 흔한 함정 시간대 + 작은 행동 제안.
     * 매우 단순한 키워드 매칭 — Gemini 가 동작하면 이 fallback 은 거의 안 쓰임.
     */
    private fun firstTimeHint(job: String, schedule: String): Pair<String, String> {
        val s = "$job $schedule"
        return when {
            s.contains("학생") || s.contains("수험") ->
                "공부 끝나고 22시쯤 마음 풀러 숏폼을 켜기 쉬워." to
                        "오늘은 자기 직전 30분만이라도 폰을 눈에서 치워보자."
            s.contains("직장") || s.contains("회사") || s.contains("출근") ->
                "퇴근 직후 19~21시가 함정이야." to
                        "오늘은 집에 도착하자마자 5분만 산책해보자."
            s.contains("주부") || s.contains("육아") ->
                "아이가 잠든 22시 이후가 위험해." to
                        "오늘은 그 시간에 따뜻한 차 한 잔으로 시작해보자."
            else ->
                "퇴근/하교 직후 시간대에 숏폼이 늘기 쉬워." to
                        "오늘은 그 시간에 폰 대신 5분 스트레칭으로 시작해보자."
        }
    }

    companion object {
        private const val MAX_LEN = 300
    }
}
