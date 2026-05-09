package com.groomteam2.dopamind.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.groomteam2.dopamind.ui.theme.BrandPurple
import com.groomteam2.dopamind.ui.theme.BrandPurpleDark

/**
 * 타이머 동작 중 화면에 떠 있는 작은 동그라미.
 *  - 평소엔 보라색 원에 mm:ss 표시
 *  - 탭하면 작은 메뉴(남은 시간, 앱 열기, 종료) 펼침
 *
 * 드래그 이동은 WindowManager 측 OnTouchListener 가 처리하므로 여기서는 시각만 담당.
 */
@Composable
fun TimerFloatingBubble(
    remainText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(BrandPurple)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = remainText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
        if (expanded) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandPurpleDark)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.timer_bubble_remaining, remainText),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onOpenApp() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.timer_bubble_open_app), color = Color.White)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onStop() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.timer_bubble_stop), color = Color.White)
                    }
                }
            }
        }
    }
}
