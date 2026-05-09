package com.groomteam2.dopamind.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.groomteam2.dopamind.DopamindApp
import com.groomteam2.dopamind.MainActivity
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.ai.CoachAdvice
import com.groomteam2.dopamind.analyzer.ZombieDetection
import com.groomteam2.dopamind.di.ServiceLocator
import com.groomteam2.dopamind.ui.overlay.OverlayCoachView
import com.groomteam2.dopamind.ui.theme.DopamindTheme
import com.groomteam2.dopamind.work.ChallengeWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 인스타/유튜브 위에 코치 팝업을 띄우는 포그라운드 서비스.
 *
 * 책임:
 *  1) startForeground 로 떠서 시스템 종료를 회피
 *  2) WindowManager 에 ComposeView 를 TYPE_APPLICATION_OVERLAY 로 attach
 *  3) Compose 가 LifecycleOwner / SavedStateRegistry / ViewModelStore 가 필요한데,
 *     일반 Service 에는 없으므로 직접 구현 (LifecycleService 를 상속해 1차 해결).
 *  4) 사용자 응답:
 *      - 수락 → 챌린지 생성 + ChallengeWorker enqueue + 오버레이 종료
 *      - 무시 → 5초마다 보상 -10 (UI 가 보여줌). 일정 시간 뒤 자동 종료
 *      - 외부 닫기 버튼 → 보상 0 으로 만들고 종료
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    // SavedStateRegistry / ViewModelStore — Compose UI 가 ViewTree 에서 찾기 때문에 필요.
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private var overlayView: View? = null
    private val state = MutableStateFlow(OverlayState())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // 권한이 없으면 띄울 수 없음 — 토스트로 안내하고 종료.
        if (!canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildSilentNotification())

        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE_PACKAGE) ?: "com.instagram.android"
        showOverlay(sourcePackage)
        return START_NOT_STICKY
    }

    private fun showOverlay(sourcePackage: String) {
        if (overlayView != null) return

        val detection = ServiceLocator.patternAnalyzer.zombieStateFlow.value
            ?: ZombieDetection(
                packageName = sourcePackage,
                avgIntervalMs = 700,
                stdDevMs = 100,
                eventCount = 6,
                detectedAt = System.currentTimeMillis(),
                hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                isVulnerableHour = false,
            )

        // Gemini 호출은 비동기. 우선 임시 멘트로 띄우고, 도착하면 갱신.
        state.value = OverlayState(
            advice = CoachAdvice("도파민 좀비 상태 감지! 휴식 콜?", 30, 50),
            currentReward = 50,
            isVulnerableHour = detection.isVulnerableHour,
            packageName = sourcePackage,
        )

        lifecycleScope.launch {
            val advice = ServiceLocator.coachingEngine.coach(detection)
            state.value = state.value.copy(advice = advice, currentReward = advice.rewardPoints)
            startIgnoreDecay()
        }

        overlayView = createOverlayView(state)
        addToWindow(overlayView!!)
    }

    /**
     * 사용자가 무시하고 계속 보면 5초마다 보상 -10. 최소 10 에서 멈추고,
     * 그 이후 30초가 더 지나면 자동 종료(시연을 깔끔하게).
     */
    private fun startIgnoreDecay() {
        lifecycleScope.launch {
            var idleSecs = 0
            while (true) {
                kotlinx.coroutines.delay(5_000)
                val cur = state.value
                val next = (cur.currentReward - 10).coerceAtLeast(10)
                state.value = cur.copy(currentReward = next)
                if (next == 10) idleSecs += 5
                if (idleSecs >= 30) {
                    stopSelf()
                    break
                }
            }
        }
    }

    private fun createOverlayView(stateFlow: StateFlow<OverlayState>): View {
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                DopamindTheme(darkTheme = true) {
                    OverlayContent(
                        stateFlow = stateFlow,
                        onAccept = ::handleAccept,
                        onDismiss = ::handleDismiss,
                    )
                }
            }
        }
        return composeView
    }

    private fun addToWindow(view: View) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = 80
        }

        getSystemService(WINDOW_SERVICE)
            .let { it as WindowManager }
            .addView(view, lp)
    }

    private fun handleAccept() {
        val cur = state.value
        val advice = cur.advice ?: return
        lifecycleScope.launch {
            val challengeId = ServiceLocator.challengeRepository.createPending(
                durationMinutes = advice.challengeMinutes,
                rewardPoints = cur.currentReward,
                packages = listOf(
                    "com.instagram.android",
                    "com.google.android.youtube",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                ),
            )
            ChallengeWorker.enqueue(applicationContext, challengeId, advice.challengeMinutes)
            Toast.makeText(
                applicationContext,
                getString(R.string.challenge_started, advice.challengeMinutes),
                Toast.LENGTH_LONG,
            ).show()
            stopSelf()
        }
    }

    private fun handleDismiss() {
        // "나중에" 누르면 단순 종료. 사용자에게 페널티는 주지 않음(자율적 선택 강조).
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { v ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        overlayView = null
        _viewModelStore.clear()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    /**
     * 포그라운드 서비스 알림. 시연 중 사용자에게 거슬리지 않도록 IMPORTANCE_MIN 채널 사용.
     */
    private fun buildSilentNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, DopamindApp.CHANNEL_OVERLAY_FG)
            .setContentTitle("도파민드 코치 활동 중")
            .setContentText("숏폼 사용을 모니터링하고 있어요")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 4242
        private const val EXTRA_SOURCE_PACKAGE = "extra_src_pkg"

        fun start(context: Context, sourcePackage: String) {
            val i = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun canDrawOverlays(context: Context): Boolean =
            Settings.canDrawOverlays(context)
    }
}

/** 오버레이 상태 한 묶음. Compose UI 가 collectAsState 로 관찰. */
data class OverlayState(
    val advice: CoachAdvice? = null,
    val currentReward: Int = 50,
    val isVulnerableHour: Boolean = false,
    val packageName: String = "",
)

@Composable
private fun OverlayContent(
    stateFlow: StateFlow<OverlayState>,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s by stateFlow.collectAsState()
    OverlayCoachView(
        message = s.advice?.message ?: "",
        currentReward = s.currentReward,
        challengeMinutes = s.advice?.challengeMinutes ?: 30,
        isVulnerableHour = s.isVulnerableHour,
        onAccept = onAccept,
        onDismiss = onDismiss,
    )
}
