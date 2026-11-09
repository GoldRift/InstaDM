package com.goldrift.instadm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Process-scoped settings backed by SharedPreferences. Compose state makes
 * changes observable app-wide; the store is written from the main thread and
 * read by the UI and by protocol helpers when they act.
 */
@Stable
class SettingsStore(
    initial: AppSettings = AppSettings(),
    private val persist: (AppSettings) -> Unit = {},
) {
    var current by mutableStateOf(initial)
        private set

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }

    fun setDynamicColor(value: Boolean) = update { it.copy(dynamicColor = value) }

    fun setDoubleTapEmoji(value: String) =
        update { it.copy(doubleTapEmoji = normalizeDoubleTapEmoji(value)) }

    fun setSendTypingIndicators(value: Boolean) =
        update { it.copy(sendTypingIndicators = value) }

    fun setSendReadReceipts(value: Boolean) =
        update { it.copy(sendReadReceipts = value) }

    fun setShowNotificationPreviews(value: Boolean) =
        update { it.copy(showNotificationPreviews = value) }

    fun reset() {
        current = AppSettings()
        persist(current)
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current)
        if (next == current) return
        current = next
        persist(next)
    }

    companion object {
        private const val PREFS = "instadm.settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_DOUBLE_TAP_EMOJI = "double_tap_emoji"
        private const val KEY_SEND_TYPING = "send_typing"
        private const val KEY_SEND_READ_RECEIPTS = "send_read_receipts"
        private const val KEY_NOTIFICATION_PREVIEWS = "notification_previews"

        fun create(context: Context): SettingsStore {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val initial = runCatching { load(prefs) }.getOrDefault(AppSettings())
            return SettingsStore(initial) { save(prefs, it) }
        }

        private fun load(prefs: SharedPreferences): AppSettings {
            val defaults = AppSettings()
            return AppSettings(
                themeMode = ThemeMode.fromStored(prefs.stringOr(KEY_THEME_MODE, null)),
                dynamicColor = prefs.booleanOr(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
                doubleTapEmoji = normalizeDoubleTapEmoji(
                    prefs.stringOr(KEY_DOUBLE_TAP_EMOJI, defaults.doubleTapEmoji).orEmpty(),
                ),
                sendTypingIndicators = prefs.booleanOr(
                    KEY_SEND_TYPING,
                    defaults.sendTypingIndicators,
                ),
                sendReadReceipts = prefs.booleanOr(
                    KEY_SEND_READ_RECEIPTS,
                    defaults.sendReadReceipts,
                ),
                showNotificationPreviews = prefs.booleanOr(
                    KEY_NOTIFICATION_PREVIEWS,
                    defaults.showNotificationPreviews,
                ),
            )
        }

        private fun SharedPreferences.stringOr(key: String, default: String?): String? =
            runCatching { getString(key, default) }.getOrDefault(default)

        private fun SharedPreferences.booleanOr(key: String, default: Boolean): Boolean =
            runCatching { getBoolean(key, default) }.getOrDefault(default)

        private fun save(prefs: SharedPreferences, settings: AppSettings) {
            prefs.edit {
                putString(KEY_THEME_MODE, settings.themeMode.storedValue)
                putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
                putString(KEY_DOUBLE_TAP_EMOJI, settings.doubleTapEmoji)
                putBoolean(KEY_SEND_TYPING, settings.sendTypingIndicators)
                putBoolean(KEY_SEND_READ_RECEIPTS, settings.sendReadReceipts)
                putBoolean(KEY_NOTIFICATION_PREVIEWS, settings.showNotificationPreviews)
            }
        }
    }
}
