package com.goldrift.instadm

import com.goldrift.instadm.data.AppSettings
import com.goldrift.instadm.data.SettingsStore
import com.goldrift.instadm.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun settersNormalizeAndPersist() {
        val writes = mutableListOf<AppSettings>()
        val store = SettingsStore(persist = { writes += it })

        store.setThemeMode(ThemeMode.Dark)
        store.setDoubleTapEmoji("  😂 ")
        store.setSendTypingIndicators(false)

        assertEquals(ThemeMode.Dark, store.current.themeMode)
        assertEquals("😂", store.current.doubleTapEmoji)
        assertFalse(store.current.sendTypingIndicators)
        assertEquals(3, writes.size)
        assertEquals(store.current, writes.last())
    }

    @Test
    fun settingTheSameValueDoesNotWriteAgain() {
        val writes = mutableListOf<AppSettings>()
        val store = SettingsStore(persist = { writes += it })

        store.setSendReadReceipts(true)
        store.setDynamicColor(true)

        assertTrue(writes.isEmpty())
    }

    @Test
    fun resetRestoresDefaultsAndPersists() {
        val writes = mutableListOf<AppSettings>()
        val store = SettingsStore(
            initial = AppSettings(themeMode = ThemeMode.Light, sendTypingIndicators = false),
            persist = { writes += it },
        )

        store.reset()

        assertEquals(AppSettings(), store.current)
        assertEquals(listOf(AppSettings()), writes)
    }
}
