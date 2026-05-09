package com.groomteam2.dopamind.di

import android.content.Context
import com.groomteam2.dopamind.ai.CoachingEngine
import com.groomteam2.dopamind.ai.GeminiClient
import com.groomteam2.dopamind.ai.PatternFeedbackEngine
import com.groomteam2.dopamind.analyzer.PatternAnalyzer
import com.groomteam2.dopamind.analyzer.VulnerableTimeLearner
import com.groomteam2.dopamind.data.db.AppDatabase
import com.groomteam2.dopamind.data.prefs.UserPrefs
import com.groomteam2.dopamind.data.repo.ChallengeRepository
import com.groomteam2.dopamind.data.repo.PointRepository
import com.groomteam2.dopamind.data.repo.UserRepository

/**
 * 수동 DI 컨테이너.
 *
 * Hilt/Koin 같은 정식 DI 프레임워크를 도입하면 보일러플레이트가 줄지만
 * 해커톤 MVP 단계에서는 빌드시간 단축과 디버깅 단순화를 위해 직접 관리한다.
 *
 * 사용처:
 *  - ViewModel: ServiceLocator.userRepository 등으로 의존성 주입
 *  - Service: 백그라운드 컴포넌트도 동일하게 접근
 *
 * 모든 인스턴스는 lazy 로 초기화 + 앱 컨텍스트 기반 싱글톤이라 메모리 누수 위험 없음.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── 데이터 ───────────────────────────────────────────────────
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val userPrefs: UserPrefs by lazy { UserPrefs(appContext) }

    val userRepository: UserRepository by lazy {
        UserRepository(database.userProfileDao(), userPrefs)
    }
    val pointRepository: PointRepository by lazy {
        PointRepository(database.pointLedgerDao())
    }
    val challengeRepository: ChallengeRepository by lazy {
        ChallengeRepository(database.challengeDao())
    }

    // ── 분석/학습 ────────────────────────────────────────────────
    val vulnerableTimeLearner: VulnerableTimeLearner by lazy {
        VulnerableTimeLearner(userPrefs)
    }
    val patternAnalyzer: PatternAnalyzer by lazy {
        PatternAnalyzer(vulnerableTimeLearner)
    }

    // ── AI ───────────────────────────────────────────────────────
    val geminiClient: GeminiClient by lazy { GeminiClient() }
    val coachingEngine: CoachingEngine by lazy {
        CoachingEngine(geminiClient, userRepository, pointRepository, vulnerableTimeLearner)
    }
    val patternFeedbackEngine: PatternFeedbackEngine by lazy {
        PatternFeedbackEngine(
            geminiClient,
            userRepository,
            pointRepository,
            challengeRepository,
            vulnerableTimeLearner,
        )
    }
}
