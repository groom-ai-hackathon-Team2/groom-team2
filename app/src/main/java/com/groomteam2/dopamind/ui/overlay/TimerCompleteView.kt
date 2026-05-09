package com.groomteam2.dopamind.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * 타이머 만료 시 뜨는 큰 팝업.
 * "수고했어요!" + 포인트 안내 + [5분 연장] / [앱 닫기] / [종료].
 *
 * - 5분 연장: 카운트다운 5분 추가
 * - 앱 닫기: 보던 숏폼 앱을 백그라운드로 보내고(=홈 런처로 이동) 타이머 종료
 * - 종료: 오버레이만 닫고 타이머 정리 (기존 동작)
 */
@Composable
fun TimerCompleteView(
    rewardPoints: Int,
    onDismiss: () -> Unit,
    onExtend: () -> Unit,
    onExit: () -> Unit,
) {
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BrandSuccess)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("🎉 타이머 완료", color = Color(0xFF1A1B2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.timer_complete_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.timer_complete_body, rewardPoints),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            // 1행: 주요 액션 — 보던 숏폼 앱을 닫고 홈으로 이동.
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
            ) {
                Text(
                    stringResource(R.string.timer_complete_exit),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            // 2행: 보조 액션 — 5분 연장 / 오버레이만 종료.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onExtend,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.timer_complete_extend)) }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.timer_complete_dismiss)) }
            }
        }
    }
}
