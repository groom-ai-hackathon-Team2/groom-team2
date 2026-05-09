package com.groomteam2.dopamind.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.groomteam2.dopamind.di.ServiceLocator
import com.groomteam2.dopamind.ui.overlay.TimerCompleteView
import com.groomteam2.dopamind.ui.overlay.TimerFloatingBubble
import com.groomteam2.dopamind.ui.overlay.TimerIntroView
import com.groomteam2.dopamind.ui.theme.DopamindTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 사용자 정의 앱 타이머의 모든 오버레이를 책임지는 포그라운드 서비스.
 *
 * 5가지 액션:
 *  - ACTION_SHOW_INTRO   : 인스타/유튜브 진입 시 큰 안내 팝업
 *  - ACTION_START_TIMER  : [시작하기] 누른 직후 — 인트로 제거 + 플로팅 버블 부착 + 카운트다운 시작
 *  - ACTION_FINISH       : 카운트다운 만료 — 버블 제거 + 만료 팝업 + 포인트 적립
 *  - ACTION_EXTEND       : 만료 팝업의 [5분 연장] — 만료 팝업 제거 + 카운트다운 5분 추가
 *  - ACTION_DISMISS      : 모든 뷰 정리 + DataStore 클리어 + stopSelf
 *
 * 여러 뷰를 동시에 띄울 수 있도록 view 슬롯을 분리해서 관리한다.
 */
class TimerService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    // Compose ViewTree 가 요구하는 owner들 — OverlayService 와 동일 패턴
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private var introView: View? = null
    private var bubbleView: View? = null
    private var completeView: View? = null

    private val bubbleState = MutableStateFlow(BubbleUiState())
    /** 인트로 [시작하기] 누른 시점에 Compose 가 넘겨준 보상값을 보관해 startTimer 가 락인. */
    private var pendingLockedReward: Int = com.groomteam2.dopamind.data.prefs.UserPrefs.REWARD_INTRO_MAX
    private var countdownJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildSilentNotification())

        when (intent?.action) {
            ACTION_SHOW_INTRO -> showIntro()
            ACTION_START_TIMER -> startTimer()
            ACTION_FINISH -> finishTimer()
            ACTION_EXTEND -> extendTimer()
            ACTION_EXIT_APP -> exitApp()
            ACTION_DISMISS -> dismissAll()
            else -> showIntro()
        }
        return START_NOT_STICKY
    }

    // ── 뷰 표시 분기 ──────────────────────────────────────────────

    private fun showIntro() {
        if (introView != null) return
        lifecycleScope.launch {
            val minutes = ServiceLocator.userPrefs.goalTimerMinutes.firstOrNull()
                ?: com.groomteam2.dopamind.data.prefs.UserPrefs.DEFAULT_GOAL_MIN
            pendingLockedReward = com.groomteam2.dopamind.data.prefs.UserPrefs.REWARD_INTRO_MAX
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val vulnerableNow = ServiceLocator.vulnerableTimeLearner.isVulnerableNow(hour)
            val view = createComposeView {
                TimerIntroView(
                    minutes = minutes,
                    rewardMax = com.groomteam2.dopamind.data.prefs.UserPrefs.REWARD_INTRO_MAX,
                    rewardMin = com.groomteam2.dopamind.data.prefs.UserPrefs.REWARD_INTRO_MIN,
                    isVulnerableHour = vulnerableNow,
                    onStart = { lockedReward ->
                        // Compose 가 카운트다운을 자체 관리하므로 그 시점 값을 그대로 받아 startTimer 로 전달.
                        pendingLockedReward = lockedReward
                        start(applicationContext, ACTION_START_TIMER)
                    },
                    onCancel = { start(applicationContext, ACTION_DISMISS) },
                    onAutoExpire = { start(applicationContext, ACTION_DISMISS) },
                )
            }
            introView = view
            addCenteredCard(view)
        }
    }

    private fun startTimer() {
        // pendingLockedReward 는 Compose 의 onStart(lockedReward) 콜백에서 채워둠.
        var lockedReward = pendingLockedReward.coerceAtLeast(
            com.groomteam2.dopamind.data.prefs.UserPrefs.REWARD_INTRO_MIN
        )
        // 취약 시간대 진입 시 즉시 보상 3배 — 적응형 학습이 사용자에게 체감되도록.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (ServiceLocator.vulnerableTimeLearner.isVulnerableNow(hour)) {
            lockedReward *= 3
        }
        val finalReward = lockedReward
        removeView(introView); introView = null

        lifecycleScope.launch {
            val prefs = ServiceLocator.userPrefs
            val minutes = prefs.goalTimerMinutes.firstOrNull() ?: com.groomteam2.dopamind.data.prefs.UserPrefs.DEFAULT_GOAL_MIN
            val endAt = System.currentTimeMillis() + minutes * 60_000L
            prefs.setActiveTimerEndAt(endAt)
            prefs.setActiveTimerReward(finalReward)
            attachBubble()
            launchCountdown(endAt)
        }
    }

    private fun extendTimer() {
        removeView(completeView); completeView = null

        lifecycleScope.launch {
            val prefs = ServiceLocator.userPrefs
            val newEnd = System.currentTimeMillis() + EXTEND_MINUTES * 60_000L
            prefs.setActiveTimerEndAt(newEnd)
            if (bubbleView == null) attachBubble()
            launchCountdown(newEnd)
        }
    }

    private fun finishTimer() {
        countdownJob?.cancel()
        removeView(bubbleView); bubbleView = null

        lifecycleScope.launch {
            val prefs = ServiceLocator.userPrefs
            val reward = prefs.activeTimerReward.firstOrNull()
                ?: com.groomteam2.dopamind.data.prefs.UserPrefs.REWARD_INTRO_MAX
            val minutes = prefs.goalTimerMinutes.firstOrNull()
                ?: com.groomteam2.dopamind.data.prefs.UserPrefs.DEFAULT_GOAL_MIN

            ServiceLocator.pointRepository.add(
                delta = reward,
                reason = "timer_complete",
                challengeId = null,
            )
            // 시간대별 사용량 학습 — '이 시간에 N분 머물렀다' 신호를 EMA 에 누적.
            ServiceLocator.vulnerableTimeLearner.recordUsage(
                hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                minutes = minutes,
            )
            prefs.setActiveTimerEndAt(0L)

            val view = createComposeView {
                TimerCompleteView(
                    rewardPoints = reward,
                    onDismiss = { start(applicationContext, ACTION_DISMISS) },
                    onExtend = { start(applicationContext, ACTION_EXTEND) },
                    onExit = { start(applicationContext, ACTION_EXIT_APP) },
                )
            }
            completeView = view
            addCenteredCard(view)
        }
    }

    /**
     * "앱 닫기" 액션.
     * 보던 숏폼 앱을 백그라운드로 보내고(=홈 런처로 이동) 타이머 정리.
     *
     * Android 10+ 의 백그라운드 액티비티 시작 제한이 있지만,
     * TimerService 는 startForeground 로 떠있는 포그라운드 서비스라 홈 런처는 정상 시작됨.
     */
    private fun exitApp() {
        countdownJob?.cancel()
        removeView(introView); introView = null
        removeView(bubbleView); bubbleView = null
        removeView(completeView); completeView = null
        lifecycleScope.launch {
            ServiceLocator.userPrefs.setActiveTimerEndAt(0L)
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(homeIntent)
            } catch (e: Exception) {
                // 런처 부재 등 예외는 무시 — 어차피 stopSelf 로 정리.
            }
            stopSelf()
        }
    }

    private fun dismissAll() {
        countdownJob?.cancel()
        removeView(introView); introView = null
        removeView(bubbleView); bubbleView = null
        removeView(completeView); completeView = null
        lifecycleScope.launch {
            ServiceLocator.userPrefs.setActiveTimerEndAt(0L)
            stopSelf()
        }
    }

    // ── 카운트다운 ────────────────────────────────────────────────

    private fun launchCountdown(endAt: Long) {
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            while (isActive) {
                val remain = endAt - System.currentTimeMillis()
                if (remain <= 0) {
                    start(applicationContext, ACTION_FINISH)
                    break
                }
                bubbleState.value = bubbleState.value.copy(remainText = formatMmSs(remain))
                delay(1_000)
            }
        }
    }

    // ── 뷰 생성/부착 ──────────────────────────────────────────────

    private fun createComposeView(content: @Composable () -> Unit): View {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TimerService)
            setViewTreeSavedStateRegistryOwner(this@TimerService)
            setViewTreeViewModelStoreOwner(this@TimerService)
            setContent {
                DopamindTheme(darkTheme = true) {
                    content()
                }
            }
        }
    }

    /** 인트로/만료 카드(중앙 상단) 부착. */
    private fun addCenteredCard(view: View) {
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
            y = 120
        }
        windowManager().addView(view, lp)
    }

    /** 좌측 플로팅 버블 부착. 드래그 OnTouchListener 포함. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubble() {
        if (bubbleView != null) return
        val expanded = mutableStateOf(false)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TimerService)
            setViewTreeSavedStateRegistryOwner(this@TimerService)
            setViewTreeViewModelStoreOwner(this@TimerService)
            setContent {
                DopamindTheme(darkTheme = true) {
                    BubbleContent(
                        stateFlow = bubbleState,
                        expanded = expanded.value,
                        onToggle = { expanded.value = !expanded.value },
                        onOpenApp = {
                            val i = Intent(this@TimerService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(i)
                            expanded.value = false
                        },
                        onStop = { start(applicationContext, ACTION_DISMISS) },
                    )
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        // 드래그 처리: ACTION_DOWN 위치 기억 → ACTION_MOVE 마다 lp 갱신.
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x; initialY = lp.y
                    touchX = event.rawX; touchY = event.rawY
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) moved = true
                    if (moved) {
                        lp.x = (initialX + dx).toInt()
                        lp.y = (initialY + dy).toInt()
                        runCatching { windowManager().updateViewLayout(v, lp) }
                    }
                    moved
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 드래그 안 했으면 클릭으로 넘김(Compose clickable 가 받음)
                    moved
                }
                else -> false
            }
        }

        bubbleView = view
        windowManager().addView(view, lp)
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private fun windowManager(): WindowManager = getSystemService(WINDOW_SERVICE) as WindowManager

    private fun removeView(view: View?) {
        view ?: return
        runCatching { windowManager().removeView(view) }
    }

    private fun formatMmSs(remainMs: Long): String {
        val total = (remainMs / 1000).coerceAtLeast(0)
        val mm = total / 60
        val ss = total % 60
        return "%02d:%02d".format(mm, ss)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        removeView(introView); introView = null
        removeView(bubbleView); bubbleView = null
        removeView(completeView); completeView = null
        _viewModelStore.clear()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildSilentNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, DopamindApp.CHANNEL_TIMER)
            .setContentTitle(getString(R.string.timer_fg_title))
            .setContentText(getString(R.string.timer_fg_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 4343
        private const val EXTEND_MINUTES = 5
        private const val TOUCH_SLOP = 12

        const val ACTION_SHOW_INTRO = "com.groomteam2.dopamind.timer.SHOW_INTRO"
        const val ACTION_START_TIMER = "com.groomteam2.dopamind.timer.START_TIMER"
        const val ACTION_FINISH = "com.groomteam2.dopamind.timer.FINISH"
        const val ACTION_EXTEND = "com.groomteam2.dopamind.timer.EXTEND"
        const val ACTION_EXIT_APP = "com.groomteam2.dopamind.timer.EXIT_APP"
        const val ACTION_DISMISS = "com.groomteam2.dopamind.timer.DISMISS"

        fun start(context: Context, action: String) {
            val i = Intent(context, TimerService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}

data class BubbleUiState(val remainText: String = "00:00")

@Composable
private fun BubbleContent(
    stateFlow: StateFlow<BubbleUiState>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
) {
    val s by stateFlow.collectAsState()
    TimerFloatingBubble(
        remainText = s.remainText,
        expanded = expanded,
        onToggle = onToggle,
        onOpenApp = onOpenApp,
        onStop = onStop,
    )
}
