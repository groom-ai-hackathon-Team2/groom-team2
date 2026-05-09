package com.groomteam2.dopamind.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 작은 설정/플래그 저장.
 *
 * Room 에 두기엔 과한 데이터(부울 플래그, 시간대별 EMA 가중치)를 모아두는 곳.
 * - onboardingDone: 온보딩 완료 여부 — 시작 화면 분기
 * - vulnerable_<hour>: 0..23 시간대 별 좀비 판정 EMA 점수 (VulnerableTimeLearner 가 사용)
 */
private val Context.dataStore by preferencesDataStore(name = "dopamind_prefs")

class UserPrefs(private val context: Context) {

    private val store get() = context.dataStore

    val onboardingDone: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(value: Boolean) {
        store.edit { it[KEY_ONBOARDING_DONE] = value }
    }

    /** 시간대(0..23)별 EMA 점수. 평소보다 높을수록 '취약 시간대'로 판단. */
    fun vulnerableScore(hour: Int): Flow<Float> =
        store.data.map { it[hourKey(hour)] ?: 0f }

    suspend fun updateVulnerableScore(hour: Int, value: Float) {
        store.edit { it[hourKey(hour)] = value }
    }

    /** 24개 시간대를 한 번에 읽음. 홈 화면이 현재 시간이 취약시간인지 판단할 때 사용. */
    fun allVulnerableScores(): Flow<FloatArray> = store.data.map { prefs ->
        FloatArray(24) { i -> prefs[hourKey(i)] ?: 0f }
    }

    // ── 앱 타이머 ────────────────────────────────────────────────
    // 사용자가 메인에서 설정하는 목표 시간(초). 30..3600 사이의 프리셋 중 선택.
    // 기존 분 단위 키와 공존 — 새 키가 비어 있으면 분 키를 ×60 해서 마이그레이션.
    val goalTimerSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[KEY_GOAL_TIMER_SEC]
            ?: prefs[KEY_GOAL_TIMER_MIN]?.let { it * 60 }
            ?: DEFAULT_GOAL_SEC
    }

    suspend fun setGoalTimerSeconds(value: Int) {
        store.edit { it[KEY_GOAL_TIMER_SEC] = value }
    }

    // 현재 진행 중인 타이머의 만료 시각 (epoch ms). 0 이면 없음.
    val activeTimerEndAt: Flow<Long> = store.data.map { it[KEY_TIMER_END_AT] ?: 0L }

    suspend fun setActiveTimerEndAt(value: Long) {
        store.edit { it[KEY_TIMER_END_AT] = value }
    }

    // ── 구독 플랜 ────────────────────────────────────────────────
    // 수익화 모델: STANDARD 는 기본 리포트만, PRO 는 심층 섹션 잠금 해제.
    val planTier: Flow<PlanTier> = store.data.map {
        when (it[KEY_PLAN_TIER]) {
            "PRO" -> PlanTier.PRO
            else -> PlanTier.STANDARD
        }
    }

    suspend fun setPlanTier(tier: PlanTier) {
        store.edit { it[KEY_PLAN_TIER] = tier.name }
    }

    // ── 마지막 AI 리포트 캐시 ────────────────────────────────────
    // Gemini 호출은 비싸고 느려서 가장 최근 1건만 캐시. 탭 재진입 시 즉시 표시.
    val lastReportText: Flow<String> = store.data.map { it[KEY_LAST_REPORT_TEXT] ?: "" }
    val lastReportPlan: Flow<String> = store.data.map { it[KEY_LAST_REPORT_PLAN] ?: "" }
    val lastReportAt: Flow<Long> = store.data.map { it[KEY_LAST_REPORT_AT] ?: 0L }

    suspend fun saveReport(text: String, plan: PlanTier, atMs: Long) {
        store.edit {
            it[KEY_LAST_REPORT_TEXT] = text
            it[KEY_LAST_REPORT_PLAN] = plan.name
            it[KEY_LAST_REPORT_AT] = atMs
        }
    }

    companion object {
        const val DEFAULT_GOAL_SEC = 300 // 5분
        /** 사용자가 선택할 수 있는 타이머 프리셋(초 단위). 30초 ~ 1시간. */
        val GOAL_PRESETS_SEC: List<Int> = listOf(30, 60, 180, 300, 600, 900, 1800, 3600)

        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_GOAL_TIMER_MIN = intPreferencesKey("goal_timer_min")
        private val KEY_GOAL_TIMER_SEC = intPreferencesKey("goal_timer_sec")
        private val KEY_TIMER_END_AT = longPreferencesKey("timer_end_at")
        private val KEY_PLAN_TIER = stringPreferencesKey("plan_tier")
        private val KEY_LAST_REPORT_TEXT = stringPreferencesKey("last_report_text")
        private val KEY_LAST_REPORT_PLAN = stringPreferencesKey("last_report_plan")
        private val KEY_LAST_REPORT_AT = longPreferencesKey("last_report_at")
        private fun hourKey(hour: Int): Preferences.Key<Float> =
            floatPreferencesKey("vulnerable_$hour")
    }
}

/** 사용자 구독 등급. STANDARD: 기본 리포트, PRO: 심층 섹션까지 잠금 해제. */
enum class PlanTier { STANDARD, PRO }
