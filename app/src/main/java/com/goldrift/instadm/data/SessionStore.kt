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
class SessionStore(context: Context) : SessionBacking {
    private val appContext = context.applicationContext

    val cookieFile: File
        get() = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "cookies.json")

    override val cookieLocation: String
        get() = cookieFile.absolutePath

    /** App-private cache for downloaded media. Safe to clear at any time. */
    override val mediaCacheDir: File
        get() = File(appContext.cacheDir, "media").apply { mkdirs() }

    /**
     * Durable copies of attachments queued for sending. Kept out of the cache
     * because a pending send must survive process death and cache eviction.
     */
    val outboxDir: File
        get() = File(appContext.filesDir, "outbox").apply { mkdirs() }

    private val sessionFile = File(appContext.noBackupFilesDir, "session.enc")
    private val isDebuggable =
        appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val cookieCache = SessionCookieCache(
        isDebuggable = isDebuggable,
        hasEncryptedFile = { sessionFile.isFile },
        decryptEncryptedFile = { readEncryptedSession() },
        deleteEncryptedFile = { sessionFile.delete() },
        legacyFileLastModified = { cookieFile.takeIf { it.isFile }?.lastModified() },
        readLegacyFile = { cookieFile.takeIf { it.isFile }?.readText() },
    )

    /** Cheap check for startup routing and realtime start without touching Keystore. */
    fun hasStoredSession(): Boolean =
        sessionFile.isFile || (isDebuggable && cookieFile.isFile)

    @Synchronized
    override fun readCookies(): String? = cookieCache.read()

    @Synchronized
    override fun saveCookies(cookiesJson: String) {
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
        cookieCache.recordEncrypted(plaintext)
    }

    /** Deletes the encrypted session and the legacy debug cookie file. */
    @Synchronized
    override fun clearCookies() {
        cookieCache.clear()
        sessionFile.delete()
        cookieFile.delete()
    }

    private fun readEncryptedSession(): String? {
        if (!sessionFile.isFile) return null
        val plaintext = SessionCipher.decrypt(sessionFile.readBytes())
        return plaintext.toString(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }
}

internal class SessionCookieCache(
    private val isDebuggable: Boolean,
    private val hasEncryptedFile: () -> Boolean,
    private val decryptEncryptedFile: () -> String?,
    private val deleteEncryptedFile: () -> Unit,
    private val legacyFileLastModified: () -> Long?,
    private val readLegacyFile: () -> String?,
) {
    private var cached: String? = null
    private var fromLegacyFile: Boolean = false
    private var legacyLastModified: Long = -1L

    @Synchronized
    fun read(): String? {
        val existing = cached
        if (existing != null) {
            if (!fromLegacyFile) return existing
            // In debuggable builds an adb-pushed cookies.json can change
            // out-of-band; reload if its mtime changed or an encrypted session
            // superseded it.
            val currentMtime = legacyFileLastModified()
            if (!hasEncryptedFile() && currentMtime != null && currentMtime == legacyLastModified) {
                return existing
            }
            cached = null
            fromLegacyFile = false
            legacyLastModified = -1L
        }

        if (hasEncryptedFile()) {
            val decrypted = runCatching { decryptEncryptedFile() }
                .onFailure { deleteEncryptedFile() }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (decrypted != null) {
                cached = decrypted
                fromLegacyFile = false
                legacyLastModified = -1L
                return decrypted
            }
        }

        if (isDebuggable) {
            val mtime = legacyFileLastModified() ?: return null
            val legacy = runCatching { readLegacyFile() }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (legacy != null) {
                cached = legacy
                fromLegacyFile = true
                legacyLastModified = mtime
                return legacy
            }
        }
        return null
    }

    @Synchronized
    fun recordEncrypted(cookiesJson: String) {
        cached = cookiesJson
        fromLegacyFile = false
        legacyLastModified = -1L
    }

    @Synchronized
    fun clear() {
        cached = null
        fromLegacyFile = false
        legacyLastModified = -1L
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
