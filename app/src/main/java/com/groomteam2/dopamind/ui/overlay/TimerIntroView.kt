package com.groomteam2.dopamind.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.ui.theme.BrandPurple
import com.groomteam2.dopamind.ui.theme.BrandSuccess
import com.groomteam2.dopamind.ui.theme.BrandWarning
import kotlinx.coroutines.delay

/**
 * 인스타/유튜브 진입 시 처음 한 번 뜨는 큰 안내 팝업.
 * "지금부터 앱 타이머가 시작됩니다" + 기대 보상 + [시작하기] / [취소].
 *
 * 보상 깎기 카운트다운(rewardMax→rewardMin)을 Compose 내부 LaunchedEffect 로 처리.
 * Compose 가 알아서 코루틴 lifecycle 을 관리하므로 외부 Service 코루틴 타이밍 이슈 없음.
 *
 * @param onStart 시작하기 클릭 시 현재 시점의 보상값을 그대로 호출자에게 전달.
 * @param onAutoExpire 최저값 도달 후 30초 더 지나면 호출 → Service 가 인트로 닫고 dismiss.
 */
@Composable
fun TimerIntroView(
    minutes: Int,
    rewardMax: Int,
    rewardMin: Int,
    isVulnerableHour: Boolean,
    onStart: (lockedReward: Int) -> Unit,
    onCancel: () -> Unit,
    onAutoExpire: () -> Unit,
) {
    var currentReward by remember { mutableIntStateOf(rewardMax) }
    var idleAtMinSecs by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val next = (currentReward - 5).coerceAtLeast(rewardMin)
            currentReward = next
            if (next == rewardMin) idleAtMinSecs += 5
            if (idleAtMinSecs >= 30) {
                onAutoExpire()
                break
            }
        }
    }

    val span = (rewardMax - rewardMin).coerceAtLeast(1)
    val ratio = ((currentReward - rewardMin).toFloat() / span).coerceIn(0f, 1f)
    val rewardColor by animateColorAsState(
        targetValue = lerp(Color(0xFFFF5252), BrandSuccess, ratio),
        label = "introRewardColor"
    )
    val pulse by animateFloatAsState(
        targetValue = if (currentReward <= rewardMin + 5) 0.85f else 1f,
        label = "introPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(0.97f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BrandPurple)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("⏱ 도파민드 타이머", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (isVulnerableHour) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BrandWarning)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "취약시간 · 보상 3배!",
                            color = Color(0xFF1A1B2E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.timer_intro_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.timer_intro_minutes, minutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // 기대 보상 — 머뭇거리면 깎임. 색상이 점점 빨강으로.
            Text(
                text = "지금 시작하면 받을 포인트",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "+$currentReward",
                    color = rewardColor,
                    fontSize = (40 * pulse).sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "포인트",
                    color = rewardColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.timer_intro_cancel)) }
                Button(
                    onClick = { onStart(currentReward) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                ) {
                    Text(
                        stringResource(R.string.timer_intro_start),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 두 색상 사이의 단순 선형 보간. OverlayCoachView 와 동일 헬퍼. */
private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
