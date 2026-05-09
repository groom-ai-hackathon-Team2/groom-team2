package com.groomteam2.dopamind.data.repo

import com.groomteam2.dopamind.analyzer.ActiveShortsSessionRegistry
import com.groomteam2.dopamind.analyzer.VulnerableTimeLearner
import com.groomteam2.dopamind.data.db.HourlySessionDuration
import com.groomteam2.dopamind.data.db.HourlyViewCount
import com.groomteam2.dopamind.data.db.PackageViewCount
import com.groomteam2.dopamind.data.db.ShortsSessionLog
import com.groomteam2.dopamind.data.db.ShortsSessionLogDao
import com.groomteam2.dopamind.data.db.ShortsViewLog
import com.groomteam2.dopamind.data.db.ShortsViewLogDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 숏폼 사용 통계 조회 Repository.
 *
 * - View 로그(영상 1개 시청 = 1행)와 Session 로그(연속 시청 구간) 두 테이블을 결합.
 * - 시간 경계는 모두 디바이스 로컬 타임존 기준 (자정 / 월요일 00:00 / 어제 등).
 * - 모든 메서드는 Flow 를 반환 — UI 가 자동 갱신.
 */
class ShortsStatsRepository(
    private val viewDao: ShortsViewLogDao,
    private val sessionDao: ShortsSessionLogDao,
    private val vulnerableTimeLearner: VulnerableTimeLearner,
) {

    // ── 수집 진입점 ────────────────────────────────────────────────
    /**
     * 스크롤 이벤트 1건 = 영상 1개 시청 완료로 간주, View 로그 1행 적재.
     * 호출자(ShortsStatsCollector)가 백그라운드 코루틴에서 호출.
     */
    suspend fun recordView(packageName: String, timestampMs: Long) {
        viewDao.insert(
            ShortsViewLog(
                packageName = packageName,
                timestamp = timestampMs,
                dayKey = dayKeyOf(timestampMs),
            )
        )
    }

    /**
     * 한 세션이 종료되었을 때(마지막 이벤트 후 30초 침묵) 1행 적재.
     */
    suspend fun recordSession(packageName: String, startMs: Long, endMs: Long) {
        val durationSec = ((endMs - startMs) / 1000L).coerceAtLeast(0L)
        sessionDao.insert(
            ShortsSessionLog(
                packageName = packageName,
                startTime = startMs,
                endTime = endMs,
                durationSec = durationSec,
                dayKey = dayKeyOf(startMs),
            )
        )
    }

    // ── 카드 1: 시청한 숏츠 개수 ──────────────────────────────────
    fun getTodayViewCount(): Flow<Int> = viewDao.countSince(startOfTodayMs())
    fun getTotalViewCount(): Flow<Int> = viewDao.totalCount()

    /** 패키지별(인스타/유튜브/틱톡) 오늘 시청 개수 분리 표시용. */
    fun getTodayCountByPackage(): Flow<Map<String, Int>> =
        viewDao.countByPackageSince(startOfTodayMs())
            .map { list -> list.associate { it.packageName to it.count } }

    // ── 카드 2: 시청 시간 ─────────────────────────────────────────
    /**
     * 오늘 시청 시간 = (DB 의 완료 세션 합계) + (현재 진행 중 세션의 실시간 경과).
     * 진행 중 세션이 있으면 1초 ticker 로 흐르는 시간을 그대로 반영해 UI 가 매초 갱신된다.
     */
    fun getTodayWatchTimeSec(): Flow<Long> = liveWatchTimeSec(startOfTodayMs())
    fun getWeekWatchTimeSec(): Flow<Long> = liveWatchTimeSec(startOfWeekMs())

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun liveWatchTimeSec(sinceMs: Long): Flow<Long> =
        ActiveShortsSessionRegistry.flow.flatMapLatest { active ->
            if (active.isEmpty()) {
                // 진행 중 세션 없음 — DB 값만 그대로 흘려보냄.
                sessionDao.sumDurationSecSince(sinceMs)
            } else {
                // 진행 중 세션 있음 — DB 값 + 현재 시각 기반 실시간 경과를 1초마다 합산.
                combine(sessionDao.sumDurationSecSince(sinceMs), tickerFlow(1_000L)) { stored, _ ->
                    val now = System.currentTimeMillis()
                    val ongoing = active.values.sumOf { s ->
                        val from = maxOf(s.startMs, sinceMs)
                        ((now - from) / 1000L).coerceAtLeast(0L)
                    }
                    stored + ongoing
                }
            }
        }

    private fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }

    /** 어제 대비 증감(+/-%) 표기를 위해 어제 합계도 노출. */
    fun getYesterdayWatchTimeSec(): Flow<Long> {
        val (start, end) = yesterdayRangeMs()
        return sessionDao.sumDurationSecBetween(start, end)
    }

    // ── 카드 3: 평균 스크롤 간격 ─────────────────────────────────
    /**
     * 오늘 발생한 스크롤 이벤트들의 인접 간격 평균.
     * - View 로그가 2건 미만이면 0L 반환(=데이터 부족).
     * - 한 세션 안의 인접 간격만이 의미 있으므로 30초(=세션 컷오프) 이상 갭은 평균에서 제외.
     */
    fun getTodayAvgScrollIntervalMs(): Flow<Long> =
        viewDao.timestampsSince(startOfTodayMs()).map { times ->
            if (times.size < 2) return@map 0L
            var sum = 0L
            var count = 0
            for (i in 1 until times.size) {
                val gap = times[i] - times[i - 1]
                if (gap in 1L..SESSION_GAP_MS) {
                    sum += gap
                    count++
                }
            }
            if (count == 0) 0L else sum / count
        }

    // ── 카드 4: 주요 취약 시간 Top N ─────────────────────────────
    /**
     * VulnerableTimeLearner 의 EMA 점수 기준으로 상위 N개 시간대를 뽑고,
     * 같은 시간대의 ShortsViewLog 누적 시청 횟수 + 평균 시청 분 수를 부가 정보로 결합.
     */
    fun getTopVulnerableHours(limit: Int = 3): Flow<List<VulnerableHourStat>> =
        combine(
            vulnerableTimeLearner.scoresFlow,
            viewDao.countByHourSince(0L),
            sessionDao.durationByHourSince(0L),
        ) { scores, viewCounts, durations ->
            val viewByHour = viewCounts.associate { it.hourOfDay to it.count }
            val durByHour = durations.associate { it.hour to it.totalSec }

            // 점수 0 인 시간대는 의미 없으므로 제외.
            val ranked = scores.toList()
                .mapIndexed { hour, score -> Triple(hour, score, viewByHour[hour] ?: 0) }
                .filter { it.second > 0f || it.third > 0 }
                .sortedWith(
                    compareByDescending<Triple<Int, Float, Int>> { it.second }
                        .thenByDescending { it.third }
                )
                .take(limit)

            ranked.map { (hour, score, count) ->
                val secs = durByHour[hour] ?: 0L
                VulnerableHourStat(
                    hour = hour,
                    count = count,
                    score = score,
                    avgWatchMin = (secs / 60L),
                )
            }
        }

    // ── 시간 경계 헬퍼 ───────────────────────────────────────────
    private fun startOfTodayMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeekMs(): Long = Calendar.getInstance().apply {
        // 한국 사용자 기준 — 월요일을 한 주의 시작으로.
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun yesterdayRangeMs(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 24L * 60L * 60L * 1000L
        return start to end
    }

    companion object {
        /** 세션 컷오프(=30초)와 동일 — 평균 스크롤 간격 계산 시 갭 필터에 사용. */
        const val SESSION_GAP_MS = 30_000L

        private val DAY_KEY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        fun dayKeyOf(timestampMs: Long): String =
            synchronized(DAY_KEY_FMT) { DAY_KEY_FMT.format(timestampMs) }
    }
}

/**
 * 취약 시간대 1개의 표시용 데이터.
 * - hour: 0..23
 * - count: 해당 시간대 누적 시청 영상 수
 * - score: VulnerableTimeLearner EMA 점수(정렬 기준)
 * - avgWatchMin: 해당 시간대 누적 시청 시간(분)
 */
data class VulnerableHourStat(
    val hour: Int,
    val count: Int,
    val score: Float,
    val avgWatchMin: Long,
)