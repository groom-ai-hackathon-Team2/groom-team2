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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groomteam2.dopamind.ui.theme.BrandPurple
import com.groomteam2.dopamind.ui.theme.BrandSuccess
import com.groomteam2.dopamind.ui.theme.BrandWarning

/**
 * 오버레이 카드.
 *
 * 디자인 의도:
 *  - 다른 앱 위에 떠야 하니 시각적으로 너무 강하지 않게(불투명 95%) 둥근 모서리.
 *  - 보상 포인트가 깎이는 것을 사용자가 즉시 보도록 큰 숫자로 표시 + 색상이 점점 빨강으로 변함.
 *  - 취약 시간대 배지로 '지금 참으면 3배' 동기 강화.
 */
@Composable
fun OverlayCoachView(
    message: String,
    currentReward: Int,
    challengeMinutes: Int,
    isVulnerableHour: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 보상 포인트 50→10 으로 떨어질 때 색이 점점 빨강으로.
    val ratio = ((currentReward - 10) / 40f).coerceIn(0f, 1f)
    val rewardColor by animateColorAsState(
        targetValue = lerp(Color(0xFFFF5252), BrandSuccess, ratio),
        label = "rewardColor"
    )
    val pulse by animateFloatAsState(
        targetValue = if (currentReward <= 20) 0.85f else 1f,
        label = "pulse"
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
                    Text("AI 코치", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (isVulnerableHour) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BrandWarning)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("취약 시간대 · 보상 3배!", color = Color(0xFF1A1B2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // 코치 멘트 — Gemini 가 생성한 한 줄.
            Text(
                text = message.ifBlank { "도파민 좀비 상태 감지!" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "지금 ${challengeMinutes}분 동안 안 켜면",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("나중에") }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                ) { Text("콜!", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** 두 색상 사이의 단순 선형 보간 — 보상 포인트 색상 전이용. */
private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
