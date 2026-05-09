package com.groomteam2.dopamind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.groomteam2.dopamind.ui.nav.AppNavigation
import com.groomteam2.dopamind.ui.theme.DopamindTheme

/**
 * 단일 액티비티 + Compose Navigation 구조.
 *
 * 실제 화면 분기는 AppNavigation 에서 다음 순서로 처리:
 *   온보딩(직업/일정 미입력 시) → 권한 안내 → 홈 → 랭킹
 *
 * 액티비티가 단순한 이유:
 *  - MVP 라 다중 액티비티가 필요하지 않음
 *  - Compose 가 화면 전환을 모두 처리
 *  - 권한 요청 결과/오버레이 인텐트도 ActivityResult API 로 Compose 에서 직접 처리
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DopamindTheme {
                AppNavigation()
            }
        }
    }
}
