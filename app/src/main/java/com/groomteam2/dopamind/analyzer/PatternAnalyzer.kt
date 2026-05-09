package com.groomteam2.dopamind.analyzer

import com.groomteam2.dopamind.analyzer.ScrollEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.math.sqrt

/**
 * 도파민 좀비 상태 감지기.
 *
 * 알고리즘:
 *  1) 슬라이딩 윈도우(WINDOW_MS=5초) 안의 스크롤 타임스탬프를 큐에 보관.
 *  2) 윈도우 안 이벤트가 MIN_EVENTS 이상이면 평균 간격 / 표준편차 계산.
 *  3) 평균 간격이 임계치(기본 800ms) 미만 + 표준편차도 작으면(기계적 스와이프) 좀비 판정.
 *  4) 한 번 판정 후 COOLDOWN_MS(10분) 동안 재판정 안 함 — 사용자를 너무 자주 방해하지 않기 위함.
 *
 * 적응형 부분:
 *  - 현재 시간대가 '취약 시간대'면 임계치를 1000ms 로 완화 (= 더 쉽게 트리거).
 *    VulnerableTimeLearner 가 시간대별 점수를 관리.
 */
class PatternAnalyzer(
    private val vulnerableTimeLearner: VulnerableTimeLearner,
) {
    private val _zombieStateFlow = MutableStateFlow<ZombieDetection?>(null)
    val zombieStateFlow: StateFlow<ZombieDetection?> = _zombieStateFlow

    private val timestamps = ArrayDeque<Long>()
    private var lastZombieAt = 0L
    private var currentPackage: String? = null

    private var collectorJob: Job? = null

    /**
     * 분석 코루틴 시작. 이미 돌고 있으면 무시.
     * AccessibilityService.onServiceConnected 에서 호출.
     */
    fun start(scope: CoroutineScope) {
        if (collectorJob?.isActive == true) return
        collectorJob = scope.launch {
            ScrollEventBus.events.collect { ev -> handle(ev) }
        }
    }

    private fun handle(ev: ScrollEvent) {
        // 패키지가 바뀌면 윈도우 리셋 — 인스타→유튜브 갈아탄 즉시 새로 측정.
        if (currentPackage != ev.packageName) {
            timestamps.clear()
            currentPackage = ev.packageName
        }

        timestamps.addLast(ev.timestampMs)
        // 윈도우 밖 이벤트 제거.
        while (timestamps.isNotEmpty() && ev.timestampMs - timestamps.first > WINDOW_MS) {
            timestamps.removeFirst()
        }

        if (timestamps.size < MIN_EVENTS) return
        if (ev.timestampMs - lastZombieAt < COOLDOWN_MS) return

        // 평균/표준편차 계산.
        val intervals = mutableListOf<Long>()
        val it = timestamps.iterator()
        var prev = it.next()
        while (it.hasNext()) {
            val cur = it.next()
            intervals += cur - prev
            prev = cur
        }
        val avg = intervals.average()
        val std = run {
            val mean = avg
            sqrt(intervals.sumOf { (it - mean) * (it - mean) } / intervals.size)
        }

        // 시간대별 임계치 결정 — 취약시간이면 더 쉽게 트리거.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isVulnerable = vulnerableTimeLearner.isVulnerableNow(hour)
        val threshold = if (isVulnerable) THRESHOLD_VULNERABLE_MS else THRESHOLD_MS

        if (avg < threshold && std < STD_MAX_MS) {
            lastZombieAt = ev.timestampMs

            // 학습기에 시간대 점수 가산.
            vulnerableTimeLearner.recordZombie(hour)

            _zombieStateFlow.value = ZombieDetection(
                packageName = ev.packageName,
                avgIntervalMs = avg.toLong(),
                stdDevMs = std.toLong(),
                eventCount = timestamps.size,
                detectedAt = ev.timestampMs,
                hourOfDay = hour,
                isVulnerableHour = isVulnerable,
            )
        }
    }

    /**
     * 팝업이 닫힐 때 호출. 제어 플래그만 리셋하고 누적 데이터는 건드리지 않는다.
     * - lastZombieAt = 0: 쿨다운 해제 → 다음 스크롤 패턴 감지 시 즉시 재트리거 가능
     * - _zombieStateFlow = null: stale 감지값 제거 → collectLatest 가 다음 감지를 새 값으로 수신
     */
    fun reset() {
        lastZombieAt = 0L
        _zombieStateFlow.value = null
    }

    companion object {
        private const val WINDOW_MS = 5_000L
        private const val MIN_EVENTS = 5
        private const val THRESHOLD_MS = 800.0           // 평소 시간대
        private const val THRESHOLD_VULNERABLE_MS = 1000.0 // 취약 시간대 — 1초 미만이면 트리거
        private const val STD_MAX_MS = 300.0             // 표준편차가 너무 크면 의도적 사용으로 판단
        private const val COOLDOWN_MS = 10 * 60_000L     // 10분 쿨다운
    }
}

/**
 * 좀비 판정 결과.
 * OverlayService 가 받아서 코칭 멘트 생성에 사용.
 */
data class ZombieDetection(
    val packageName: String,
    val avgIntervalMs: Long,
    val stdDevMs: Long,
    val eventCount: Int,
    val detectedAt: Long,
    val hourOfDay: Int,
    val isVulnerableHour: Boolean,
)
