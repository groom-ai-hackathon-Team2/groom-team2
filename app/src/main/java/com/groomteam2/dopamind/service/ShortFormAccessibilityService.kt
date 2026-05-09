package com.groomteam2.dopamind.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.groomteam2.dopamind.analyzer.ScrollEvent
import com.groomteam2.dopamind.analyzer.ScrollEventBus
import com.groomteam2.dopamind.analyzer.ShortsDetector
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
 *  1) onAccessibilityEvent: 매우 가볍게 — ShortsDetector 로 숏폼 탭 여부 검사 후 통과하면 publish.
 *     (일반 유튜브 영상 목록 / 인스타 피드는 통과 안 됨)
 *  2) 분석 결과(좀비 판정)를 PatternAnalyzer 에서 수신하면 OverlayService 시작.
 *  3) 활성 챌린지가 있을 때 대상 앱이 다시 포그라운드면 챌린지 실패 처리 — 단 숏폼 탭일 때만.
 *  4) 패키지 진입 감지 → 타이머 인트로 트리거 — 단 숏폼/릴스 탭일 때만.
 *
 * 주의:
 *  - 접근성 서비스의 콜백은 메인 스레드. 무거운 작업 절대 금지.
 *    ShortsDetector 의 트리 탐색은 1초 TTL 캐시로 보호됨.
 *  - 패키지 필터는 accessibility_service_config.xml 에서 이미 한정되어 있음.
 *  - canRetrieveWindowContent=true 로 변경됨 — 단, 영상/댓글 텍스트는 사용하지 않고
 *    Shorts/Reels 탭의 isSelected / 리믹스·오디오트랙 등의 contentDescription 만 검사.
 */
class ShortFormAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var analysisJob: Job? = null
    private var lastPackage: String? = null

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return

        // 패키지가 바뀌었으면 캐시 무효화 — 다른 앱의 잔존 결과를 잘못 적용하지 않도록.
        if (pkg != lastPackage) {
            ShortsDetector.invalidate()
            lastPackage = pkg
        }

        // 이 두 트리거(스크롤/윈도우전환) 모두 "지금 정말 숏폼/릴스 탭인지" 검사를 통과해야 동작.
        // 검사 자체는 1초 TTL 캐시로 보호되어 빠른 연속 스크롤에도 트리 탐색은 1번만 일어남.
        val inShorts = ShortsDetector.isShortsTabActive(this, pkg)

        when (ev.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (inShorts) {
                    ScrollEventBus.publish(ScrollEvent(System.currentTimeMillis(), pkg))
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 숏폼 탭 진입 감지 → 타이머 인트로 트리거. 이미 타이머 동작 중이면 maybeStartTimerIntro 가 무시.
                if (inShorts) {
                    scope.launch { maybeStartTimerIntro() }
                }
            }
        }

        // 챌린지 실패 검사 — 챌린지 중에는 어떤 식으로든 대상 앱을 켜는 것 자체가 실패이므로
        // 숏폼 탭 여부와 무관하게 작동. (피드만 잠깐 본다고 봐주지 않는 게 챌린지의 약속)
        scope.launch { checkChallengeFailure(pkg) }
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
        ShortsDetector.invalidate()
    }

    companion object {
        private val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
        )
    }
}
