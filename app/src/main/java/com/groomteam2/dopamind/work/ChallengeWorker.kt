package com.groomteam2.dopamind.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.groomteam2.dopamind.DopamindApp
import kotlinx.coroutines.flow.firstOrNull
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.data.db.ChallengeRecord
import com.groomteam2.dopamind.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * 챌린지 시간 만료 시점에 결과를 정산하는 1회성 워커.
 *
 * - 약속 시간 후에 실행
 * - DB 에서 해당 챌린지를 가져와 STATUS_PENDING 이면 성공 처리 + 포인트 적립
 * - 이미 FAILED 면 무시 (AccessibilityService 가 먼저 실패 처리했을 수 있음)
 * - 결과를 알림으로 사용자에게 통지
 */
class ChallengeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val challengeId = inputData.getLong(KEY_CHALLENGE_ID, -1)
        if (challengeId < 0) return Result.failure()

        val repo = ServiceLocator.challengeRepository
        val recentList = repo.recent.firstOrNull().orEmpty()
        val record = recentList.firstOrNull { it.id == challengeId }
            ?: return Result.success()

        when (record.status) {
            ChallengeRecord.STATUS_PENDING -> {
                repo.markSuccess(record.id)
                ServiceLocator.pointRepository.add(
                    delta = record.rewardPoints,
                    reason = "challenge_success",
                    challengeId = record.id,
                )
                notify(
                    title = "🎉 챌린지 성공!",
                    body = applicationContext.getString(R.string.challenge_succeeded, record.rewardPoints),
                )
            }
            ChallengeRecord.STATUS_FAILED -> {
                notify(
                    title = "😭 챌린지 실패",
                    body = applicationContext.getString(R.string.challenge_failed),
                )
            }
            else -> {} // SUCCESS 면 이미 처리됨
        }
        return Result.success()
    }

    private fun notify(title: String, body: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, DopamindApp.CHANNEL_CHALLENGE)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }

    companion object {
        private const val KEY_CHALLENGE_ID = "challenge_id"

        fun enqueue(context: Context, challengeId: Long, durationMinutes: Int) {
            val req = OneTimeWorkRequestBuilder<ChallengeWorker>()
                .setInputData(Data.Builder().putLong(KEY_CHALLENGE_ID, challengeId).build())
                .setInitialDelay(durationMinutes.toLong(), TimeUnit.MINUTES)
                .addTag("challenge_$challengeId")
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

