package com.groomteam2.dopamind.analyzer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 현재 화면이 정말로 숏폼/릴스 탭인지 판정.
 *
 * 일반 유튜브 영상 목록(홈/구독/검색결과) 스크롤이나 인스타 피드 스크롤은
 * 도파민 좀비 카운트에 잡히면 안 됨 → onAccessibilityEvent 에서
 * scroll publish + 타이머 트리거 전 이 함수로 게이팅.
 *
 * 검사 방식:
 *  1) 하단 탭바의 "Shorts/쇼츠/Reels/릴스" 노드가 isSelected=true 인지
 *  2) 또는 화면 안에 숏폼 전용 UI (리믹스/오디오 트랙/오디오 사용/Remix 등)
 *     contentDescription 이 존재하는지
 *  → 둘 중 하나라도 충족 시 true
 *
 * 성능:
 *  - 트리 깊이 MAX_DEPTH 까지만 탐색
 *  - 패키지+윈도우 단위로 1초 TTL 캐시 → 빠른 연속 스크롤 시 트리 재탐색 회피
 *  - 틱톡은 앱 자체가 숏폼이므로 항상 true (탐색 생략)
 */
object ShortsDetector {

    private const val MAX_DEPTH = 8
    private const val CACHE_TTL_MS = 1_000L

    private val TIKTOK_PKGS = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /**
     * Shorts/Reels 탭 자체의 contentDescription / text — isSelected=true 도 함께 보아야 함.
     * 일반 유튜브 영상 목록에서도 하단 Shorts 탭은 보이지만 isSelected=false 이므로 통과 안 됨.
     */
    private val YOUTUBE_TAB_LABELS = listOf("Shorts", "쇼츠")
    private val INSTAGRAM_TAB_LABELS = listOf("Reels", "릴스")

    /**
     * 강한 신호 — 이 contentDescription/text 가 화면에 보인다면
     * 사용자는 분명히 숏폼/릴스 화면 안에 있다고 판단 (탭 선택 여부 무관).
     * 일반 영상 목록에는 절대 등장하지 않는 단어들.
     */
    private val YOUTUBE_STRONG_HINTS = listOf(
        "리믹스", "Remix",
        "오디오 트랙", "Audio track",
        "Sound", "사운드",
    )
    private val INSTAGRAM_STRONG_HINTS = listOf(
        "리믹스", "Remix",
        "오디오 사용", "Use audio",
        "Audio page", "오디오 페이지",
    )

    private var cachePackage: String? = null
    private var cacheValue: Boolean = false
    private var cacheAt: Long = 0L

    /**
     * @param service 호출자가 가진 AccessibilityService 인스턴스. rootInActiveWindow 접근용.
     * @param packageName 이벤트가 발생한 패키지명.
     * @return 현재 화면이 숏폼/릴스로 판정되면 true.
     */
    fun isShortsTabActive(service: AccessibilityService, packageName: String): Boolean {
        // 틱톡은 항상 숏폼.
        if (packageName in TIKTOK_PKGS) return true

        val now = System.currentTimeMillis()
        if (cachePackage == packageName && now - cacheAt < CACHE_TTL_MS) {
            return cacheValue
        }

        val root: AccessibilityNodeInfo? = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        val result = root?.let { detect(it, packageName) } ?: false

        cachePackage = packageName
        cacheValue = result
        cacheAt = now
        return result
    }

    /** 캐시 무효화 — 패키지가 바뀐 직후 등 호출자가 강제로 재계산하고 싶을 때. */
    fun invalidate() {
        cachePackage = null
        cacheAt = 0L
    }

    private fun detect(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val tabLabels = when (packageName) {
            "com.google.android.youtube" -> YOUTUBE_TAB_LABELS
            "com.instagram.android" -> INSTAGRAM_TAB_LABELS
            else -> emptyList()
        }
        val strongHints = when (packageName) {
            "com.google.android.youtube" -> YOUTUBE_STRONG_HINTS
            "com.instagram.android" -> INSTAGRAM_STRONG_HINTS
            else -> emptyList()
        }
        if (tabLabels.isEmpty() && strongHints.isEmpty()) return false

        return walk(root, tabLabels, strongHints, depth = 0)
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        tabLabels: List<String>,
        strongHints: List<String>,
        depth: Int,
    ): Boolean {
        node ?: return false
        if (depth > MAX_DEPTH) return false

        val cd = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()

        // (1) 강한 신호 — 리믹스/오디오 트랙 등은 화면에 떠 있기만 해도 숏폼.
        for (hint in strongHints) {
            if (cd.contains(hint, ignoreCase = true) || text.contains(hint, ignoreCase = true)) {
                return true
            }
        }

        // (2) 탭바 라벨 + isSelected — 하단 탭이 실제로 선택된 상태일 때만 인정.
        if (node.isSelected) {
            for (label in tabLabels) {
                if (cd.contains(label, ignoreCase = true) || text.contains(label, ignoreCase = true)) {
                    return true
                }
            }
        }

        // (3) 자식 재귀.
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (walk(child, tabLabels, strongHints, depth + 1)) return true
        }
        return false
    }
}
