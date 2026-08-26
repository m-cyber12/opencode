package dev.opencode.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF101418),
    secondary = Color(0xFF9ECE6A),
    tertiary = Color(0xFFBB9AF7),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1D232C),
    onSurfaceVariant = Color(0xFF9DA7B3),
    outline = Color(0xFF30363D),
    error = Color(0xFFF7768E),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    secondary = Color(0xFF2F9E44),
    tertiary = Color(0xFF7048E8),
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF1C2128),
    surface = Color.White,
    onSurface = Color(0xFF1C2128),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E),
)

@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
