package com.groomteam2.dopamind.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.data.repo.VulnerableHourStat
import com.groomteam2.dopamind.ui.theme.BrandPink
import com.groomteam2.dopamind.ui.theme.BrandPurple
import com.groomteam2.dopamind.ui.theme.BrandSuccess
import com.groomteam2.dopamind.ui.theme.BrandWarning
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 홈 화면의 "숏폼 사용 통계" 섹션.
 *
 * 4개 카드를 2행으로 배치(2x2):
 *  1) 시청한 숏츠 개수 (오늘/누적, 패키지별 분리)
 *  2) 시청 시간 (오늘/이번 주, 어제 대비 증감)
 *  3) 평균 스크롤 간격 (좀비 임계치 800ms 시각화)
 *  4) 주요 취약 시간 Top 3
 *
 * 데이터가 전혀 없으면 placeholder 한 장.
 */
@Composable
fun ShortsStatsSection(state: ShortsStatsUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.shorts_stats_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        if (state.isEmpty) {
            EmptyStatsCard()
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ViewCountCard(state = state, modifier = Modifier.weight(1f))
            WatchTimeCard(state = state, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AvgIntervalCard(state = state, modifier = Modifier.weight(1f))
            TopHoursCard(state = state, modifier = Modifier.weight(1f))
        }
    }
}

// ── 카드 1: 시청 개수 ────────────────────────────────────────────
@Composable
private fun ViewCountCard(state: ShortsStatsUiState, modifier: Modifier = Modifier) {
    StatsCard(modifier = modifier) {
        CardLabel(stringResource(R.string.shorts_stats_view_count_title))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                R.string.shorts_stats_view_count_value,
                state.todayViewCount,
                state.totalViewCount,
            ),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        // 패키지별 분리: 가로 막대 비례 (인스타/유튜브/틱톡 색상 구분).
        PackageBreakdownBar(state.todayCountByPackage)
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.shorts_stats_view_count_breakdown,
                state.todayCountByPackage[PKG_INSTAGRAM] ?: 0,
                state.todayCountByPackage[PKG_YOUTUBE] ?: 0,
                tiktokCount(state.todayCountByPackage),
            ),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackageBreakdownBar(byPkg: Map<String, Int>) {
    val ig = byPkg[PKG_INSTAGRAM] ?: 0
    val yt = byPkg[PKG_YOUTUBE] ?: 0
    val tt = tiktokCount(byPkg)
    val total = (ig + yt + tt).coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (ig > 0) Box(Modifier.weight(ig.toFloat() / total).fillMaxWidth().background(BrandPink))
        if (yt > 0) Box(Modifier.weight(yt.toFloat() / total).fillMaxWidth().background(BrandPurple))
        if (tt > 0) Box(Modifier.weight(tt.toFloat() / total).fillMaxWidth().background(BrandSuccess))
    }
}

// ── 카드 2: 시청 시간 ────────────────────────────────────────────
@Composable
private fun WatchTimeCard(state: ShortsStatsUiState, modifier: Modifier = Modifier) {
    StatsCard(modifier = modifier) {
        CardLabel(stringResource(R.string.shorts_stats_watch_time_title))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.shorts_stats_watch_time_today, formatMinutes(state.todayWatchSec)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.shorts_stats_watch_time_week, formatHoursMinutes(state.weekWatchSec)),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        // 어제 대비 증감.
        DeltaText(today = state.todayWatchSec, yesterday = state.yesterdayWatchSec)
    }
}

@Composable
private fun DeltaText(today: Long, yesterday: Long) {
    when {
        yesterday <= 0L && today <= 0L -> Unit
        yesterday <= 0L -> Text(
            stringResource(R.string.shorts_stats_watch_time_delta_up, 100),
            fontSize = 11.sp,
            color = BrandWarning,
        )
        else -> {
            val pct = ((today - yesterday).toDouble() / yesterday * 100.0).roundToInt()
            when {
                pct > 0 -> Text(
                    stringResource(R.string.shorts_stats_watch_time_delta_up, pct),
                    fontSize = 11.sp,
                    color = BrandWarning,
                )
                pct < 0 -> Text(
                    stringResource(R.string.shorts_stats_watch_time_delta_down, abs(pct)),
                    fontSize = 11.sp,
                    color = BrandSuccess,
                )
                else -> Text(
                    stringResource(R.string.shorts_stats_watch_time_delta_zero),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── 카드 3: 평균 스크롤 간격 ────────────────────────────────────
@Composable
private fun AvgIntervalCard(state: ShortsStatsUiState, modifier: Modifier = Modifier) {
    StatsCard(modifier = modifier) {
        CardLabel(stringResource(R.string.shorts_stats_avg_interval_title))
        Spacer(Modifier.height(6.dp))
        if (state.todayAvgScrollMs <= 0L) {
            Text(
                stringResource(R.string.shorts_stats_avg_interval_none),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val seconds = state.todayAvgScrollMs / 1000.0
            val isZombie = state.todayAvgScrollMs < ZOMBIE_THRESHOLD_MS
            Text(
                text = stringResource(R.string.shorts_stats_avg_interval_value, seconds),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (isZombie) BrandWarning else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            IntervalGauge(avgMs = state.todayAvgScrollMs)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.shorts_stats_avg_interval_caption),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 0..GAUGE_MAX_MS 구간을 가로 막대로 표현, 800ms 위치에 빨간 임계치 라인 표시.
 */
@Composable
private fun IntervalGauge(avgMs: Long) {
    val ratio = (avgMs.toFloat() / GAUGE_MAX_MS).coerceIn(0f, 1f)
    val thresholdRatio = (ZOMBIE_THRESHOLD_MS.toFloat() / GAUGE_MAX_MS).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // 평균값 막대.
        Box(
            Modifier
                .fillMaxWidth(ratio)
                .height(10.dp)
                .background(if (avgMs < ZOMBIE_THRESHOLD_MS) BrandWarning else BrandPurple),
        )
        // 임계치(800ms) 위치를 빨간 세로선으로 표시.
        Row(Modifier.fillMaxWidth().height(10.dp)) {
            Spacer(Modifier.fillMaxWidth(thresholdRatio))
            Box(
                Modifier
                    .width(2.dp)
                    .height(10.dp)
                    .background(Color(0xFFFF5252)),
            )
        }
    }
}

// ── 카드 4: 취약 시간 Top 3 ──────────────────────────────────────
@Composable
private fun TopHoursCard(state: ShortsStatsUiState, modifier: Modifier = Modifier) {
    StatsCard(modifier = modifier) {
        CardLabel(stringResource(R.string.shorts_stats_top_hours_title))
        Spacer(Modifier.height(6.dp))
        if (state.topVulnerableHours.isEmpty()) {
            Text(
                stringResource(R.string.shorts_stats_avg_interval_none),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.topVulnerableHours.forEachIndexed { idx, stat ->
                TopHourRow(rank = idx, stat = stat)
                if (idx != state.topVulnerableHours.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun TopHourRow(rank: Int, stat: VulnerableHourStat) {
    val medal = when (rank) {
        0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "·"
    }
    Column {
        Text(
            stringResource(
                R.string.shorts_stats_top_hours_row,
                medal,
                stat.hour,
                (stat.hour + 1) % 24,
                stat.count,
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (stat.avgWatchMin > 0) {
            Text(
                stringResource(R.string.shorts_stats_top_hours_avg_min, stat.avgWatchMin),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 공통 컴포넌트 ────────────────────────────────────────────────
@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.heightIn(min = 140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun EmptyStatsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.shorts_stats_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 헬퍼 ─────────────────────────────────────────────────────────
private fun tiktokCount(byPkg: Map<String, Int>): Int =
    (byPkg[PKG_TIKTOK_MUSICAL] ?: 0) + (byPkg[PKG_TIKTOK_TRILL] ?: 0)

@Composable
private fun formatMinutes(totalSec: Long): String {
    val mins = (totalSec / 60L).toInt()
    return stringResource(R.string.shorts_stats_minutes, mins)
}

@Composable
private fun formatHoursMinutes(totalSec: Long): String {
    val totalMin = (totalSec / 60L).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) stringResource(R.string.shorts_stats_hours_minutes, h, m)
    else stringResource(R.string.shorts_stats_minutes, m)
}

private const val PKG_INSTAGRAM = "com.instagram.android"
private const val PKG_YOUTUBE = "com.google.android.youtube"
private const val PKG_TIKTOK_MUSICAL = "com.zhiliaoapp.musically"
private const val PKG_TIKTOK_TRILL = "com.ss.android.ugc.trill"

private const val ZOMBIE_THRESHOLD_MS = 800L
private const val GAUGE_MAX_MS = 3_000L