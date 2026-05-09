package com.groomteam2.dopamind.ui.report

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.groomteam2.dopamind.data.prefs.PlanTier
import com.groomteam2.dopamind.ui.theme.BrandPurple
import com.groomteam2.dopamind.ui.theme.BrandSuccess
import com.groomteam2.dopamind.ui.theme.BrandWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 주간 리포트 화면.
 *
 *  - 비어 있을 때: 안내 + "리포트 생성하기" 버튼.
 *  - 생성 중: 버튼이 로딩으로 바뀌고 본문 자리에 진행 표시.
 *  - 결과 있을 때: 마크다운 ## 헤더 단위로 잘라 카드로 렌더링.
 *  - PRO 잠금 섹션: STANDARD 플랜이 PRO 섹션을 봐야 할 때 가림막을 덮음.
 *  - 상단 우측에 플랜 배지 — 탭하면 STANDARD ↔ PRO 토글 (시연용).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.report_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    PlanBadge(plan = state.plan, onClick = { vm.togglePlan() })
                    Spacer(Modifier.padding(end = 8.dp))
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            GenerateButton(
                isGenerating = state.isGenerating,
                hasReport = state.hasReport,
                onClick = { vm.generate() },
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.isGenerating && !state.hasReport -> EmptyLoading()
                !state.hasReport -> EmptyHint()
                else -> ReportBody(state = state)
            }
        }
    }
}

// ── 상단 플랜 배지 ────────────────────────────────────────────────
@Composable
private fun PlanBadge(plan: PlanTier, onClick: () -> Unit) {
    val (label, bg, fg) = when (plan) {
        PlanTier.PRO -> Triple(stringResource(R.string.report_plan_pro), BrandPurple, Color.White)
        PlanTier.STANDARD -> Triple(
            stringResource(R.string.report_plan_standard),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurface,
        )
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 생성 버튼 ────────────────────────────────────────────────────
@Composable
private fun GenerateButton(isGenerating: Boolean, hasReport: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isGenerating,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.padding(end = 10.dp))
            Text(stringResource(R.string.report_generating), color = Color.White)
        } else {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
            Spacer(Modifier.padding(end = 8.dp))
            Text(
                if (hasReport) stringResource(R.string.report_regenerate)
                else stringResource(R.string.report_generate),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── 빈 상태 ─────────────────────────────────────────────────────
@Composable
private fun EmptyHint() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.report_empty_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.report_empty_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EmptyLoading() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandPurple)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.report_generating_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

// ── 결과 본문 ────────────────────────────────────────────────────
@Composable
private fun ReportBody(state: ReportUiState) {
    Column {
        // 생성 시각/플랜 메타.
        MetaLine(state)
        Spacer(Modifier.height(12.dp))

        // ## 헤더 단위로 섹션 분리. 첫 부분에 헤더 없는 잡문이 있으면 무시.
        val sections = parseSections(state.reportText)
        val proLockedSections = setOf(SECTION_DEEP, SECTION_PLAN)
        val isStandard = state.plan == PlanTier.STANDARD

        sections.forEach { sec ->
            val locked = isStandard && sec.title in proLockedSections
            SectionCard(section = sec, locked = locked)
            Spacer(Modifier.height(12.dp))
        }

        // STANDARD 플랜이 보고서를 PRO 로 만들기 전에 잠금 안내 카드를 끝에 한 번 더.
        if (isStandard) {
            UpgradeHintCard()
        }
    }
}

@Composable
private fun MetaLine(state: ReportUiState) {
    val planLabel = when (state.generatedWithPlan) {
        PlanTier.PRO -> stringResource(R.string.report_meta_pro)
        else -> stringResource(R.string.report_meta_standard)
    }
    val time = remember(state.generatedAtMs) {
        if (state.generatedAtMs <= 0L) ""
        else SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(state.generatedAtMs))
    }
    Text(
        text = stringResource(R.string.report_meta_line, time, planLabel),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )
}

@Composable
private fun SectionCard(section: ReportSection, locked: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        section.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (locked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrandWarning,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (locked) stringResource(R.string.report_section_locked) else section.body,
                    fontSize = 14.sp,
                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun UpgradeHintCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandPurple),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                Spacer(Modifier.padding(end = 8.dp))
                Text(
                    stringResource(R.string.report_upgrade_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.report_upgrade_desc),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.report_upgrade_hint_toggle),
                color = BrandSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── 마크다운 파싱 (## 헤더 단위) ─────────────────────────────────
private data class ReportSection(val title: String, val body: String)

private const val SECTION_SUMMARY = "이번 주 요약"
private const val SECTION_STATS = "핵심 통계"
private const val SECTION_DEEP = "시간대별 심층 분석"
private const val SECTION_PLAN = "맞춤 코칭 플랜"

private fun parseSections(raw: String): List<ReportSection> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lineSequence().toList()
    val out = mutableListOf<ReportSection>()
    var currentTitle: String? = null
    val currentBody = StringBuilder()
    fun flush() {
        val t = currentTitle ?: return
        out += ReportSection(t, currentBody.toString().trim())
        currentBody.clear()
    }
    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("## ")) {
            flush()
            currentTitle = trimmed.removePrefix("## ").trim()
        } else if (currentTitle != null) {
            currentBody.appendLine(line)
        }
    }
    flush()
    return out
}
