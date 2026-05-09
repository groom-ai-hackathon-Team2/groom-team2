package com.groomteam2.dopamind.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private fun hourKey(hour: Int): Preferences.Key<Float> =
            floatPreferencesKey("vulnerable_$hour")
    }
}
