package com.groomteam2.dopamind.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.service.ShortFormAccessibilityService
import com.groomteam2.dopamind.ui.theme.BrandSuccess

/**
 * 두 가지 시스템 권한 부여를 안내하는 화면.
 *
 *  1) SYSTEM_ALERT_WINDOW (다른 앱 위에 그리기) — 오버레이 코치 팝업에 필요
 *  2) Accessibility Service 활성화 — 스크롤 이벤트 후킹에 필요
 *
 * 두 권한 모두 시스템 설정 화면을 직접 열어 사용자가 수동 허용해야 함(우회 불가).
 * onResume 시점에 다시 확인해서 두 권한이 모두 켜졌는지 체크.
 */
@Composable
fun PermissionScreen(onAllGranted: () -> Unit) {
    val ctx = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var a11yGranted by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }

    // 화면이 다시 보일 때마다 권한 재확인 — 사용자가 설정 화면 다녀온 직후 자동 갱신.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(ctx)
                a11yGranted = isAccessibilityEnabled(ctx)
            }
        }
        owner.lifecycle.addObserver(obs)
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.permission_title),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(24.dp))

            PermissionCard(
                granted = overlayGranted,
                title = stringResource(R.string.permission_overlay),
                description = stringResource(R.string.permission_overlay_desc),
                onClick = { openOverlaySettings(ctx) },
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                granted = a11yGranted,
                title = stringResource(R.string.permission_accessibility),
                description = stringResource(R.string.permission_accessibility_desc),
                onClick = { openAccessibilitySettings(ctx) },
            )

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onAllGranted,
                enabled = overlayGranted && a11yGranted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.permission_done)) }
        }
    }
}

@Composable
private fun PermissionCard(
    granted: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) BrandSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = !granted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (granted) "허용 완료 ✓" else stringResource(R.string.permission_grant))
            }
        }
    }
}

// ── 시스템 설정 인텐트 헬퍼 ────────────────────────────────────────

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + context.packageName),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(intent)
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    )
}

/**
 * 우리 서비스가 시스템 접근성 설정에서 활성화돼 있는지 확인.
 * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 는 "패키지/서비스명:..." 의 콜론 구분 문자열.
 */
private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = context.packageName + "/" + ShortFormAccessibilityService::class.java.name
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
        val service = splitter.next()
        if (service.equals(expected, ignoreCase = true)) return true
    }
    return false
}
