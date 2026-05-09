package com.groomteam2.dopamind.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 챌린지 기록.
 *
 * 코치가 제안 → 사용자가 수락한 시점에 PENDING 으로 생성.
 * - ChallengeWorker 가 시간이 지나면 SUCCESS 로 갱신하고 포인트 지급
 * - AccessibilityService 가 챌린지 시간 안에 대상 앱 재진입을 감지하면 FAILED 로 갱신
 */
@Entity(tableName = "challenge_record")
data class ChallengeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val durationMinutes: Int,
    val rewardPoints: Int,
    val targetPackages: String,        // 콤마 구분 — 챌린지 대상 앱 패키지
    val status: String = STATUS_PENDING,
    val finishedAt: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
    }
}

@Dao
interface ChallengeDao {
    @Insert
    suspend fun insert(record: ChallengeRecord): Long

    @Update
    suspend fun update(record: ChallengeRecord)

    @Query("SELECT * FROM challenge_record WHERE status = 'PENDING' LIMIT 1")
    suspend fun activePending(): ChallengeRecord?

    @Query("SELECT * FROM challenge_record WHERE id = :id")
    suspend fun byId(id: Long): ChallengeRecord?

    @Query("SELECT * FROM challenge_record ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int = 30): Flow<List<ChallengeRecord>>

    @Query("SELECT COUNT(*) FROM challenge_record WHERE status = 'SUCCESS' AND finishedAt >= :since")
    fun successCountSince(since: Long): Flow<Int>
}
