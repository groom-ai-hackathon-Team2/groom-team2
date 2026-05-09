package com.groomteam2.dopamind.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groomteam2.dopamind.data.prefs.PlanTier
import com.groomteam2.dopamind.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 리포트 탭 상태/액션.
 *
 *  - 캐시된 마지막 리포트(UserPrefs)를 읽어 즉시 표시.
 *  - generate() 호출 시 ReportEngine 으로 새 텍스트 생성 → 캐시에 덮어쓰기.
 *  - 플랜 토글은 데모/시연용 (실제 결제 연동은 본 MVP 범위 밖).
 */
class ReportViewModel : ViewModel() {

    private val prefs = ServiceLocator.userPrefs
    private val engine = ServiceLocator.reportEngine

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    val state: StateFlow<ReportUiState> = combine(
        prefs.planTier,
        prefs.lastReportText,
        prefs.lastReportPlan,
        prefs.lastReportAt,
        _generating,
    ) { plan, text, savedPlanName, at, busy ->
        ReportUiState(
            plan = plan,
            reportText = text,
            generatedAtMs = at,
            generatedWithPlan = runCatching { PlanTier.valueOf(savedPlanName) }.getOrNull(),
            isGenerating = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    fun generate() {
        if (_generating.value) return
        viewModelScope.launch {
            _generating.value = true
            try {
                val plan = state.value.plan
                val text = engine.generate(plan)
                prefs.saveReport(text = text, plan = plan, atMs = System.currentTimeMillis())
            } finally {
                _generating.value = false
            }
        }
    }

    fun togglePlan() {
        viewModelScope.launch {
            val next = if (state.value.plan == PlanTier.PRO) PlanTier.STANDARD else PlanTier.PRO
            prefs.setPlanTier(next)
        }
    }
}

data class ReportUiState(
    val plan: PlanTier = PlanTier.STANDARD,
    val reportText: String = "",
    val generatedAtMs: Long = 0L,
    val generatedWithPlan: PlanTier? = null,
    val isGenerating: Boolean = false,
) {
    val hasReport: Boolean get() = reportText.isNotBlank()
}
