package com.groomteam2.dopamind.analyzer

import com.groomteam2.dopamind.data.repo.ShortsStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ScrollEventBus 의 또 다른 구독자.
 *
 * 책임:
 *  1) 모든 스크롤 이벤트를 ShortsViewLog 에 1행씩 기록 (= 영상 1개 시청).
 *  2) 패키지별 시청 세션을 메모리에서 추적 → 30초 침묵 시 ShortsSessionLog 로 flush.
 *
 * 주의:
 *  - PatternAnalyzer 는 SharedFlow 의 별도 collect 로 받고 있으므로,
 *    이 컬렉터 추가만으로 기존 좀비 판정 동작에는 아무 영향 없음.
 *  - DB insert 는 짧은 코루틴 launch 로 수행 — 콜백 스레드 블로킹 회피.
 */
class ShortsStatsCollector(
    private val repository: ShortsStatsRepository,
) {
    private data class SessionState(val startMs: Long, var lastMs: Long)

    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, SessionState>()

    private var collectorJob: Job? = null
    private var sweeperJob: Job? = null

    /**
     * 분석 시작. 이미 동작 중이면 무시(중복 구독 방지).
     * AccessibilityService.onServiceConnected 또는 동등한 진입점에서 호출.
     */
    fun start(scope: CoroutineScope) {
        if (collectorJob?.isActive == true) return

        collectorJob = scope.launch {
            ScrollEventBus.events.collect { ev -> handle(scope, ev) }
        }

        // 주기적으로 휴면 세션 flush (이벤트 콜백 안에서 직접 처리하지 않아 콜백을 짧게 유지).
        sweeperJob = scope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MS)
                sweepStaleSessions()
            }
        }
    }

    private fun handle(scope: CoroutineScope, ev: ScrollEvent) {
        // 1) View 로그 적재. 짧은 IO 작업 — 백그라운드 코루틴에 던짐.
        scope.launch { repository.recordView(ev.packageName, ev.timestampMs) }

        // 2) 패키지별 세션 상태 갱신.
        scope.launch {
            mutex.withLock {
                val state = sessions[ev.packageName]
                if (state == null) {
                    sessions[ev.packageName] = SessionState(ev.timestampMs, ev.timestampMs)
                } else {
                    state.lastMs = ev.timestampMs
                }
            }
        }
    }

    private suspend fun sweepStaleSessions() {
        val now = System.currentTimeMillis()
        val toFinalize = mutex.withLock {
            val ready = sessions.entries
                .filter { now - it.value.lastMs >= SESSION_TIMEOUT_MS }
                .map { it.key to it.value.copy() }
            ready.forEach { (k, _) -> sessions.remove(k) }
            ready
        }
        for ((pkg, state) in toFinalize) {
            // 0초짜리(이벤트 1개뿐) 세션도 그대로 기록 — UI 에서 시청 시간이 0분으로 보일 뿐.
            repository.recordSession(pkg, state.startMs, state.lastMs)
        }
    }

    companion object {
        /** 마지막 이벤트 후 이 시간 이상 침묵하면 세션 종료로 간주. */
        const val SESSION_TIMEOUT_MS = 30_000L

        /** 휴면 세션 검사 주기. 너무 짧으면 CPU 낭비, 너무 길면 종료 지연. */
        private const val SWEEP_INTERVAL_MS = 5_000L
    }
}