package com.groomteam2.dopamind.analyzer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 진행 중인 숏폼 시청 세션 메모리 레지스트리.
 *
 * - ShortsStatsCollector 가 세션 시작/갱신/종료 시 갱신.
 * - ShortsStatsRepository 가 "오늘/이번 주 시청 시간"을 계산할 때
 *   "DB 의 완료 세션 합계 + 현재 진행 중 세션의 실시간 경과" 를 더하는 데 사용.
 *
 * Collector ↔ Repository 양쪽이 직접 의존하면 DI 순환 문제가 생기므로 별도 싱글톤으로 분리.
 */
object ActiveShortsSessionRegistry {

    /** 진행 중 한 세션의 스냅샷. */
    data class Session(
        val packageName: String,
        val startMs: Long,
        val lastMs: Long,
    )

    private val _flow = MutableStateFlow<Map<String, Session>>(emptyMap())
    val flow: StateFlow<Map<String, Session>> = _flow

    fun update(snapshot: Map<String, Session>) {
        _flow.value = snapshot
    }
}