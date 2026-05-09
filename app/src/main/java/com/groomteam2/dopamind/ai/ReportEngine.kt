package com.groomteam2.dopamind.ai

import com.groomteam2.dopamind.data.prefs.PlanTier
import com.groomteam2.dopamind.data.repo.ShortsStatsRepository
import com.groomteam2.dopamind.data.repo.UserRepository
import com.groomteam2.dopamind.data.repo.VulnerableHourStat
import kotlinx.coroutines.flow.first

/**
 * 숏폼 사용 패턴 주간 리포트 생성기.
 *
 * 흐름:
 *  1) ShortsStatsRepository / UserRepository / PointRepository 의 Flow 들을 일회성 first() 로 수집
 *  2) ReportContext 로 묶어 ReportPrompts 로 프롬프트 구성
 *  3) Gemini 호출 → 마크다운 텍스트 반환
 *  4) 호출 실패/키 미설정 시 fallback 텍스트 반환 (시연 흐름이 끊기지 않도록)
 *
 * 결과 텍스트 캐싱은 ViewModel/UserPrefs 레이어에서 처리.
 */
class ReportEngine(
    private val gemini: GeminiClient,
    private val userRepo: UserRepository,
    private val statsRepo: ShortsStatsRepository,
    private val pointBalance: suspend () -> Int,
) {

    suspend fun generate(plan: PlanTier): String {
        val ctx = collectContext()

        return if (gemini.isConfigured) {
            runCatching {
                gemini.generateJson(
                    systemPrompt = ReportPrompts.SYSTEM,
                    userPrompt = ReportPrompts.buildUserPrompt(ctx, plan),
                ).trim()
            }.getOrElse { fallback(ctx, plan) }
        } else {
            fallback(ctx, plan)
        }
    }

    private suspend fun collectContext(): ReportContext {
        val profile = userRepo.currentProfile()
        val total = runCatching { statsRepo.getTotalViewCount().first() }.getOrDefault(0)
        val todayCount = runCatching { statsRepo.getTodayViewCount().first() }.getOrDefault(0)
        val todaySec = runCatching { statsRepo.getTodayWatchTimeSec().first() }.getOrDefault(0L)
        val weekSec = runCatching { statsRepo.getWeekWatchTimeSec().first() }.getOrDefault(0L)
        val ydaySec = runCatching { statsRepo.getYesterdayWatchTimeSec().first() }.getOrDefault(0L)
        val avgMs = runCatching { statsRepo.getTodayAvgScrollIntervalMs().first() }.getOrDefault(0L)
        val byPkg = runCatching { statsRepo.getTodayCountByPackage().first() }.getOrDefault(emptyMap())
        val topHours = runCatching { statsRepo.getTopVulnerableHours(limit = 5).first() }
            .getOrDefault(emptyList())
        val balance = runCatching { pointBalance() }.getOrDefault(0)

        return ReportContext(
            job = profile?.job ?: "직장인",
            schedule = profile?.schedule ?: "일정 미입력",
            totalPoints = balance,
            todayViewCount = todayCount,
            totalViewCount = total,
            todayWatchMinutes = todaySec / 60L,
            weekWatchMinutes = weekSec / 60L,
            yesterdayWatchMinutes = ydaySec / 60L,
            todayAvgScrollMs = avgMs,
            todayInstagram = byPkg[PKG_INSTAGRAM] ?: 0,
            todayYoutube = byPkg[PKG_YOUTUBE] ?: 0,
            todayTiktok = (byPkg[PKG_TIKTOK_MUSICAL] ?: 0) + (byPkg[PKG_TIKTOK_TRILL] ?: 0),
            topVulnerableHours = topHours.map { it.toPoint() },
        )
    }

    /** Gemini 키가 없거나 호출이 실패할 때 — 데이터 기반 사전 정의 멘트로 채운다. */
    private fun fallback(ctx: ReportContext, plan: PlanTier): String {
        val sb = StringBuilder()
        sb.appendLine("## 이번 주 요약")
        sb.appendLine("이번 주 ${ctx.weekWatchMinutes}분, 오늘은 ${ctx.todayWatchMinutes}분 동안 숏폼을 봤어. 평균 스크롤 간격 ${ctx.todayAvgScrollMs}ms — ${if (ctx.todayAvgScrollMs in 1L..799L) "좀비 모드 의심" else "비교적 의식적인 시청"}이야.")
        sb.appendLine()
        sb.appendLine("## 핵심 통계")
        sb.appendLine("- 누적 시청: ${ctx.totalViewCount}개 / 오늘: ${ctx.todayViewCount}개")
        sb.appendLine("- 오늘 분포: 인스타 ${ctx.todayInstagram} · 유튜브 ${ctx.todayYoutube} · 틱톡 ${ctx.todayTiktok}")
        sb.appendLine("- 어제 대비: ${deltaText(ctx.todayWatchMinutes, ctx.yesterdayWatchMinutes)}")

        if (plan == PlanTier.PRO) {
            sb.appendLine()
            sb.appendLine("## 시간대별 심층 분석")
            if (ctx.topVulnerableHours.isEmpty()) {
                sb.appendLine("아직 시간대별 패턴이 학습되지 않았어. 며칠 더 사용하면 의미 있는 분석이 가능해.")
            } else {
                ctx.topVulnerableHours.take(3).forEach {
                    sb.appendLine("- ${it.hour}시 구간: ${it.count}회 시청, 평균 ${it.avgWatchMin}분 머묾")
                }
            }
            sb.appendLine()
            sb.appendLine("## 맞춤 코칭 플랜")
            sb.appendLine("- 가장 취약한 시간대 30분 전 알림을 받아보자")
            sb.appendLine("- 잠들기 1시간 전엔 폰을 거실에 두기")
            sb.appendLine("- ${ctx.job} 일정에 맞춰 점심 직후 5분 산책 챌린지 추가")
        }
        return sb.toString().trim()
    }

    private fun deltaText(today: Long, yesterday: Long): String = when {
        yesterday <= 0L && today <= 0L -> "비교 데이터 없음"
        yesterday <= 0L -> "어제 데이터 없음 (오늘 ${today}분)"
        else -> {
            val diff = today - yesterday
            when {
                diff > 0 -> "+${diff}분 늘어남"
                diff < 0 -> "${diff}분 줄어듦 👍"
                else -> "어제와 동일"
            }
        }
    }

    private fun VulnerableHourStat.toPoint() = VulnerableHourPoint(
        hour = hour, count = count, avgWatchMin = avgWatchMin,
    )

    companion object {
        private const val PKG_INSTAGRAM = "com.instagram.android"
        private const val PKG_YOUTUBE = "com.google.android.youtube"
        private const val PKG_TIKTOK_MUSICAL = "com.zhiliaoapp.musically"
        private const val PKG_TIKTOK_TRILL = "com.ss.android.ugc.trill"
    }
}
