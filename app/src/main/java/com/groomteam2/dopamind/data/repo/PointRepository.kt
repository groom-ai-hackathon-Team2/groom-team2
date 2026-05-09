package com.groomteam2.dopamind.data.repo

import com.groomteam2.dopamind.data.db.PointLedger
import com.groomteam2.dopamind.data.db.PointLedgerDao
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 포인트 적립/차감 + 잔액 조회.
 *
 * 이유 코드(reason) 는 통계 화면에서 활용 가능하도록 자유 문자열.
 * 도메인 의미는 호출자(ChallengeWorker, Overlay 무시 처리 등)에서 부여.
 */
class PointRepository(private val dao: PointLedgerDao) {

    val balance: Flow<Int> = dao.totalBalance()
    val recent: Flow<List<PointLedger>> = dao.recent()

    suspend fun add(delta: Int, reason: String, challengeId: Long? = null): Long =
        dao.insert(PointLedger(delta = delta, reason = reason, challengeId = challengeId))

    fun earnedToday(): Flow<Int> = dao.earnedSince(startOfTodayMs())

    private fun startOfTodayMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
