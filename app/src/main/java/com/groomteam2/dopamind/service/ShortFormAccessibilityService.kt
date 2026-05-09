package com.groomteam2.dopamind.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.groomteam2.dopamind.analyzer.ScrollEvent
import com.groomteam2.dopamind.analyzer.ScrollEventBus
import com.groomteam2.dopamind.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * 숏폼 앱의 스크롤 이벤트를 받아 ScrollEventBus 로 발행하는 접근성 서비스.
 *
 * 핵심 책임:
 *  1) onAccessibilityEvent: 매우 가볍게 — 그냥 ScrollEventBus.publish 만.
 *  2) 분석 결과(좀비 판정)를 PatternAnalyzer 에서 수신하면 OverlayService 시작.
 *  3) 활성 챌린지가 있을 때 대상 앱이 다시 포그라운드면 챌린지 실패 처리.
 *
 * 주의:
 *  - 접근성 서비스의 콜백은 메인 스레드. 무거운 작업 절대 금지.
 *  - 패키지 필터는 accessibility_service_config.xml 에서 이미 한정되어 있음.
 *  - canRetrieveWindowContent=false 라 화면 텍스트는 절대 수집하지 않음.
 */
class ShortFormAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var analysisJob: Job? = null
    private var challengeWatchJob: Job? = null

    /**
     * YouTube 메인 Shorts 페이저의 마지막 카운트 시각 (디바운스용).
     * 단일 페이저라 패키지 1개에 단일 Long 으로 충분.
     */
    @Volatile private var lastYoutubeScrollCountedMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        // PatternAnalyzer 가 ScrollEventBus 를 구독하면서 좀비 상태를 판정.
        // 판정 결과 Flow 를 여기서 다시 수신해서 오버레이 트리거.
        analysisJob?.cancel()
        analysisJob = scope.launch {
            ServiceLocator.patternAnalyzer.start(scope)
            ServiceLocator.patternAnalyzer.zombieStateFlow.collectLatest { detection ->
                if (detection != null) {
                    OverlayService.start(applicationContext, detection.packageName)
                }
            }
        }

        // 숏폼 사용 통계 컬렉터 — ScrollEventBus 의 또 다른 구독자라
        // PatternAnalyzer 동작에 영향 없이 View/Session 로그만 추가로 적재.
        ServiceLocator.shortsStatsCollector.start(scope)

        // 활성 챌린지가 있을 때 대상 앱이 다시 떠오르면 실패 처리.
        challengeWatchJob?.cancel()
        challengeWatchJob = scope.launch {
            // (간단 구현) onAccessibilityEvent 의 패키지명만으로도 충분한 판단이 가능하므로
            // 별도 폴링은 두지 않고 onAccessibilityEvent 안에서 함께 처리.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return

        // [PROBE] 진단 로깅 — Shorts/Reels 메인 페이저 vs 댓글 리스트의 className/fromIndex 패턴 확인용.
        // 실측이 끝나면 import 와 함께 제거.
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val type = if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) "SCROLL" else "WINDOW"
            Log.d(
                "DopaProbe",
                "$type pkg=$pkg cls=${ev.className} from=${ev.fromIndex} to=${ev.toIndex} cnt=${ev.itemCount}"
            )
        }

        // 1) 스크롤 이벤트 — 패키지별 필터 적용 후 분석 버스로 흘려보냄.
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && shouldCountScroll(ev, pkg)) {
            ScrollEventBus.publish(ScrollEvent(System.currentTimeMillis(), pkg))
        }

        // 2) 인스타/유튜브/틱톡 등 대상 앱 진입 감지 → 타이머 인트로 팝업 트리거.
        //    이미 동작 중인 타이머가 있으면 트리거하지 않음 (재진입 무시).
        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg in TARGET_PACKAGES) {
            scope.launch { maybeStartTimerIntro() }
        }

        // 3) 활성 챌린지 대상 앱 재진입 감지 → 챌린지 실패.
        //    어떤 이벤트든 패키지명만 있으면 판단 가능.
        scope.launch { checkChallengeFailure(pkg) }
    }

    /**
     * 패키지별 스크롤 카운트 채택 여부.
     *
     * - YouTube: 실측 결과 메인 Shorts 페이저만 from=to=cnt=-1 (인덱스 미제공),
     *   댓글 RecyclerView 는 정상 인덱스(0+, cnt>0)를 발행. 이 차이로 댓글 차단 가능.
     *   추가로 한 스와이프의 잔여 이벤트(100~200ms 간격으로 3~4개) 가 있어 500ms 디바운스 적용.
     * - Instagram / TikTok: 인덱스로 메인/댓글 구별이 불가능해 기존 동작(모든 이벤트 통과) 유지.
     *
     * 위험: YouTube 가 빌드 업데이트로 인덱스 발행 정책을 바꾸면 카운트가 0 또는 과다로 회귀.
     * 현재 빌드의 logcat 패턴을 근거로 적용.
     */
    private fun shouldCountScroll(ev: AccessibilityEvent, pkg: String): Boolean {
        if (pkg != PKG_YOUTUBE) return true

        val isMainShortsPager =
            ev.fromIndex == -1 && ev.toIndex == -1 && ev.itemCount == -1
        if (!isMainShortsPager) return false

        val now = System.currentTimeMillis()
        if (now - lastYoutubeScrollCountedMs < YOUTUBE_DEBOUNCE_MS) return false
        lastYoutubeScrollCountedMs = now
        return true
    }

    private suspend fun maybeStartTimerIntro() {
        val endAt = ServiceLocator.userPrefs.activeTimerEndAt.firstOrNull() ?: 0L
        if (endAt > System.currentTimeMillis()) return // 타이머 동작 중 — 인트로 띄우지 않음
        TimerService.start(applicationContext, TimerService.ACTION_SHOW_INTRO)
    }

    private suspend fun checkChallengeFailure(currentPkg: String) {
        val challenge = ServiceLocator.challengeRepository.activeChallenge() ?: return
        val targets = challenge.targetPackages.split(",").map { it.trim() }
        if (currentPkg !in targets) return

        // 약속 시간이 아직 지나지 않았는데 대상 앱을 켰다면 실패.
        val deadline = challenge.createdAt + challenge.durationMinutes * 60_000L
        if (System.currentTimeMillis() < deadline) {
            ServiceLocator.challengeRepository.markFailed(challenge.id)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val PKG_YOUTUBE = "com.google.android.youtube"

        /** 한 스와이프의 잔여 스크롤 이벤트(100~200ms 간격) 묶기용 디바운스 임계치. */
        private const val YOUTUBE_DEBOUNCE_MS = 500L

        private val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            PKG_YOUTUBE,
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
        )
    }
}