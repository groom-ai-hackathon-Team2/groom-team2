package com.groomteam2.dopamind.ui.home

import androidx.lifecycle.ViewModel
import com.groomteam2.dopamind.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * 홈 화면용 데이터 결합.
 *  - 총 포인트
 *  - 오늘 적립 포인트(=코칭 받고 챌린지 완수 횟수와 비례)
 *  - 오늘 챌린지 성공 횟수
 *  - 현재 시간이 취약시간인지 — 화면에 빨간 배너로 띄움
 */
class HomeViewModel : ViewModel() {

    private val pointRepo = ServiceLocator.pointRepository
    private val challengeRepo = ServiceLocator.challengeRepository
    private val learner = ServiceLocator.vulnerableTimeLearner

    val state: Flow<HomeState> = combine(
        pointRepo.balance,
        pointRepo.earnedToday(),
        challengeRepo.successCountToday(),
        learner.scoresFlow,
    ) { balance, earnedToday, successToday, scores ->
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        HomeState(
            totalPoints = balance,
            earnedToday = earnedToday,
            successToday = successToday,
            isVulnerableNow = learner.isVulnerableNow(now),
            currentHour = now,
            vulnerableScores = scores.toList(),
        )
    }
}

data class HomeState(
    val totalPoints: Int = 0,
    val earnedToday: Int = 0,
    val successToday: Int = 0,
    val isVulnerableNow: Boolean = false,
    val currentHour: Int = 0,
    val vulnerableScores: List<Float> = List(24) { 0f },
)
