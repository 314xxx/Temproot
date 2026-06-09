package com.temproot.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// MIUI 橙
private val MiOrange = Color(0xFFFF6B35)
private val MiOrangeDark = Color(0xFFFF8A65)
private val MiOrangeLight = Color(0xFFFFAB91)
private val MiOrangeContainer = Color(0xFFFFF0E8)

// MIUI 经典背景色
private val MiBgLight = Color(0xFFF5F5F5)
private val MiBgDark = Color(0xFF1A1A1A)
private val MiCardLight = Color(0xFFFFFFFF)
private val MiCardDark = Color(0xFF2C2C2C)
private val MiSurfaceLight = Color(0xFFF0F0F0)
private val MiSurfaceDark = Color(0xFF252525)

private val MiTextLight = Color(0xFF1A1A1A)
private val MiTextDark = Color(0xFFEBEBEB)
private val MiSubTextLight = Color(0xFF8E8E93)
private val MiSubTextDark = Color(0xFF8E8E93)

private val MiSuccessLight = Color(0xFF34C759)
private val MiErrorLight = Color(0xFFFF3B30)
private val MiSuccessDark = Color(0xFF30D158)
private val MiErrorDark = Color(0xFFFF453A)

private val MiLightColorScheme = lightColorScheme(
    primary = MiOrange,
    onPrimary = Color.White,
    primaryContainer = MiOrangeContainer,
    onPrimaryContainer = MiOrangeDark,
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEBFF),
    onSecondaryContainer = Color(0xFF4A45B0),
    tertiary = Color(0xFF00B578),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6F9F1),
    onTertiaryContainer = Color(0xFF008A5C),
    background = MiBgLight,
    onBackground = MiTextLight,
    surface = MiCardLight,
    onSurface = MiTextLight,
    surfaceVariant = MiSurfaceLight,
    onSurfaceVariant = MiSubTextLight,
    outline = Color(0xFFD1D1D6),
    error = MiErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFECEA),
    onErrorContainer = MiErrorLight
)

private val MiDarkColorScheme = darkColorScheme(
    primary = MiOrangeDark,
    onPrimary = Color(0xFF3D1A00),
    primaryContainer = Color(0xFF5D2800),
    onPrimaryContainer = MiOrangeLight,
    secondary = Color(0xFF7B79F0),
    onSecondary = Color(0xFF121030),
    secondaryContainer = Color(0xFF2D2A60),
    onSecondaryContainer = Color(0xFFCBCBFF),
    tertiary = Color(0xFF30D158),
    onTertiary = Color(0xFF002B18),
    tertiaryContainer = Color(0xFF004D28),
    onTertiaryContainer = Color(0xFF8FF0B0),
    background = MiBgDark,
    onBackground = MiTextDark,
    surface = MiCardDark,
    onSurface = MiTextDark,
    surfaceVariant = MiSurfaceDark,
    onSurfaceVariant = MiSubTextDark,
    outline = Color(0xFF48484A),
    error = MiErrorDark,
    onError = Color(0xFF3D0000),
    errorContainer = Color(0xFF5D0000),
    onErrorContainer = Color(0xFFFFB3B0)
)

private val MiTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun TempRootAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MiDarkColorScheme else MiLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiTypography,
        content = content
    )
}