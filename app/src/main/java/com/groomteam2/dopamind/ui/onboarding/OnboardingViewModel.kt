package com.groomteam2.dopamind.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groomteam2.dopamind.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 온보딩 화면의 입력 상태와 저장 액션 보유.
 *
 * MVP라 직업/일정 자유 텍스트 두 개만 받고 검증도 비어있지 않으면 OK 정도.
 * 저장 시 UserRepository 가 DataStore 의 onboardingDone=true 도 같이 세팅.
 */
class OnboardingViewModel : ViewModel() {

    val job = MutableStateFlow("")
    val schedule = MutableStateFlow("")

    val canSubmit: Boolean
        get() = job.value.isNotBlank() && schedule.value.isNotBlank()

    fun submit(onDone: () -> Unit) {
        if (!canSubmit) return
        viewModelScope.launch {
            ServiceLocator.userRepository.saveProfile(job.value, schedule.value)
            onDone()
        }
    }
}
