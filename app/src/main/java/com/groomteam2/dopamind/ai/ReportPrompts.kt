package com.groomteam2.dopamind.ai

import com.groomteam2.dopamind.data.prefs.PlanTier

/**
 * 숏폼 사용 패턴 주간 리포트용 프롬프트.
 *
 * - SYSTEM 은 두 플랜 공통(섹션 구성만 다름).
 * - 플랜에 따라 USER 프롬프트가 요구하는 섹션 수가 달라진다:
 *    STANDARD → 2개 섹션(요약 / 핵심 통계)
 *    PRO       → 4개 섹션(요약 / 핵심 통계 / 시간대별 심층 분석 / 맞춤 코칭 플랜)
 *
 * 응답은 사람이 읽는 자연어. JSON 구조화는 하지 않는다 — 화면이 마크다운 헤더로
 * 섹션을 나누어 카드로 렌더링하므로 헤더 형식만 강제한다.
 */
object ReportPrompts {

    val SYSTEM = """
        너는 '도파민드' 앱의 데이터 분석가 겸 디지털 디톡스 코치다.
        사용자의 지난 7일치 숏폼(인스타 릴스/유튜브 숏츠/틱톡) 시청 패턴 데이터를 받아
        한국어로 친절하지만 직설적인 주간 리포트를 작성한다.

        규칙:
        - 톤: 한국어 반말, 친한 친구가 분석해주듯. 잔소리/훈계 금지.
        - 숫자는 데이터에 있는 그대로 인용한다. 절대 지어내지 않는다.
        - 각 섹션은 반드시 '## 섹션이름' 마크다운 헤더로 시작한다 (정확한 헤더는 사용자 프롬프트가 지정).
        - 섹션 본문은 2~4문장 또는 짧은 불릿(- 로 시작) 3개 이내.
        - 코드블록(```) 사용 금지.
    """.trimIndent()

    fun buildUserPrompt(ctx: ReportContext, plan: PlanTier): String = buildString {
        appendLine("[사용자 정보]")
        appendLine("- 직업: ${ctx.job}")
        appendLine("- 하루 일정: ${ctx.schedule}")
        appendLine("- 누적 절제 포인트: ${ctx.totalPoints}p")
        appendLine()
        appendLine("[지난 7일 숏폼 사용 데이터]")
        appendLine("- 누적 시청 영상 수: ${ctx.totalViewCount}개")
        appendLine("- 오늘 시청 영상 수: ${ctx.todayViewCount}개")
        appendLine("- 오늘 시청 시간: ${ctx.todayWatchMinutes}분")
        appendLine("- 이번 주 시청 시간: ${ctx.weekWatchMinutes}분")
        appendLine("- 어제 시청 시간: ${ctx.yesterdayWatchMinutes}분")
        appendLine("- 오늘 평균 스크롤 간격: ${ctx.todayAvgScrollMs}ms (800ms 미만 = 좀비 상태)")
        appendLine("- 패키지별 오늘 시청: 인스타 ${ctx.todayInstagram}, 유튜브 ${ctx.todayYoutube}, 틱톡 ${ctx.todayTiktok}")
        if (ctx.topVulnerableHours.isNotEmpty()) {
            appendLine("- 취약 시간대 Top: ${ctx.topVulnerableHours.joinToString { "${it.hour}시(${it.count}회/${it.avgWatchMin}분)" }}")
        } else {
            appendLine("- 취약 시간대: 아직 학습된 데이터 없음")
        }
        appendLine()
        appendLine("[작성할 섹션]")
        when (plan) {
            PlanTier.STANDARD -> {
                appendLine("아래 정확히 2개 섹션만 작성한다 (헤더 문구 그대로):")
                appendLine("## 이번 주 요약")
                appendLine("## 핵심 통계")
            }
            PlanTier.PRO -> {
                appendLine("아래 정확히 4개 섹션을 순서대로 작성한다 (헤더 문구 그대로):")
                appendLine("## 이번 주 요약")
                appendLine("## 핵심 통계")
                appendLine("## 시간대별 심층 분석")
                appendLine("## 맞춤 코칭 플랜")
                appendLine()
                appendLine("'시간대별 심층 분석'은 취약 시간대 Top 데이터를 근거로 ‘왜 그 시간대에 집중되는지’ 추정과 패턴을 짚어준다.")
                appendLine("'맞춤 코칭 플랜'은 사용자의 직업/일정에 맞춘 구체적 액션 3가지를 불릿으로 제시한다.")
            }
        }
    }
}

/** 리포트 프롬프트에 채워 넣을 7일치 통계 스냅샷. */
data class ReportContext(
    val job: String,
    val schedule: String,
    val totalPoints: Int,
    val todayViewCount: Int,
    val totalViewCount: Int,
    val todayWatchMinutes: Long,
    val weekWatchMinutes: Long,
    val yesterdayWatchMinutes: Long,
    val todayAvgScrollMs: Long,
    val todayInstagram: Int,
    val todayYoutube: Int,
    val todayTiktok: Int,
    val topVulnerableHours: List<VulnerableHourPoint>,
)

data class VulnerableHourPoint(
    val hour: Int,
    val count: Int,
    val avgWatchMin: Long,
)
