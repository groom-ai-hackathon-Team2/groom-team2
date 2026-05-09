package com.groomteam2.dopamind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material3 테마.
 * 다크모드는 자동 추종. 시연용이라 동적 컬러는 끔(브랜드 색상 일관성).
 */
private val LightColors = lightColorScheme(
    primary = BrandPurple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandPink,
    tertiary = BrandSuccess,
    error = BrandWarning,
)

private val DarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandPink,
    tertiary = BrandSuccess,
    error = BrandWarning,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = OnSurfaceDim,
)

@Composable
fun DopamindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DopamindTypography,
        content = content,
    )
}
