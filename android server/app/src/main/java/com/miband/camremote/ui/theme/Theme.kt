package com.miband.camremote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val MiOrange = Color(0xFFFF6900)
val MiOrangeSoft = Color(0xFFFFB37A)
val MiGreen = Color(0xFF30B98C)
val MiRed = Color(0xFFE4473B)
val Ink = Color(0xFF111111)
val Paper = Color(0xFFF4F1EC)

private val LightColors = lightColorScheme(
    primary = MiOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2CE),
    onPrimaryContainer = Color(0xFF4A1B00),
    secondary = Color(0xFF1E8068),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F0E4),
    onSecondaryContainer = Color(0xFF00281E),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFCFAF7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE5DD),
    onSurfaceVariant = Color(0xFF68615A),
    outline = Color(0xFFD7D0C7),
    outlineVariant = Color(0xFFE8E2DA),
    error = MiRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8526),
    onPrimary = Color(0xFF2A1000),
    primaryContainer = Color(0xFF3B1B04),
    onPrimaryContainer = Color(0xFFFFD9BD),
    secondary = Color(0xFF68D8B5),
    onSecondary = Color(0xFF00251B),
    secondaryContainer = Color(0xFF00382A),
    onSecondaryContainer = Color(0xFFC5F5E4),
    background = Color(0xFF08090B),
    onBackground = Color(0xFFF7F2EA),
    surface = Color(0xFF131417),
    onSurface = Color(0xFFF7F2EA),
    surfaceVariant = Color(0xFF1D1F23),
    onSurfaceVariant = Color(0xFFA8A29A),
    outline = Color(0xFF32353A),
    outlineVariant = Color(0xFF23252A),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun MiBandCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
