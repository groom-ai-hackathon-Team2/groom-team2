package com.groomteam2.dopamind.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 스크롤 세션 통계.
 *
 * 하나의 "도파민 좀비 판정 이벤트"가 발생할 때마다 1행씩 기록.
 * 추후 분석/시각화용. 직접적인 코칭 로직보다는 사용자 회고에 활용.
 */
@Entity(tableName = "scroll_session")
data class ScrollSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,        // com.instagram.android 등
    val startedAt: Long,            // 세션 시작 epoch ms
    val endedAt: Long,              // 세션 종료 epoch ms
    val scrollCount: Int,           // 윈도우 내 스크롤 이벤트 개수
    val avgIntervalMs: Long,        // 평균 간격 — 작을수록 도파민 좀비 가능성↑
    val hourOfDay: Int,             // 0..23 — VulnerableTimeLearner 학습용
)

@Dao
interface ScrollSessionDao {
    @Insert
    suspend fun insert(session: ScrollSession): Long

    @Query("SELECT * FROM scroll_session ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<ScrollSession>>

    @Query("SELECT COUNT(*) FROM scroll_session WHERE startedAt >= :since")
    fun countSince(since: Long): Flow<Int>
}
