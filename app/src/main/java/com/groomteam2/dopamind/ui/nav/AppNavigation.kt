package com.groomteam2.dopamind.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.groomteam2.dopamind.di.ServiceLocator
import com.groomteam2.dopamind.ui.home.HomeScreen
import com.groomteam2.dopamind.ui.onboarding.OnboardingScreen
import com.groomteam2.dopamind.ui.permission.PermissionScreen
import com.groomteam2.dopamind.ui.ranking.RankingScreen

/**
 * 앱 전체 네비게이션 그래프.
 *
 * 플로우:
 *  1) startDestination 은 온보딩 완료 여부에 따라 분기
 *     - 미완료: onboarding
 *     - 완료:   home (권한이 비어있으면 홈에서 권한 화면 띄움)
 *  2) 각 화면이 자체 viewModel 에서 ServiceLocator 의 Repository 를 직접 사용 (수동 DI).
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val RANKING = "ranking"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // 온보딩 완료 플래그를 DataStore 에서 읽어 시작 화면 결정.
    val onboardingDone by ServiceLocator.userPrefs.onboardingDone
        .collectAsState(initial = false)

    val start = if (onboardingDone) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    // 온보딩이 끝나면 권한 화면으로 이동. 백스택은 비움.
                    navController.navigate(Routes.PERMISSION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PERMISSION) {
            PermissionScreen(
                onAllGranted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenRanking = { navController.navigate(Routes.RANKING) },
                onOpenPermission = { navController.navigate(Routes.PERMISSION) },
            )
        }
        composable(Routes.RANKING) {
            RankingScreen(onBack = { navController.popBackStack() })
        }
    }
}
