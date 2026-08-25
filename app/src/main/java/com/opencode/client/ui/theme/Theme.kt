package com.opencode.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.opencode.client.data.settings.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = MintPrimary,
    onPrimary = Color(0xFF06231B),
    primaryContainer = MintContainer,
    onPrimaryContainer = MintPrimary,
    secondary = Color(0xFF9DB2C7),
    onSecondary = Color(0xFF16202B),
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkText,
    tertiary = BlueInfo,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextDim,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = RedError,
    onError = Color(0xFF2A0E0B),
    errorContainer = Color(0xFF3A1714),
    onErrorContainer = Color(0xFFF5B8B1)
)

private val LightScheme = lightColorScheme(
    primary = LightMintPrimary,
    onPrimary = Color.White,
    primaryContainer = LightMintContainer,
    onPrimaryContainer = Color(0xFF05372C),
    secondary = Color(0xFF48586B),
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceHigh,
    onSecondaryContainer = LightText,
    tertiary = Color(0xFF0B57D0),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextDim,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightBg,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/** Extended palette entries that Material 3 does not cover (code, diffs, status). */
data class ExtendedColors(
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,
    val codeAnnotation: Color,
    val codeFunction: Color,
    val codeBase: Color,
    val codeBg: Color,
    val diffAddBg: Color,
    val diffAddFg: Color,
    val diffDelBg: Color,
    val diffDelFg: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val textFaint: Color,
    val border: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        codeKeyword = CodeColors.keyword,
        codeString = CodeColors.string,
        codeComment = CodeColors.comment,
        codeNumber = CodeColors.number,
        codeAnnotation = CodeColors.annotation,
        codeFunction = CodeColors.function,
        codeBase = CodeColors.base,
        codeBg = Color(0xFF10151C),
        diffAddBg = DiffAddBg,
        diffAddFg = DiffAddFg,
        diffDelBg = DiffDelBg,
        diffDelFg = DiffDelFg,
        success = GreenOk,
        warning = AmberWarn,
        info = BlueInfo,
        textFaint = DarkTextFaint,
        border = DarkBorder
    )
}

@Composable
fun OpencodeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme

    val extended = if (dark) {
        ExtendedColors(
            codeKeyword = CodeColors.keyword,
            codeString = CodeColors.string,
            codeComment = CodeColors.comment,
            codeNumber = CodeColors.number,
            codeAnnotation = CodeColors.annotation,
            codeFunction = CodeColors.function,
            codeBase = CodeColors.base,
            codeBg = Color(0xFF10151C),
            diffAddBg = DiffAddBg,
            diffAddFg = DiffAddFg,
            diffDelBg = DiffDelBg,
            diffDelFg = DiffDelFg,
            success = GreenOk,
            warning = AmberWarn,
            info = BlueInfo,
            textFaint = DarkTextFaint,
            border = DarkBorder
        )
    } else {
        ExtendedColors(
            codeKeyword = CodeColorsLight.keyword,
            codeString = CodeColorsLight.string,
            codeComment = CodeColorsLight.comment,
            codeNumber = CodeColorsLight.number,
            codeAnnotation = CodeColorsLight.annotation,
            codeFunction = CodeColorsLight.function,
            codeBase = CodeColorsLight.base,
            codeBg = Color(0xFFF4F6F8),
            diffAddBg = Color(0x263FB96F),
            diffAddFg = Color(0xFF116329),
            diffDelBg = Color(0x26CF5548),
            diffDelFg = Color(0xFF82071D),
            success = Color(0xFF187738),
            warning = Color(0xFF9A6700),
            info = Color(0xFF0969DA),
            textFaint = LightTextFaint,
            border = LightBorder
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content
        )
    }
}

object AppTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
