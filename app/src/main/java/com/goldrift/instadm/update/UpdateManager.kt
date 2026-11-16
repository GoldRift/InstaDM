package com.goldrift.instadm.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.goldrift.instadm.InstaDmApplication
import com.goldrift.instadm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: UpdateManifest, val apk: UpdateApk) : UpdateState
    data class Downloading(
        val update: UpdateManifest,
        val apk: UpdateApk,
        val fraction: Float,
    ) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String?) : UpdateState
}

/** Process-scoped updater state; the check runs at most once a day in the foreground. */
class UpdateManager(
    app: InstaDmApplication,
    private val scope: CoroutineScope,
) {
    private val context: Context = app.applicationContext
    private val settings = app.settings
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private var job: Job? = null

    private val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    val installedVersionName: String = packageInfo?.versionName.orEmpty()

    private val installedVersionCode: Long = packageInfo?.let { info ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } ?: 0L

    /** Debug and demo builds can't install the release APK over themselves. */
    private val canSelfUpdate: Boolean =
        context.packageName == "com.goldrift.instadm" &&
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0

    /**
     * A found update outlives the process, so a restart inside the same day does
     * not hide the banner until tomorrow's check.
     */
    private var lastFound: UpdateState.Available? = if (canSelfUpdate) {
        prefs.getString(KEY_PENDING, null)
            ?.let(UpdateManifest::parse)
            ?.takeIf { it.isNewerThan(installedVersionCode) }
            ?.let { update ->
                update.apkFor(Build.SUPPORTED_ABIS.toList())
                    ?.let { UpdateState.Available(update, it) }
            }
    } else {
        null
    }

    var state by mutableStateOf<UpdateState>(lastFound ?: UpdateState.Idle)
        private set

    var dismissedVersionCode by mutableLongStateOf(prefs.getLong(KEY_DISMISSED, 0L))
        private set

    fun checkIfStale() {
        if (!canSelfUpdate || !settings.current.autoCheckUpdates) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return
        }
        check(manual = false)
    }

    fun checkNow() = check(manual = true)

    fun install() {
        val available = state as? UpdateState.Available ?: lastFound ?: return
        job?.cancel()
        job = scope.launch {
            state = UpdateState.Downloading(available.update, available.apk, 0f)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    UpdateInstaller.install(context, available.apk) { sent ->
                        val fraction = sent.toFloat() / available.apk.size
                        scope.launch {
                            if (state is UpdateState.Downloading) {
                                state = UpdateState.Downloading(
                                    update = available.update,
                                    apk = available.apk,
                                    fraction = fraction.coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                }
            }
            state = result.fold(
                onSuccess = { UpdateState.Installing },
                onFailure = {
                    UpdateState.Failed(
                        it.message ?: context.getString(R.string.updates_install_failed),
                    )
                },
            )
        }
    }

    fun dismiss() {
        val available = state as? UpdateState.Available ?: return
        dismissedVersionCode = available.update.versionCode
        prefs.edit { putLong(KEY_DISMISSED, dismissedVersionCode) }
    }

    /** Only a session this process started can fail; a fresh process ignores it. */
    fun installResult(status: Int, message: String?) {
        if (state is UpdateState.Idle) return
        state = UpdateState.Failed(message ?: context.getString(R.string.updates_install_failed))
    }

    private fun check(manual: Boolean) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading ||
            state is UpdateState.Installing
        ) {
            return
        }
        if (!canSelfUpdate) {
            if (manual) state = UpdateState.Failed(context.getString(R.string.updates_unsupported))
            return
        }
        // Stamped before the request so a failing network also waits a day.
        prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        job?.cancel()
        state = UpdateState.Checking
        job = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetch() } }
            state = result.fold(
                onSuccess = { next ->
                    lastFound = next as? UpdateState.Available
                    next
                },
                onFailure = {
                    if (manual) {
                        UpdateState.Failed(
                            it.message ?: context.getString(R.string.updates_check_failed),
                        )
                    } else {
                        // A failed automatic check keeps whatever it already knew.
                        lastFound ?: UpdateState.Idle
                    }
                },
            )
        }
    }

    private fun fetch(): UpdateState {
        client.newCall(Request.Builder().url(UpdateManifest.URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = response.body?.string() ?: throw IOException("Empty response")
            val update = UpdateManifest.parse(json) ?: throw IOException("Malformed manifest")
            val apk = update.apkFor(Build.SUPPORTED_ABIS.toList()) ?: return UpdateState.Idle
            return if (update.isNewerThan(installedVersionCode)) {
                prefs.edit { putString(KEY_PENDING, json) }
                UpdateState.Available(update, apk)
            } else {
                prefs.edit { remove(KEY_PENDING) }
                UpdateState.Idle
            }
        }
    }

    private companion object {
        const val PREFS = "instadm.updates"
        const val KEY_DISMISSED = "dismissed_version_code"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_PENDING = "pending_manifest"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
