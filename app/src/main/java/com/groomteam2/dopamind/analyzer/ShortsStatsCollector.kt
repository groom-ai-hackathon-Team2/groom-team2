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
 *  2) 패키지별 시청 세션을 메모리에서 추적.
 *  3) 진행 중 세션 상태를 ActiveShortsSessionRegistry 에 publish — UI 가 실시간으로 누적 시간을 표시.
 *  4) 마지막 이벤트 후 SESSION_TIMEOUT_MS 동안 침묵하면 ShortsSessionLog 로 flush.
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

    fun start(scope: CoroutineScope) {
        if (collectorJob?.isActive == true) return

        collectorJob = scope.launch {
            ScrollEventBus.events.collect { ev -> handle(scope, ev) }
        }

        // 주기적으로 휴면 세션 flush + 진행 중 세션 스냅샷 publish.
        sweeperJob = scope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MS)
                sweepStaleSessions()
                publishSnapshot()
            }
        }
    }

    private fun handle(scope: CoroutineScope, ev: ScrollEvent) {
        // 1) View 로그 적재. 짧은 IO 작업 — 백그라운드 코루틴에 던짐.
        scope.launch { repository.recordView(ev.packageName, ev.timestampMs) }

        // 2) 패키지별 세션 상태 갱신 후 즉시 스냅샷 publish — UI 가 즉시 반영.
        scope.launch {
            mutex.withLock {
                val state = sessions[ev.packageName]
                if (state == null) {
                    sessions[ev.packageName] = SessionState(ev.timestampMs, ev.timestampMs)
                } else {
                    state.lastMs = ev.timestampMs
                }
            }
            publishSnapshot()
        }
    }

    /**
     * 휴면 세션 마무리.
     *
     * 종료 시점은 "현재 시각(now)" — 즉, 라이브 UI 가 보여주던 누적값(now - startMs)을
     * 그대로 DB 에 기록한다. 이렇게 하지 않으면 활성→완료 전환 직후 UI 가 한 번에 줄어들어
     * "1분 → 0분" 으로 떨어지는 버그가 난다.
     *
     * 효과: "스크롤 첫 발생 ~ 30초 침묵 감지 시점" 까지의 시간이 모두 시청 시간으로 누적된다.
     * 사용자가 영상 한 개를 멈춰서 보는 시간(=스크롤이 잠시 없는 시간)도 포함됨 — 사용자 요구사항.
     */
    private suspend fun sweepStaleSessions() {
        val now = System.currentTimeMillis()
        val toFinalize = mutex.withLock {
            val ready = sessions.entries
                .filter { now - it.value.lastMs >= SESSION_TIMEOUT_MS }
                .map { it.key to it.value.copy() }
            ready.forEach { (k, _) -> sessions.remove(k) }
            ready
        }
        // in-memory 에서 빠진 직후 레지스트리도 즉시 비워, DB insert 와의 일시적 이중 계산을 차단.
        if (toFinalize.isNotEmpty()) publishSnapshot()
        for ((pkg, state) in toFinalize) {
            // endAt = now → DB durationSec = now - startMs (라이브 UI 가 보여주던 값과 동일)
            repository.recordSession(pkg, state.startMs, now)
        }
    }

    /** 진행 중 세션 스냅샷을 외부 레지스트리에 publish. */
    private suspend fun publishSnapshot() {
        val snapshot = mutex.withLock {
            sessions.entries.associate { (pkg, s) ->
                pkg to ActiveShortsSessionRegistry.Session(
                    packageName = pkg,
                    startMs = s.startMs,
                    lastMs = s.lastMs,
                )
            }
        }
        ActiveShortsSessionRegistry.update(snapshot)
    }

    companion object {
        /**
         * 마지막 이벤트 후 이 시간 이상 침묵하면 세션 종료로 간주.
         * 60초로 잡아 영상 1개를 멈춰서 보더라도 세션이 너무 빨리 끝나지 않게 함.
         * (접근성 서비스가 "대상 앱을 떠났다" 를 직접 감지하지 못하므로 침묵으로 추정.)
         */
        const val SESSION_TIMEOUT_MS = 60_000L

        /** 휴면 세션 검사 + 스냅샷 publish 주기. */
        private const val SWEEP_INTERVAL_MS = 1_000L
    }
}