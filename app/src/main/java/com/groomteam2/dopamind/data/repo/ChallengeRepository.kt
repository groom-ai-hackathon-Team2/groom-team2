package com.groomteam2.dopamind.data.repo

import com.groomteam2.dopamind.data.db.ChallengeDao
import com.groomteam2.dopamind.data.db.ChallengeRecord
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 챌린지 생성/상태 갱신.
 *
 * 챌린지가 활성 상태일 때 AccessibilityService 가 대상 앱 재진입을 감지하면 markFailed,
 * ChallengeWorker 가 약속 시간만큼 지나면 markSuccess 를 호출.
 */
class ChallengeRepository(private val dao: ChallengeDao) {

    val recent: Flow<List<ChallengeRecord>> = dao.recent()

    fun successCountToday(): Flow<Int> = dao.successCountSince(startOfTodayMs())

    suspend fun createPending(durationMinutes: Int, rewardPoints: Int, packages: List<String>): Long {
        return dao.insert(
            ChallengeRecord(
                createdAt = System.currentTimeMillis(),
                durationMinutes = durationMinutes,
                rewardPoints = rewardPoints,
                targetPackages = packages.joinToString(","),
            )
        )
    }

    suspend fun activeChallenge(): ChallengeRecord? = dao.activePending()

    suspend fun markSuccess(id: Long) {
        dao.byId(id)?.let {
            dao.update(it.copy(status = ChallengeRecord.STATUS_SUCCESS, finishedAt = System.currentTimeMillis()))
        }
    }

    suspend fun markFailed(id: Long) {
        dao.byId(id)?.let {
            dao.update(it.copy(status = ChallengeRecord.STATUS_FAILED, finishedAt = System.currentTimeMillis()))
        }
    }

    private fun startOfTodayMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
