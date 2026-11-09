package com.goldrift.instadm.data

/** Emoji a double tap sends unless the viewer picked another one. */
const val DEFAULT_DOUBLE_TAP_EMOJI = "❤️"

enum class ThemeMode(val storedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

/**
 * Device-local preferences. They deliberately outlive the session and are not
 * stored in Room, whose tables are wiped on logout.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val doubleTapEmoji: String = DEFAULT_DOUBLE_TAP_EMOJI,
    val sendTypingIndicators: Boolean = true,
    val sendReadReceipts: Boolean = true,
    val showNotificationPreviews: Boolean = true,
)

/** A picked emoji may arrive with padding; an empty or malformed choice restores the default. */
fun normalizeDoubleTapEmoji(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.isEmpty() || trimmed.length > 32) DEFAULT_DOUBLE_TAP_EMOJI else trimmed
}
