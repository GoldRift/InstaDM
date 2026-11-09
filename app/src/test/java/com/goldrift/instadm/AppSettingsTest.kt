package com.goldrift.instadm

import com.goldrift.instadm.data.AppSettings
import com.goldrift.instadm.data.DEFAULT_DOUBLE_TAP_EMOJI
import com.goldrift.instadm.data.ThemeMode
import com.goldrift.instadm.data.normalizeDoubleTapEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsKeepCurrentBehavior() {
        val settings = AppSettings()

        assertEquals(ThemeMode.System, settings.themeMode)
        assertTrue(settings.dynamicColor)
        assertEquals(DEFAULT_DOUBLE_TAP_EMOJI, settings.doubleTapEmoji)
        assertTrue(settings.sendTypingIndicators)
        assertTrue(settings.sendReadReceipts)
        assertTrue(settings.showNotificationPreviews)
    }

    @Test
    fun storedThemeModeRoundTrips() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStored(mode.storedValue))
        }
    }

    @Test
    fun unknownOrMissingThemeModeFallsBackToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStored(""))
        assertEquals(ThemeMode.System, ThemeMode.fromStored("sepia"))
    }

    @Test
    fun themeModeDecidesDarkness() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
    }

    @Test
    fun doubleTapEmojiIsTrimmedAndBlankFallsBack() {
        assertEquals("😂", normalizeDoubleTapEmoji("  😂 "))
        assertEquals(DEFAULT_DOUBLE_TAP_EMOJI, normalizeDoubleTapEmoji(""))
        assertEquals(DEFAULT_DOUBLE_TAP_EMOJI, normalizeDoubleTapEmoji("   "))
        assertEquals(DEFAULT_DOUBLE_TAP_EMOJI, normalizeDoubleTapEmoji("x".repeat(33)))
    }
}
