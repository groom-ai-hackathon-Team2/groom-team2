package com.groomteam2.dopamind.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groomteam2.dopamind.data.prefs.UserPrefs
import com.groomteam2.dopamind.data.repo.VulnerableHourStat
import com.groomteam2.dopamind.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 홈 화면용 데이터 결합.
 *  - 총 포인트
 *  - 오늘 적립 포인트(=코칭 받고 챌린지 완수 횟수와 비례)
 *  - 오늘 챌린지 성공 횟수
 *  - 현재 시간이 취약시간인지 — 화면에 빨간 배너로 띄움
 *  - 사용자가 설정한 앱 타이머 목표 시간(분)
 *  - 활성 타이머 종료 시각 — 홈 상단 재생 버튼이 동작 중인지 판단할 때 사용
 *  - 숏폼 사용 통계 — 별도 StateFlow 로 분리(combine 의존성을 단순하게 유지).
 */
class HomeViewModel : ViewModel() {

    private val pointRepo = ServiceLocator.pointRepository
    private val challengeRepo = ServiceLocator.challengeRepository
    private val learner = ServiceLocator.vulnerableTimeLearner
    private val userPrefs = ServiceLocator.userPrefs
    private val shortsRepo = ServiceLocator.shortsStatsRepository

    private val baseState: Flow<HomeState> = combine(
        pointRepo.balance,
        pointRepo.earnedToday(),
        challengeRepo.successCountToday(),
        learner.scoresFlow,
        userPrefs.goalTimerSeconds,
    ) { balance, earnedToday, successToday, scores, goalSeconds ->
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        HomeState(
            totalPoints = balance,
            earnedToday = earnedToday,
            successToday = successToday,
            isVulnerableNow = learner.isVulnerableNow(now),
            currentHour = now,
            vulnerableScores = scores.toList(),
            goalTimerSeconds = goalSeconds,
        )
    }

    /** 활성 타이머의 종료 시각을 추가로 결합. 홈의 재생 버튼이 동작 중인지 판단할 때 사용. */
    val state: Flow<HomeState> = combine(baseState, userPrefs.activeTimerEndAt) { base, endAt ->
        base.copy(activeTimerEndAt = endAt)
    }

    /**
     * 숏폼 사용 통계.
     * 5-인자 combine 으로 핵심 수치를 만든 뒤 추가 3개 Flow 를 더 결합.
     * stateIn 으로 화면이 잠깐 사라져도 5초 동안 구독 유지 — 재진입 시 재계산 비용 절감.
     */
    private val partialShorts: Flow<ShortsStatsUiState> = combine(
        shortsRepo.getTodayViewCount(),
        shortsRepo.getTotalViewCount(),
        shortsRepo.getTodayWatchTimeSec(),
        shortsRepo.getWeekWatchTimeSec(),
        shortsRepo.getYesterdayWatchTimeSec(),
    ) { todayCount, totalCount, todaySec, weekSec, ydaySec ->
        ShortsStatsUiState(
            todayViewCount = todayCount,
            totalViewCount = totalCount,
            todayWatchSec = todaySec,
            weekWatchSec = weekSec,
            yesterdayWatchSec = ydaySec,
        )
    }

    val shortsStatsFlow: StateFlow<ShortsStatsUiState> = combine(
        partialShorts,
        shortsRepo.getTodayAvgScrollIntervalMs(),
        shortsRepo.getTodayCountByPackage(),
        shortsRepo.getTopVulnerableHours(),
    ) { partial, avgMs, byPkg, topHours ->
        partial.copy(
            todayAvgScrollMs = avgMs,
            todayCountByPackage = byPkg,
            topVulnerableHours = topHours,
            isEmpty = partial.totalViewCount == 0 && topHours.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShortsStatsUiState(),
    )

    fun setGoalTimerSeconds(seconds: Int) {
        viewModelScope.launch { userPrefs.setGoalTimerSeconds(seconds) }
    }
}

data class HomeState(
    val totalPoints: Int = 0,
    val earnedToday: Int = 0,
    val successToday: Int = 0,
    val isVulnerableNow: Boolean = false,
    val currentHour: Int = 0,
    val vulnerableScores: List<Float> = List(24) { 0f },
    val goalTimerSeconds: Int = UserPrefs.DEFAULT_GOAL_SEC,
    val activeTimerEndAt: Long = 0L,
)

/**
 * 숏폼 사용 통계 섹션 UI 상태.
 * - isEmpty: 첫 사용자(완전히 0건) → placeholder 표시 트리거.
 */
data class ShortsStatsUiState(
    val todayViewCount: Int = 0,
    val totalViewCount: Int = 0,
    val todayCountByPackage: Map<String, Int> = emptyMap(),
    val todayWatchSec: Long = 0L,
    val weekWatchSec: Long = 0L,
    val yesterdayWatchSec: Long = 0L,
    val todayAvgScrollMs: Long = 0L,
    val topVulnerableHours: List<VulnerableHourStat> = emptyList(),
    val isEmpty: Boolean = true,
)