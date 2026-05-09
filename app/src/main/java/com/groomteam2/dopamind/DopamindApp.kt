package com.groomteam2.dopamind

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.groomteam2.dopamind.di.ServiceLocator

/**
 * 앱 진입점.
 *
 * 역할:
 *  1) 알림 채널 생성 (오버레이 포그라운드 / 챌린지 결과 알림 / 앱 타이머)
 *  2) 의존성 컨테이너(ServiceLocator) 초기화 — Hilt 미사용 MVP 라서 수동 DI
 *
 * 다른 컴포넌트(서비스, ViewModel)는 ServiceLocator 를 통해 Repository / API 인스턴스를 가져온다.
 */
class DopamindApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY_FG,
                getString(R.string.notif_channel_overlay),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "코치 오버레이 서비스 동작 표시 (조용함)"
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHALLENGE,
                getString(R.string.notif_channel_challenge),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "챌린지 진행 상태 / 성공·실패 알림"
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER,
                getString(R.string.notif_channel_timer),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "앱 타이머 동작 중 표시 (조용함)"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_OVERLAY_FG = "ch_overlay_fg"
        const val CHANNEL_CHALLENGE = "ch_challenge"
        const val CHANNEL_TIMER = "ch_timer"
    }
}