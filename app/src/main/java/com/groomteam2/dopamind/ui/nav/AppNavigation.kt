package com.groomteam2.dopamind.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.di.ServiceLocator
import com.groomteam2.dopamind.ui.home.HomeScreen
import com.groomteam2.dopamind.ui.onboarding.OnboardingScreen
import com.groomteam2.dopamind.ui.permission.PermissionScreen
import com.groomteam2.dopamind.ui.ranking.RankingScreen
import com.groomteam2.dopamind.ui.report.ReportScreen

/**
 * 앱 전체 네비게이션 그래프.
 *
 * 플로우:
 *  1) startDestination 은 온보딩 완료 여부에 따라 분기
 *     - 미완료: onboarding
 *     - 완료:   home (권한이 비어있으면 홈에서 권한 화면 띄움)
 *  2) 메인 3개 탭(홈/리포트/랭킹)은 BottomNavigation 으로 전환.
 *     온보딩/권한 화면에서는 하단 탭 숨김.
 *  3) 각 화면이 자체 viewModel 에서 ServiceLocator 의 Repository 를 직접 사용 (수동 DI).
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val REPORT = "report"
    const val RANKING = "ranking"
}

/** 하단 탭 정의. 추가 탭이 늘어나면 이 리스트만 확장. */
private data class TabItem(val route: String, val icon: ImageVector, val labelRes: Int)

private val MainTabs = listOf(
    TabItem(Routes.HOME, Icons.Default.Home, R.string.tab_home),
    TabItem(Routes.REPORT, Icons.Default.Assessment, R.string.tab_report),
    TabItem(Routes.RANKING, Icons.Default.EmojiEvents, R.string.tab_ranking),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val onboardingDone by ServiceLocator.userPrefs.onboardingDone
        .collectAsState(initial = false)

    val start = if (onboardingDone) Routes.HOME else Routes.ONBOARDING

    val backEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route
    val showBottomBar = currentRoute in MainTabs.map { it.route }.toSet()

    Scaffold(
        bottomBar = {
            if (showBottomBar) BottomBar(currentRoute = currentRoute, navController = navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
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
                    onOpenRanking = { navigateTab(navController, Routes.RANKING) },
                    onOpenPermission = { navController.navigate(Routes.PERMISSION) },
                )
            }
            composable(Routes.REPORT) {
                ReportScreen()
            }
            composable(Routes.RANKING) {
                // 탭 진입에서는 뒤로가기 의미가 모호하므로 onBack 은 홈으로 이동.
                RankingScreen(onBack = { navigateTab(navController, Routes.HOME) })
            }
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, navController: NavController) {
    NavigationBar {
        MainTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { navigateTab(navController, tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

/**
 * 탭 전환은 백스택을 누적시키지 않도록 startDestination 으로 popUp + 단일 인스턴스 + 상태 복원.
 * (안드로이드 BottomNav 표준 패턴.)
 */
private fun navigateTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
