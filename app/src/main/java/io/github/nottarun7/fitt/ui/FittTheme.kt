package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import io.github.nottarun7.fitt.data.ThemeMode
import io.github.nottarun7.fitt.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5FCE), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EFFE), onPrimaryContainer = Color(0xFF1B427A),
    secondary = Color(0xFF4B607D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF1F7), onSecondaryContainer = Color(0xFF23374F),
    tertiary = Color(0xFF277A64), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCF5EB), onTertiaryContainer = Color(0xFF164A3C),
    background = Color(0xFFF4F6F9), onBackground = Color(0xFF172332),
    surface = Color.White, onSurface = Color(0xFF172332),
    surfaceDim = Color(0xFFE9EEF4), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF2F6), surfaceContainerHigh = Color(0xFFE4EAF1), surfaceContainerHighest = Color(0xFFDCE4ED),
    onSurfaceVariant = Color(0xFF526276), outline = Color(0xFF7B8A9C), outlineVariant = Color(0xFFDCE3EC),
    surfaceTint = Color(0xFF1B5FCE), inverseSurface = Color(0xFF243144), inverseOnSurface = Color(0xFFF1F4F8), inversePrimary = Color(0xFFADC9FF),
    error = Color(0xFFBA3340), onError = Color.White, errorContainer = Color(0xFFFFE7EA), onErrorContainer = Color(0xFF82212B),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90B8FF), onPrimary = Color(0xFF102B54),
    primaryContainer = Color(0xFF203E6C), onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFFB4C5DD), onSecondary = Color(0xFF203248),
    secondaryContainer = Color(0xFF293D56), onSecondaryContainer = Color(0xFFDFE9F8),
    tertiary = Color(0xFF8ED4BD), onTertiary = Color(0xFF10382D),
    tertiaryContainer = Color(0xFF1B4C3F), onTertiaryContainer = Color(0xFFC0F0DE),
    background = Color(0xFF0E1723), onBackground = Color(0xFFEEF3FA),
    surface = Color(0xFF142131), onSurface = Color(0xFFEEF3FA),
    surfaceDim = Color(0xFF0E1723), surfaceBright = Color(0xFF304158),
    surfaceContainerLowest = Color(0xFF0A121D), surfaceContainerLow = Color(0xFF142131),
    surfaceContainer = Color(0xFF1A2A3D), surfaceContainerHigh = Color(0xFF24354B), surfaceContainerHighest = Color(0xFF304158),
    onSurfaceVariant = Color(0xFFA9B8CC), outline = Color(0xFF7C91AB), outlineVariant = Color(0xFF2C4058),
    surfaceTint = Color(0xFF90B8FF), inverseSurface = Color(0xFFE7EDF5), inverseOnSurface = Color(0xFF172332), inversePrimary = Color(0xFF1B5FCE),
    error = Color(0xFFFFB1B9), onError = Color(0xFF5E1822), errorContainer = Color(0xFF752631), onErrorContainer = Color(0xFFFFDBDF),
)

private val Barlow = FontFamily(Font(R.font.barlow_regular, FontWeight.Normal), Font(R.font.barlow_medium, FontWeight.Medium), Font(R.font.barlow_semibold, FontWeight.SemiBold))
private val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))
private fun body(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Barlow, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp)
private fun heading(size: Int, height: Int) = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = height.sp)
private val FittTypography = Typography(
    displayLarge = heading(48, 52), displayMedium = heading(42, 46), displaySmall = heading(36, 40),
    headlineLarge = heading(34, 38), headlineMedium = heading(30, 34), headlineSmall = heading(26, 30),
    titleLarge = body(21, 27, FontWeight.SemiBold), titleMedium = body(17, 23, FontWeight.SemiBold), titleSmall = body(15, 21, FontWeight.SemiBold),
    bodyLarge = body(16, 24), bodyMedium = body(14, 21), bodySmall = body(12, 18),
    labelLarge = body(14, 20, FontWeight.SemiBold), labelMedium = body(12, 18, FontWeight.Medium), labelSmall = body(12, 16, FontWeight.Medium),
)
private val FittShapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp))

@Composable
fun FittTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = FittTypography, shapes = FittShapes, content = content)
}
