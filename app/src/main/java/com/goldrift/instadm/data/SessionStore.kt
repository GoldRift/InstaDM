package com.goldrift.instadm.data

import android.content.Context
import java.io.File

/**
 * Reads the external session file used by the Android debug client.
 *
 * The app-specific external directory needs no storage permission and can be
 * populated with adb:
 *
 *   adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
 *   adb push ~/.config/instadm/cookies.json \
 *     /sdcard/Android/data/com.goldrift.instadm/files/cookies.json
 */
class SessionStore(context: Context) {
    private val appContext = context.applicationContext

    val cookieFile: File
        get() = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "cookies.json")

    /** App-private cache for downloaded media. Safe to clear at any time. */
    val mediaCacheDir: File
        get() = File(appContext.cacheDir, "media").apply { mkdirs() }

    fun readCookies(): String? = runCatching {
        cookieFile
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
