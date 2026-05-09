package com.groomteam2.dopamind.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groomteam2.dopamind.data.prefs.UserPrefs
import com.groomteam2.dopamind.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 *  - AI 피드백 상태 (사용자 명시적 요청 시 Gemini 호출)
 */
class HomeViewModel : ViewModel() {

    private val pointRepo = ServiceLocator.pointRepository
    private val challengeRepo = ServiceLocator.challengeRepository
    private val learner = ServiceLocator.vulnerableTimeLearner
    private val userPrefs = ServiceLocator.userPrefs
    private val feedbackEngine = ServiceLocator.patternFeedbackEngine

    private val baseState: Flow<HomeState> = combine(
        pointRepo.balance,
        pointRepo.earnedToday(),
        challengeRepo.successCountToday(),
        learner.scoresFlow,
        userPrefs.goalTimerMinutes,
    ) { balance, earnedToday, successToday, scores, goalMinutes ->
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        HomeState(
            totalPoints = balance,
            earnedToday = earnedToday,
            successToday = successToday,
            isVulnerableNow = learner.isVulnerableNow(now),
            currentHour = now,
            vulnerableScores = scores.toList(),
            goalTimerMinutes = goalMinutes,
        )
    }

    /** 활성 타이머의 종료 시각을 추가로 결합. 홈의 재생 버튼이 동작 중인지 판단할 때 사용. */
    val state: Flow<HomeState> = combine(baseState, userPrefs.activeTimerEndAt) { base, endAt ->
        base.copy(activeTimerEndAt = endAt)
    }

    private val _feedback = MutableStateFlow<AIFeedbackState>(AIFeedbackState.Empty)
    val feedback: StateFlow<AIFeedbackState> = _feedback

    fun setGoalTimerMinutes(minutes: Int) {
        viewModelScope.launch { userPrefs.setGoalTimerMinutes(minutes) }
    }

    fun requestFeedback() {
        if (_feedback.value is AIFeedbackState.Loading) return
        _feedback.value = AIFeedbackState.Loading
        viewModelScope.launch {
            _feedback.value = runCatching { feedbackEngine.generate() }
                .fold(
                    onSuccess = { AIFeedbackState.Ready(it) },
                    onFailure = { AIFeedbackState.Error(it.message ?: "알 수 없는 오류") },
                )
        }
    }
}

data class HomeState(
    val totalPoints: Int = 0,
    val earnedToday: Int = 0,
    val successToday: Int = 0,
    val isVulnerableNow: Boolean = false,
    val currentHour: Int = 0,
    val vulnerableScores: List<Float> = List(24) { 0f },
    val goalTimerMinutes: Int = UserPrefs.DEFAULT_GOAL_MIN,
    val activeTimerEndAt: Long = 0L,
) {
    /** 학습된 시간대 수 (점수 > 0). 0..24. */
    val learnedHours: Int get() = vulnerableScores.count { it > 0f }

    /** 가장 위험한 시간대 (점수 최대). 데이터 없으면 null. */
    val peakHour: Int? get() = vulnerableScores
        .withIndex()
        .filter { it.value > 0f }
        .maxByOrNull { it.value }
        ?.index
}

/** AI 피드백 카드의 4가지 상태. */
sealed interface AIFeedbackState {
    data object Empty : AIFeedbackState
    data object Loading : AIFeedbackState
    data class Ready(val text: String) : AIFeedbackState
    data class Error(val message: String) : AIFeedbackState
}
