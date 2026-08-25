package com.opencode.client.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Calm, professional developer palette. Dark-first; a single restrained mint accent carries the
 * identity. No purple gradients, no glow - hierarchy comes from spacing and typography.
 */

// Dark theme
val DarkBg = Color(0xFF0E1116)
val DarkSurface = Color(0xFF151A21)
val DarkSurfaceHigh = Color(0xFF1C232C)
val DarkSurfaceHighest = Color(0xFF242D38)
val DarkBorder = Color(0xFF2A3441)

val DarkText = Color(0xFFE6EBF2)
val DarkTextDim = Color(0xFF98A2B3)
val DarkTextFaint = Color(0xFF5E6B7E)

val MintPrimary = Color(0xFF6EE7C8)
val MintDimmed = Color(0xFF3B8F78)
val MintContainer = Color(0xFF12332B)

val AmberWarn = Color(0xFFF0B357)
val RedError = Color(0xFFF2766B)
val GreenOk = Color(0xFF63C97A)
val BlueInfo = Color(0xFF6CA7F2)

// Code colors (dark) - muted, readable
object CodeColors {
    val keyword = Color(0xFFB39EDB)
    val string = Color(0xFF9ECE8C)
    val comment = Color(0xFF5E6B7E)
    val number = Color(0xFFF0B357)
    val annotation = Color(0xFF6CA7F2)
    val function = Color(0xFF7FB4E8)
    val base = Color(0xFFD6DEEB)
}

// Code colors (light)
object CodeColorsLight {
    val keyword = Color(0xFF7A3E9D)
    val string = Color(0xFF207944)
    val comment = Color(0xFF8B95A6)
    val number = Color(0xFF9A6700)
    val annotation = Color(0xFF0550AE)
    val function = Color(0xFF1F6FEB)
    val base = Color(0xFF24292F)
}

// Diff colors (dark)
val DiffAddBg = Color(0x1F4FA96B)
val DiffAddFg = Color(0xFF8FD9A8)
val DiffDelBg = Color(0x26A94F4F)
val DiffDelFg = Color(0xFFF2A09A)

// Light theme
val LightBg = Color(0xFFFAFBFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFF1F3F6)
val LightSurfaceHighest = Color(0xFFE7EAEF)
val LightBorder = Color(0xFFDCE1E8)

val LightText = Color(0xFF1A2230)
val LightTextDim = Color(0xFF5A6577)
val LightTextFaint = Color(0xFF8B95A6)

val LightMintPrimary = Color(0xFF0B7A62)
val LightMintContainer = Color(0xFFD7F3EA)
