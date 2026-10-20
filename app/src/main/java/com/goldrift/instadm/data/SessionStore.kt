package com.goldrift.instadm.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the Instagram session.
 *
 * Captured cookies are encrypted with a non-exportable Android Keystore key and
 * stored in the app's no-backup directory. Debuggable builds also accept the
 * adb-pushed `cookies.json` used by the smoke tests and the CLI:
 *
 *   adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
 *   adb push ~/.config/instadm/cookies.json \
 *     /sdcard/Android/data/com.goldrift.instadm/files/cookies.json
 *
 * The encrypted session wins when both exist. Cookie values are never logged.
 */
class SessionStore(context: Context) {
    private val appContext = context.applicationContext

    val cookieFile: File
        get() = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "cookies.json")

    /** App-private cache for downloaded media. Safe to clear at any time. */
    val mediaCacheDir: File
        get() = File(appContext.cacheDir, "media").apply { mkdirs() }

    private val sessionFile = File(appContext.noBackupFilesDir, "session.enc")
    private val isDebuggable =
        appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Volatile
    private var cachedCookies: String? = null

    /** Cheap check for MainActivity before deciding whether to start realtime. */
    fun hasStoredSession(): Boolean =
        sessionFile.isFile || (isDebuggable && cookieFile.isFile)

    @Synchronized
    fun readCookies(): String? {
        cachedCookies?.let { return it }

        val decrypted = runCatching { readEncryptedSession() }
            .onFailure { sessionFile.delete() }
            .getOrNull()
        if (decrypted != null) {
            cachedCookies = decrypted
            return decrypted
        }

        if (isDebuggable) {
            val legacy = runCatching {
                cookieFile
                    .takeIf { it.isFile }
                    ?.readText()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()
            if (legacy != null) {
                cachedCookies = legacy
                return legacy
            }
        }
        return null
    }

    @Synchronized
    fun saveCookies(cookiesJson: String) {
        val plaintext = cookiesJson.trim()
        require(plaintext.isNotEmpty()) { "cookies are empty" }

        val payload = SessionCipher.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        val temp = File(sessionFile.parentFile, "${sessionFile.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(payload)
            output.fd.sync()
        }
        if (!temp.renameTo(sessionFile)) {
            sessionFile.writeBytes(payload)
            temp.delete()
        }
        cachedCookies = plaintext
    }

    /** Deletes the encrypted session and the legacy debug cookie file. */
    @Synchronized
    fun clearCookies() {
        cachedCookies = null
        sessionFile.delete()
        cookieFile.delete()
    }

    private fun readEncryptedSession(): String? {
        if (!sessionFile.isFile) return null
        val plaintext = SessionCipher.decrypt(sessionFile.readBytes())
        return plaintext.toString(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }
}

/**
 * AES-256-GCM with a key that Android Keystore will not export. The blob starts
 * with a one-byte format version and a one-byte IV length, followed by the IV
 * and ciphertext.
 */
private object SessionCipher {
    private const val KEY_ALIAS = "instadm.session.v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FORMAT_VERSION = 1
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val blob = ByteArray(2 + iv.size + ciphertext.size)
        blob[0] = FORMAT_VERSION.toByte()
        blob[1] = iv.size.toByte()
        iv.copyInto(blob, destinationOffset = 2)
        ciphertext.copyInto(blob, destinationOffset = 2 + iv.size)
        return blob
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 3 && blob[0].toInt() == FORMAT_VERSION) {
            "unsupported session format"
        }
        val ivSize = blob[1].toInt() and 0xFF
        require(ivSize > 0 && blob.size > 2 + ivSize) { "invalid session payload" }

        val iv = blob.copyOfRange(2, 2 + ivSize)
        val ciphertext = blob.copyOfRange(2 + ivSize, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
