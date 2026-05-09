package com.groomteam2.dopamind.data.repo

import com.groomteam2.dopamind.data.db.UserProfile
import com.groomteam2.dopamind.data.db.UserProfileDao
import com.groomteam2.dopamind.data.prefs.UserPrefs
import kotlinx.coroutines.flow.Flow

/**
 * 사용자 프로필/온보딩 상태 통합 액세스.
 *
 * Room(영구 프로필) + DataStore(플래그)를 한 인터페이스로 묶어
 * ViewModel 이 두 저장소를 동시에 알 필요 없게 한다.
 */
class UserRepository(
    private val dao: UserProfileDao,
    private val prefs: UserPrefs,
) {
    val profileFlow: Flow<UserProfile?> = dao.observe()
    val onboardingDoneFlow: Flow<Boolean> = prefs.onboardingDone

    suspend fun saveProfile(job: String, schedule: String) {
        dao.upsert(UserProfile(job = job.trim(), schedule = schedule.trim()))
        prefs.setOnboardingDone(true)
    }

    suspend fun currentProfile(): UserProfile? = dao.get()
}
