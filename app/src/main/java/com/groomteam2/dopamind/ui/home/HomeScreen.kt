package com.groomteam2.dopamind.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
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
            Spacer(Modifier.height(16.dp))
            TimerSettingCard(
                currentMinutes = state.goalTimerMinutes,
                onSelect = vm::setGoalTimerMinutes,
            )
            Spacer(Modifier.height(24.dp))
            Text("시간대별 위험도", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            VulnerableBars(state.vulnerableScores, state.currentHour)
        }
    }
}

/**
 * 사용자가 인스타/유튜브에 진입할 때 사용할 목표 시간을 분 단위로 선택.
 * 칩 그룹: 1, 5, 15, 30, 60 분 — 선택 시 즉시 DataStore 에 저장.
 */
@Composable
private fun TimerSettingCard(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.timer_setting_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.timer_setting_caption),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(1, 5, 15, 30, 60).forEach { m ->
                    val selected = m == currentMinutes
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) BrandPurple else Color.White.copy(alpha = 0.08f))
                            .clickable { onSelect(m) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.timer_minutes_format, m),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
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
