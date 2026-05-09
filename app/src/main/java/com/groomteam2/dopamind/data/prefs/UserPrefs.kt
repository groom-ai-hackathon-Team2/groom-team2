package com.groomteam2.dopamind.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    // 사용자가 메인에서 설정하는 목표 시간(분). 인스타/유튜브 진입 시 이 값으로 카운트다운 시작.
    val goalTimerMinutes: Flow<Int> = store.data.map { it[KEY_GOAL_TIMER_MIN] ?: DEFAULT_GOAL_MIN }

    suspend fun setGoalTimerMinutes(value: Int) {
        store.edit { it[KEY_GOAL_TIMER_MIN] = value }
    }

    // 현재 진행 중인 타이머의 만료 시각 (epoch ms). 0 이면 없음.
    val activeTimerEndAt: Flow<Long> = store.data.map { it[KEY_TIMER_END_AT] ?: 0L }

    suspend fun setActiveTimerEndAt(value: Long) {
        store.edit { it[KEY_TIMER_END_AT] = value }
    }

    // 인트로 팝업에서 사용자가 [시작하기] 누른 시점의 보상 — 만료 시 그 값으로 지급.
    val activeTimerReward: Flow<Int> = store.data.map { it[KEY_TIMER_REWARD] ?: REWARD_INTRO_MAX }

    suspend fun setActiveTimerReward(value: Int) {
        store.edit { it[KEY_TIMER_REWARD] = value }
    }

    companion object {
        const val DEFAULT_GOAL_MIN = 5
        const val REWARD_INTRO_MAX = 30   // 인트로 팝업 시작값
        const val REWARD_INTRO_MIN = 10   // 인트로 팝업 최소값(이 밑으로 안 깎임)

        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_GOAL_TIMER_MIN = intPreferencesKey("goal_timer_min")
        private val KEY_TIMER_END_AT = longPreferencesKey("timer_end_at")
        private val KEY_TIMER_REWARD = intPreferencesKey("timer_reward")
        private fun hourKey(hour: Int): Preferences.Key<Float> =
            floatPreferencesKey("vulnerable_$hour")
    }
}
