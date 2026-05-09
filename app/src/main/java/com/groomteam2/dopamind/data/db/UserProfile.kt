package com.groomteam2.dopamind.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 사용자 프로필.
 *
 * 단일 레코드(id=0)만 사용하는 단순 모델 — MVP 단계라 멀티유저는 미지원.
 * 직업/하루 일정 텍스트는 AI 코칭 프롬프트에 그대로 주입되어 맞춤 멘트의 근거가 됨.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0,
    val job: String,
    val schedule: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = 0 LIMIT 1")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 0 LIMIT 1")
    suspend fun get(): UserProfile?
}
