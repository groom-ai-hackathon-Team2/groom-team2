package com.groomteam2.dopamind.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 포인트 원장(Ledger).
 *
 * 잔액을 단일 컬럼으로 두지 않고 모든 가감 내역을 행으로 쌓는 구조.
 * - 추후 활동 내역 화면을 만들기 쉬움
 * - 디버깅 시 어디서 차감/적립됐는지 추적 가능
 */
@Entity(tableName = "point_ledger")
data class PointLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val delta: Int,           // +적립 / -차감
    val reason: String,       // "challenge_success", "ignore_decay" 등
    val challengeId: Long? = null,
)

@Dao
interface PointLedgerDao {
    @Insert
    suspend fun insert(entry: PointLedger): Long

    @Query("SELECT IFNULL(SUM(delta), 0) FROM point_ledger")
    fun totalBalance(): Flow<Int>

    @Query("SELECT IFNULL(SUM(delta), 0) FROM point_ledger WHERE createdAt >= :since AND delta > 0")
    fun earnedSince(since: Long): Flow<Int>

    @Query("SELECT * FROM point_ledger ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<PointLedger>>
}
