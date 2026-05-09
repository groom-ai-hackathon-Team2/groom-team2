package com.groomteam2.dopamind.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 데이터베이스.
 *
 * v1: 기존 4개 엔티티
 * v2: ShortsViewLog, ShortsSessionLog 추가 (홈 화면 숏폼 사용 통계 섹션용).
 *
 * - exportSchema=false: MVP 라 마이그레이션 히스토리 관리 불필요
 * - 마이그레이션 실패 시 fallbackToDestructiveMigration 으로 안전망 유지
 */
@Database(
    entities = [
        UserProfile::class,
        ScrollSession::class,
        ChallengeRecord::class,
        PointLedger::class,
        ShortsViewLog::class,
        ShortsSessionLog::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun pointLedgerDao(): PointLedgerDao
    abstract fun shortsViewLogDao(): ShortsViewLogDao
    abstract fun shortsSessionLogDao(): ShortsSessionLogDao

    companion object {
        /**
         * v1 → v2: 숏폼 사용 통계 테이블 2개 추가.
         * 기존 사용자 데이터(포인트/챌린지/사용자/스크롤세션)는 그대로 보존.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shorts_view_log` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`packageName` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`dayKey` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shorts_view_log_timestamp` " +
                            "ON `shorts_view_log` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shorts_view_log_dayKey` " +
                            "ON `shorts_view_log` (`dayKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shorts_view_log_packageName` " +
                            "ON `shorts_view_log` (`packageName`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shorts_session_log` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`packageName` TEXT NOT NULL, " +
                            "`startTime` INTEGER NOT NULL, " +
                            "`endTime` INTEGER NOT NULL, " +
                            "`durationSec` INTEGER NOT NULL, " +
                            "`dayKey` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shorts_session_log_startTime` " +
                            "ON `shorts_session_log` (`startTime`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shorts_session_log_dayKey` " +
                            "ON `shorts_session_log` (`dayKey`)"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "dopamind.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}