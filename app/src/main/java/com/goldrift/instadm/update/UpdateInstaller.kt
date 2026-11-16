package com.goldrift.instadm.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.goldrift.instadm.InstaDmApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Streams the release APK straight into a PackageInstaller session, so the
 * bytes are never a second copy on disk and the system owns the confirmation.
 */
object UpdateInstaller {
    private const val SESSION_NAME = "base.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Size, HTTPS, and the SHA-256 of the streamed bytes are all that can be
     * checked here: a session's staged APK cannot be read back, so the system
     * installer remains the authority on package id and signer.
     */
    suspend fun install(
        context: Context,
        apk: UpdateApk,
        onProgress: (sent: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        var committed = false
        try {
            client.newCall(Request.Builder().url(apk.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val length = body.contentLength()
                if (length >= 0 && length != apk.size) throw IOException("Unexpected size")
                val digest = MessageDigest.getInstance("SHA-256")
                session.openWrite(SESSION_NAME, 0, apk.size).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var sent = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            sent += read
                            val percent = (sent * 100 / apk.size).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(sent)
                            }
                        }
                    }
                    output.flush()
                    session.fsync(output)
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(apk.sha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch")
                }
            }
            session.commit(resultIntentSender(context, sessionId))
            committed = true
        } finally {
            if (!committed) runCatching { session.abandon() }
            runCatching { session.close() }
        }
    }

    private fun resultIntentSender(context: Context, sessionId: Int): IntentSender {
        // commit() rejects an immutable PendingIntent on target SDK 35+; the
        // installer writes the result extras into it.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallResultReceiver::class.java),
            flags,
        ).intentSender
    }
}

/**
 * The installer reports back through this receiver. It can run in a fresh
 * process, so every call is optional and the confirmation intent is started
 * before asking the manager for anything.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                ) ?: return
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmation)
            }
            else -> {
                val app = context.applicationContext as? InstaDmApplication ?: return
                app.updates.installResult(
                    status,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                )
            }
        }
    }
}
