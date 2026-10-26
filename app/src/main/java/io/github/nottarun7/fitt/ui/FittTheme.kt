package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import io.github.nottarun7.fitt.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF275D46), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EDCB), onPrimaryContainer = Color(0xFF183523),
    secondary = Color(0xFF53634B), secondaryContainer = Color(0xFFE1E9DA),
    background = Color(0xFFF7F9F4), surface = Color(0xFFF7F9F4),
    surfaceContainer = Color(0xFFEDF1E8), surfaceContainerLow = Color(0xFFFFFFFF),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFD5A5), onPrimary = Color(0xFF173722),
    primaryContainer = Color(0xFF28543C), onPrimaryContainer = Color(0xFFDAEED1),
    background = Color(0xFF121711), surface = Color(0xFF121711),
    surfaceContainer = Color(0xFF232B21), surfaceContainerLow = Color(0xFF1A2118),
    secondary = Color(0xFFBECDB5), onSecondary = Color(0xFF293525),
    secondaryContainer = Color(0xFF3F4C39), onSecondaryContainer = Color(0xFFDAE9D0),
    onBackground = Color(0xFFE1E6DB), onSurface = Color(0xFFE1E6DB), onSurfaceVariant = Color(0xFFC2CABB),
    outline = Color(0xFF8C9585), outlineVariant = Color(0xFF424B3D),
)

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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
