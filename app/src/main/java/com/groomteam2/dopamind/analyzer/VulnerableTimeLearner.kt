package com.groomteam2.dopamind.analyzer

import com.groomteam2.dopamind.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 시간대별 '도파민 좀비' 빈도 학습기 (Dynamic Friction 의 핵심).
 *
 * 동작:
 *  - PatternAnalyzer 가 좀비를 판정할 때마다 recordZombie(hour) 호출.
 *  - 각 시간대(0..23)별로 EMA(지수이동평균)를 갱신:
 *      score_new = score_old * (1 - α) + 1.0 * α
 *    α = 0.3 → 최근 가중치를 적당히 두면서 과거 패턴도 반영.
 *  - 점수가 0 이 아닌 시간대들 중 상위 25% 안에 들어가면 '취약 시간대'로 판정.
 *
 * 이 정보는:
 *  - PatternAnalyzer 가 좀비 판정 임계치를 완화할 때 사용
 *  - CoachingEngine 이 보상 포인트 3배 적용 여부를 결정
 *  - HomeScreen 이 "지금은 너의 취약 시간!" 배지를 띄울지 결정
 */
class VulnerableTimeLearner(
    private val prefs: UserPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = FloatArray(24)
    private val _scoresFlow = MutableStateFlow(FloatArray(24))
    val scoresFlow: StateFlow<FloatArray> = _scoresFlow

    init {
        // 디스크에서 캐시로 1회 로드. 이후 갱신은 메모리 + 디스크 동시.
        scope.launch {
            prefs.allVulnerableScores().collect { arr ->
                arr.copyInto(cache)
                _scoresFlow.value = arr.copyOf()
            }
        }
    }

    /**
     * 좀비 판정이 발생한 시간대의 점수를 EMA 로 끌어올림.
     * 메모리 캐시는 즉시 갱신, 디스크 반영은 IO 코루틴에 던져둠(블로킹 회피).
     * 호출 빈도는 10분 쿨다운으로 매우 낮아 race 우려 없음.
     */
    fun recordZombie(hour: Int) {
        val safeHour = hour.coerceIn(0, 23)
        val updated = cache[safeHour] * (1f - ALPHA) + 1f * ALPHA
        cache[safeHour] = updated
        _scoresFlow.value = cache.copyOf()
        scope.launch { prefs.updateVulnerableScore(safeHour, updated) }
    }

    /**
     * 사용자가 숏폼 앱에서 시간을 보냈음을 기록 (분 단위).
     * 좀비 판정이 안 떴어도 '오래 머문 시간대'를 학습할 수 있게 추가 신호.
     * 분 수만큼 점수에 가중치를 더해 EMA 갱신 — 길게 본 시간대일수록 빠르게 위험으로 인식.
     */
    fun recordUsage(hour: Int, minutes: Int) {
        if (minutes <= 0) return
        val safeHour = hour.coerceIn(0, 23)
        // 분 수를 0..1 로 정규화 (60분 이상 → 1.0). 30분이면 0.5 신호.
        val signal = (minutes / 60f).coerceAtMost(1f)
        val updated = cache[safeHour] * (1f - ALPHA) + signal * ALPHA
        cache[safeHour] = updated
        _scoresFlow.value = cache.copyOf()
        scope.launch { prefs.updateVulnerableScore(safeHour, updated) }
    }

    /**
     * 현재 시간대가 취약 시간대인지.
     * 점수가 0 보다 큰 시간대들을 정렬해서 상위 25% 컷오프 이상이면 true.
     * (충분한 데이터가 쌓이기 전에는 false 반환 — 보수적 판단)
     */
    fun isVulnerableNow(hour: Int): Boolean {
        val sorted = cache.filter { it > 0f }.sortedDescending()
        if (sorted.size < MIN_DATA_POINTS) return false
        val cutoffIndex = (sorted.size * 0.25f).toInt().coerceAtLeast(0)
        val cutoff = sorted[cutoffIndex]
        return cache[hour.coerceIn(0, 23)] >= cutoff
    }

    /** 코칭 프롬프트에 넣을 수 있도록 점수 배열 그대로 반환. */
    fun snapshot(): FloatArray = cache.copyOf()

    companion object {
        private const val ALPHA = 0.3f
        // 조기 적응을 위해 2개 시간대만 데이터 있어도 취약시간 판정 시작.
        private const val MIN_DATA_POINTS = 2
    }
}
