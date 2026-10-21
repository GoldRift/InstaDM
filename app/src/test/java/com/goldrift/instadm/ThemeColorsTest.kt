package com.goldrift.instadm

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.goldrift.instadm.ui.theme.DarkColorScheme
import com.goldrift.instadm.ui.theme.LightColorScheme
import com.goldrift.instadm.ui.theme.messageBubbleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bubble palette is derived from whatever scheme is active, so every
 * wallpaper palette (and every system contrast level) keeps the same role
 * relationships. These checks pin that contract to the static schemes.
 */
class ThemeColorsTest {
    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertPairContrast(fill: Color, onFill: Color, label: String) {
        val ratio = contrastRatio(fill, onFill)
        assertTrue("$label contrast was $ratio", ratio >= 4.5f)
    }

    @Test
    fun lightBubblesUseFilledAccentRoles() {
        val bubbles = messageBubbleColors(LightColorScheme, darkTheme = false)
        assertEquals(LightColorScheme.primary, bubbles.outgoing)
        assertEquals(LightColorScheme.onPrimary, bubbles.onOutgoing)
        assertEquals(LightColorScheme.surfaceContainerHigh, bubbles.incoming)
        assertEquals(LightColorScheme.onSurface, bubbles.onIncoming)
        assertEquals(LightColorScheme.primary, bubbles.composerAccent)
        assertEquals(LightColorScheme.onPrimary, bubbles.onComposerAccent)
    }

    @Test
    fun darkBubblesUseMutedContainerRoles() {
        val bubbles = messageBubbleColors(DarkColorScheme, darkTheme = true)
        assertEquals(DarkColorScheme.primaryContainer, bubbles.outgoing)
        assertEquals(DarkColorScheme.onPrimaryContainer, bubbles.onOutgoing)
        assertEquals(DarkColorScheme.surfaceContainerHigh, bubbles.incoming)
        assertEquals(DarkColorScheme.onSurface, bubbles.onIncoming)
        assertEquals(DarkColorScheme.primaryContainer, bubbles.composerAccent)
        assertEquals(DarkColorScheme.onPrimaryContainer, bubbles.onComposerAccent)
    }

    @Test
    fun bubbleTextStaysReadableInBothSchemes() {
        listOf(LightColorScheme to false, DarkColorScheme to true).forEach { (scheme, dark) ->
            val bubbles = messageBubbleColors(scheme, dark)
            val mode = if (dark) "dark" else "light"
            assertPairContrast(bubbles.outgoing, bubbles.onOutgoing, "$mode outgoing")
            assertPairContrast(bubbles.incoming, bubbles.onIncoming, "$mode incoming")
            assertPairContrast(bubbles.composerAccent, bubbles.onComposerAccent, "$mode composer")
        }
    }

    @Test
    fun surfaceContainersFormAStrictOrdering() {
        assertOrdered(LightColorScheme, listOf(
            LightColorScheme.surfaceContainerLowest,
            LightColorScheme.surfaceContainerLow,
            LightColorScheme.surfaceContainer,
            LightColorScheme.surfaceContainerHigh,
            LightColorScheme.surfaceContainerHighest,
        ), ascending = false)
        assertOrdered(DarkColorScheme, listOf(
            DarkColorScheme.surfaceContainerLowest,
            DarkColorScheme.surfaceContainerLow,
            DarkColorScheme.surfaceContainer,
            DarkColorScheme.surfaceContainerHigh,
            DarkColorScheme.surfaceContainerHighest,
        ), ascending = true)
    }

    private fun assertOrdered(scheme: ColorScheme, containers: List<Color>, ascending: Boolean) {
        containers.zipWithNext { lower, higher ->
            val lowerIsDarker = lower.luminance() < higher.luminance()
            assertTrue(
                "container sequence broke around ${scheme.primary}",
                lowerIsDarker == ascending,
            )
        }
    }

    @Test
    fun elevatedSurfacesAreNotTinted() {
        assertEquals(LightColorScheme.surface, LightColorScheme.surfaceTint)
        assertEquals(DarkColorScheme.surface, DarkColorScheme.surfaceTint)
        assertEquals(LightColorScheme.surface, LightColorScheme.background)
        assertEquals(DarkColorScheme.surface, DarkColorScheme.background)
    }
}
