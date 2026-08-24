package com.temproot.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// HyperOS 配色
private val HyperOrange = Color(0xFFFF6900)       // 小米主橙
private val HyperOrangeDark = Color(0xFFE05A00)
private val HyperOrangeContainer = Color(0xFFFFE8D9)
private val HyperBg = Color(0xFFF6F6F8)           // 浅灰背景
private val HyperSurface = Color(0xFFFFFFFF)      // 纯白卡片
private val HyperTextPrimary = Color(0xFF1A1A1E)
private val HyperTextSecondary = Color(0xFF8C8C99)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF5C2600),
    onPrimaryContainer = Color(0xFFFFDBC7),
    secondary = Color(0xFF9DCBFF),
    background = Color(0xFF121216),
    surface = Color(0xFF1C1C22),
    onBackground = Color(0xFFE8E8EC),
    onSurface = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFF9C9CA8),
    outline = Color(0xFF484854),
    error = Color(0xFFFF6B6B)
)

private val LightScheme = lightColorScheme(
    primary = HyperOrange,
    onPrimary = Color.White,
    primaryContainer = HyperOrangeContainer,
    onPrimaryContainer = HyperOrangeDark,
    secondary = Color(0xFF0091FF),
    background = HyperBg,
    onBackground = HyperTextPrimary,
    surface = HyperSurface,
    onSurface = HyperTextPrimary,
    surfaceVariant = Color(0xFFF2F2F5),
    onSurfaceVariant = HyperTextSecondary,
    outline = Color(0xFFDDDDE4),
    error = Color(0xFFE5484D)
)

// Miuix 风格超大圆角
private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val MiuixTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp)
)

@Composable
fun TempRootAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiuixTypography,
        shapes = MiuixShapes,
        content = content
    )
}
