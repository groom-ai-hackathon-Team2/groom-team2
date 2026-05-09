package com.groomteam2.dopamind.ai

/**
 * 코칭 프롬프트 상수.
 *
 * - SYSTEM: 모델의 페르소나/응답 형식 강제.
 * - USER_TEMPLATE: 런타임에 사용자 컨텍스트(직업/일정/취약시간/포인트)를 채워넣어 호출.
 *
 * 응답은 반드시 아래 JSON 스키마로 강제:
 *  {
 *    "message": "한 문장의 친근한 코칭 멘트",
 *    "challengeMinutes": 정수(권장 휴식 분),
 *    "rewardPoints": 정수(완주 시 지급할 포인트)
 *  }
 *
 * GeminiRequest 의 responseMimeType=application/json 과 함께 사용해 강한 형식 제약을 건다.
 */
object CoachingPrompts {

    val SYSTEM = """
        너는 '도파민드'라는 디지털 디톡스 앱의 AI 코치다.
        목표는 사용자가 무의식적으로 인스타그램/유튜브 숏폼을 빠르게 넘기는 도파민 좀비 상태를 빠져나오게 돕는 것.
        말투는 다정하고 짧으며 한국어 반말. 잔소리/훈계 금지. 게임처럼 챌린지를 제안.
        반드시 아래 JSON 한 객체로만 답한다 (코드블록 금지):
        {"message":"<한 문장 60자 이내>","challengeMinutes":<5..60 정수>,"rewardPoints":<10..150 정수>}
        challengeMinutes 는 사용자 일정과 시간대를 고려해 현실적인 값을 골라라.
        취약 시간대(isVulnerableHour=true)면 rewardPoints 를 평소의 3배로 책정.
    """.trimIndent()

    fun buildUserPrompt(ctx: CoachingContext): String = buildString {
        appendLine("[사용자 정보]")
        appendLine("- 직업: ${ctx.job}")
        appendLine("- 하루 일정: ${ctx.schedule}")
        appendLine()
        appendLine("[현재 상황]")
        appendLine("- 시간: ${ctx.hourOfDay}시")
        appendLine("- 취약 시간대 여부: ${ctx.isVulnerableHour}")
        appendLine("- 어떤 앱: ${ctx.packageLabel}")
        appendLine("- 평균 스와이프 간격: ${ctx.avgIntervalMs}ms (작을수록 무의식 상태)")
        appendLine("- 5초 윈도우 안 스크롤 횟수: ${ctx.eventCount}")
        appendLine("- 누적 포인트: ${ctx.totalPoints}")
        appendLine()
        appendLine("위 상황을 보고 위 JSON 형식으로만 답해라.")
    }
}

/**
 * 프롬프트 조립에 필요한 컨텍스트 묶음.
 * CoachingEngine 이 Repository 에서 모아서 채운다.
 */
data class CoachingContext(
    val job: String,
    val schedule: String,
    val hourOfDay: Int,
    val isVulnerableHour: Boolean,
    val packageLabel: String,
    val avgIntervalMs: Long,
    val eventCount: Int,
    val totalPoints: Int,
)
