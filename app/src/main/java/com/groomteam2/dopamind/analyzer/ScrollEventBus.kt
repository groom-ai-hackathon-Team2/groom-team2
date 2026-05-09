package com.groomteam2.dopamind.analyzer

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * AccessibilityService 와 PatternAnalyzer 사이의 브릿지.
 *
 * 서비스는 짧은 콜백 안에서 빠르게 이벤트를 흘려보내야 하므로
 * 분석 로직을 직접 호출하지 않고 SharedFlow 에 emit 만 한다.
 *
 * extraBufferCapacity: 잠깐 동안 받을 수 있는 버퍼.
 * onBufferOverflow=DROP_OLDEST: 오래된 이벤트가 쌓여도 분석엔 무의미하므로 버린다(메모리 보호).
 */
object ScrollEventBus {

    private val _events = MutableSharedFlow<ScrollEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events get() = _events

    fun publish(event: ScrollEvent) {
        // tryEmit 은 SharedFlow 에서 비동기 안전. 실패해도 분석 정확도에 큰 영향 없음.
        _events.tryEmit(event)
    }
}

/**
 * 한 번의 스크롤 이벤트.
 * - timestampMs: 이벤트 발생 시각 (System.currentTimeMillis 기준)
 * - packageName: 어느 앱에서 발생했는지 (인스타/유튜브/...).
 *   분석에는 패키지 단위 윈도우를 사용하므로 필수.
 */
data class ScrollEvent(
    val timestampMs: Long,
    val packageName: String,
)
