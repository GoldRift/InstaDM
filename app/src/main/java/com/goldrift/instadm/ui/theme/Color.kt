package com.goldrift.instadm.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Near-black content surface on a slightly lighter app-bar layer in dark mode,
 * and the inverse in light mode. Blue is reserved for outgoing bubbles, read
 * receipts, and the composer action.
 */

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF5F6368),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EAED),
    onSecondaryContainer = Color(0xFF202124),
    tertiary = Color(0xFF1A73E8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E3FD),
    onTertiaryContainer = Color(0xFF041E49),
    background = Color(0xFFF0F4F9),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFF1F3F4),
    surfaceContainerHighest = Color(0xFFE8EAED),
    outline = Color(0xFF80868B),
    outlineVariant = Color(0xFFDADCE0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF1F1F1F),
    inverseOnSurface = Color(0xFFF4F6F7),
    inversePrimary = Color(0xFFA8C7FA),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFC2E7FF),
    secondary = Color(0xFF9AA0A6),
    onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFF3C4043),
    onSecondaryContainer = Color(0xFFE3E8EF),
    tertiary = Color(0xFFA8C7FA),
    onTertiary = Color(0xFF062E6F),
    tertiaryContainer = Color(0xFF004A77),
    onTertiaryContainer = Color(0xFFC2E7FF),
    background = Color(0xFF1D2023),
    onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF3C4043),
    onSurfaceVariant = Color(0xFFA0A7AE),
    surfaceContainerLowest = Color(0xFF0B0F11),
    surfaceContainerLow = Color(0xFF171A1D),
    surfaceContainer = Color(0xFF1D2023),
    surfaceContainerHigh = Color(0xFF1D2023),
    surfaceContainerHighest = Color(0xFF2A2D30),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF3C4043),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Color(0xFFE3E8EF),
    inverseOnSurface = Color(0xFF101417),
    inversePrimary = Color(0xFF1A73E8),
)

/**
 * Colors that do not map onto the standard Material roles: the outgoing bubble
 * is a muted blue and the read receipt a lighter accent.
 */
@Immutable
data class MessageBubbleColors(
    val outgoing: Color,
    val onOutgoing: Color,
    val incoming: Color,
    val onIncoming: Color,
    val accent: Color,
    val composerAccent: Color,
    val onComposerAccent: Color,
)

val LightMessageBubbleColors = MessageBubbleColors(
    outgoing = Color(0xFF1A73E8),
    onOutgoing = Color(0xFFFFFFFF),
    incoming = Color(0xFFF1F3F4),
    onIncoming = Color(0xFF1F1F1F),
    accent = Color(0xFF1A73E8),
    composerAccent = Color(0xFF1A73E8),
    onComposerAccent = Color(0xFFFFFFFF),
)

val DarkMessageBubbleColors = MessageBubbleColors(
    outgoing = Color(0xFF244B61),
    onOutgoing = Color(0xFFF4F6F7),
    incoming = Color(0xFF1D2023),
    onIncoming = Color(0xFFDFE2E5),
    accent = Color(0xFFA8CCE5),
    composerAccent = Color(0xFF3D446B),
    onComposerAccent = Color(0xFFFFFFFF),
)

val LocalMessageBubbleColors = staticCompositionLocalOf { LightMessageBubbleColors }
