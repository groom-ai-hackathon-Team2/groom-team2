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

/**
 * 홈 화면 'AI 피드백' 카드용 프롬프트.
 *
 * 자유 텍스트 응답 (JSON 강제 X). 두 모드 지원:
 *   - 누적 데이터 있음: 패턴 회고 + 다음 행동 제안
 *   - 누적 데이터 없음(첫 사용): 직업/일정 기반 환영 + 가장 흔한 함정 안내
 */
object FeedbackPrompts {

    val SYSTEM = """
        너는 '도파민드'라는 디지털 디톡스 앱의 AI 코치다.
        사용자의 인스타그램·유튜브 숏폼 사용 데이터를 보고 한국어 반말로 다정하게 한 마디 해준다.
        말투 규칙: 짧고 부드럽게. 잔소리 금지. 칭찬할 부분이 있으면 먼저 칭찬.
        분량: 2~3 문장, 총 200자 이내. 마크다운/JSON 금지, 일반 텍스트로만 답한다.
        마지막 문장은 사용자가 오늘/내일 시도할 작은 행동 제안 한 줄로 마무리.

        [모드 분기 — userPrompt 의 isFirstTime 값으로 판단]
        - isFirstTime=true 면 누적 데이터가 거의 없는 첫 사용자. 일반론 금지. 직업/일정에서
          위험할 만한 시간대(예: 점심 직후, 야근 후 22시 등)를 1개만 짚어 환영 메시지를 준다.
        - isFirstTime=false 면 제공된 시간대별 좀비 빈도 / 오늘 성과 / 누적 포인트 중 가장 두드러진
          1개 수치를 인용해 회고한다.
    """.trimIndent()

    fun buildUserPrompt(ctx: FeedbackContext): String = buildString {
        appendLine("[사용자 정보]")
        appendLine("- 직업: ${ctx.job}")
        appendLine("- 하루 일정: ${ctx.schedule}")
        appendLine()
        appendLine("[모드]")
        appendLine("- isFirstTime: ${ctx.isFirstTime}")
        appendLine()
        appendLine("[누적 통계]")
        appendLine("- 총 포인트: ${ctx.totalPoints}")
        appendLine("- 오늘 적립 포인트: ${ctx.earnedToday}")
        appendLine("- 오늘 챌린지 성공 횟수: ${ctx.successToday}")
        appendLine()
        appendLine("[현재 시간대]")
        appendLine("- 현재 시: ${ctx.currentHour}시 (취약시간 = ${ctx.isVulnerableHourNow})")
        appendLine()
        if (ctx.vulnerableScores.any { it > 0f }) {
            appendLine("[24시간 위험도 점수 — 큰 값일수록 좀비 자주 발생]")
            ctx.vulnerableScores.forEachIndexed { hour, score ->
                if (score > 0f) appendLine("  ${hour}시: ${"%.2f".format(score)}")
            }
        } else {
            appendLine("[24시간 위험도] 아직 학습 데이터 없음.")
        }
        appendLine()
        appendLine("위 정보를 보고 시스템 프롬프트의 모드 분기 규칙대로 피드백을 작성해라.")
    }
}

/** AI 피드백 카드용 컨텍스트. */
data class FeedbackContext(
    val job: String,
    val schedule: String,
    val totalPoints: Int,
    val earnedToday: Int,
    val successToday: Int,
    val currentHour: Int,
    val isVulnerableHourNow: Boolean,
    val vulnerableScores: List<Float>,
    /** 누적 데이터가 거의 없는지 여부 — true 면 환영 톤으로 응답. */
    val isFirstTime: Boolean,
)
