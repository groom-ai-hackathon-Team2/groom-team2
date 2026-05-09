package com.groomteam2.dopamind.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import com.groomteam2.dopamind.service.TimerService
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.ui.theme.BrandPurple
import com.groomteam2.dopamind.ui.theme.BrandSuccess
import com.groomteam2.dopamind.ui.theme.BrandWarning

/**
 * 홈 화면.
 *  - 상단: 총 포인트 거대한 숫자
 *  - 중간: 오늘 받은 코칭/챌린지 성공 카드
 *  - 취약 시간대 배지(현재 시간이 그렇다면)
 *  - 24시간 위험도 막대 그래프 (간단한 시각화)
 *  - 우측 상단 트로피 → 랭킹, 자물쇠 → 권한 화면 재진입
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRanking: () -> Unit,
    onOpenPermission: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle(initialValue = HomeState())
    val shortsStats by vm.shortsStatsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 제목 옆에 재생 버튼 — 한 번 종료된 뒤에도 사용자가 다시 타이머를 시작할 수 있도록.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val running = state.activeTimerEndAt > System.currentTimeMillis()
                                if (running) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.home_timer_running),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    TimerService.start(context, TimerService.ACTION_SHOW_INTRO)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.home_start_timer),
                                tint = BrandPurple,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.home_title), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPermission) {
                        Icon(Icons.Default.Lock, contentDescription = "권한")
                    }
                    IconButton(onClick = onOpenRanking) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = "랭킹")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.isVulnerableNow) {
                VulnerableBanner()
                Spacer(Modifier.height(12.dp))
            }
            PointHeader(state.totalPoints)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.home_today_alerts),
                    value = state.successToday.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.home_today_resisted),
                    value = "+${state.earnedToday}",
                    modifier = Modifier.weight(1f),
                    accent = BrandSuccess,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("시간대별 위험도", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            VulnerableBars(state.vulnerableScores, state.currentHour)
            Spacer(Modifier.height(24.dp))
            // 시간대별 위험도 그래프 바로 아래에 숏폼 사용 통계 섹션.
            ShortsStatsSection(shortsStats)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PointHeader(total: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandPurple),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.home_points), color = Color.White.copy(alpha = 0.85f))
            Text(
                text = total.toString(),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
            )
            Text("절제력은 곧 자유 ✨", color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = BrandPurple,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = accent)
        }
    }
}

@Composable
private fun VulnerableBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandWarning)
            .padding(12.dp)
    ) {
        Text(
            stringResource(R.string.home_vulnerable_now),
            color = Color(0xFF1A1B2E),
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 0..23시 24개 막대로 위험도 표현. 현재 시간은 강조.
 * 막대 높이는 학습된 EMA 점수에 비례.
 */
@Composable
private fun VulnerableBars(scores: List<Float>, currentHour: Int) {
    val max = (scores.maxOrNull() ?: 0f).coerceAtLeast(0.001f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        scores.forEachIndexed { hour, score ->
            val ratio = (score / max).coerceIn(0f, 1f)
            val color = if (hour == currentHour) BrandWarning else BrandPurple
            Box(
                Modifier
                    .width(8.dp)
                    .height((6 + ratio * 70).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
