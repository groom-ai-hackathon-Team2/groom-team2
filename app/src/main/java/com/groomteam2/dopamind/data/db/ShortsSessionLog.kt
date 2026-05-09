package com.groomteam2.dopamind.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 숏폼 시청 세션.
 *
 * 한 세션 = 첫 스크롤 이벤트부터 마지막 이벤트까지 (마지막 이벤트 후 30초 이상 침묵 시 종료).
 * 누적 시청 시간 통계의 원천.
 */
@Entity(
    tableName = "shorts_session_log",
    indices = [
        Index("startTime"),
        Index("dayKey"),
    ],
)
data class ShortsSessionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSec: Long,
    val dayKey: String,
)

@Dao
interface ShortsSessionLogDao {
    @Insert
    suspend fun insert(session: ShortsSessionLog): Long

    @Query(
        "SELECT IFNULL(SUM(durationSec), 0) FROM shorts_session_log " +
                "WHERE startTime >= :since"
    )
    fun sumDurationSecSince(since: Long): Flow<Long>

    @Query(
        "SELECT IFNULL(SUM(durationSec), 0) FROM shorts_session_log " +
                "WHERE startTime >= :start AND startTime < :end"
    )
    fun sumDurationSecBetween(start: Long, end: Long): Flow<Long>

    @Query(
        "SELECT CAST(strftime('%H', datetime(startTime / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour, " +
                "IFNULL(SUM(durationSec), 0) AS totalSec FROM shorts_session_log " +
                "WHERE startTime >= :since GROUP BY hour"
    )
    fun durationByHourSince(since: Long): Flow<List<HourlySessionDuration>>
}

data class HourlySessionDuration(
    val hour: Int,
    val totalSec: Long,
)