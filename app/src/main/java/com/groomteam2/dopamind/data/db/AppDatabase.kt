package com.groomteam2.dopamind.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 데이터베이스.
 *
 * 4개의 엔티티만 다루는 단순 구조.
 * - exportSchema=false: MVP 라 마이그레이션 히스토리 관리 불필요
 * - fallbackToDestructiveMigration: 스키마 바뀌면 그냥 재생성 (사용자 데이터는 잃을 수 있지만 시연용 OK)
 */
@Database(
    entities = [
        UserProfile::class,
        ScrollSession::class,
        ChallengeRecord::class,
        PointLedger::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun pointLedgerDao(): PointLedgerDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "dopamind.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
