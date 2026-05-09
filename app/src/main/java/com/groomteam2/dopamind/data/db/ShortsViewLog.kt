package com.groomteam2.dopamind.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 숏폼 1개 시청 완료 로그.
 * 스크롤 이벤트 1건 = 영상 1개 시청 완료로 간주하여 1행씩 적재.
 *
 * 프라이버시: 어떤 콘텐츠인지/제목/URL 등은 일체 저장하지 않고
 * 패키지명 + 타임스탬프(+조회 편의용 dayKey) 만 보관.
 */
@Entity(
    tableName = "shorts_view_log",
    indices = [
        Index("timestamp"),
        Index("dayKey"),
        Index("packageName"),
    ],
)
data class ShortsViewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val dayKey: String,
)

/**
 * 패키지별 시청 수 (DAO 결과 매핑용).
 */
data class PackageViewCount(
    val packageName: String,
    val count: Int,
)

/**
 * 시간대별 시청 수 (DAO 결과 매핑용).
 */
data class HourlyViewCount(
    val hourOfDay: Int,
    val count: Int,
)

@Dao
interface ShortsViewLogDao {
    @Insert
    suspend fun insert(log: ShortsViewLog): Long

    @Query("SELECT COUNT(*) FROM shorts_view_log WHERE timestamp >= :since")
    fun countSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM shorts_view_log")
    fun totalCount(): Flow<Int>

    @Query(
        "SELECT packageName, COUNT(*) AS count FROM shorts_view_log " +
                "WHERE timestamp >= :since GROUP BY packageName"
    )
    fun countByPackageSince(since: Long): Flow<List<PackageViewCount>>

    @Query(
        "SELECT timestamp FROM shorts_view_log " +
                "WHERE timestamp >= :since ORDER BY timestamp ASC"
    )
    fun timestampsSince(since: Long): Flow<List<Long>>

    @Query(
        "SELECT CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hourOfDay, " +
                "COUNT(*) AS count FROM shorts_view_log " +
                "WHERE timestamp >= :since GROUP BY hourOfDay"
    )
    fun countByHourSince(since: Long): Flow<List<HourlyViewCount>>
}