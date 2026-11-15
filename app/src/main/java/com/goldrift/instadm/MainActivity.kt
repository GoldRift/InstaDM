package com.goldrift.instadm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.goldrift.instadm.data.SharedContent
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.service.MessageNotifier
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.ui.InstaDmApp
import com.goldrift.instadm.ui.theme.InstaDMTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var pendingThreadId by mutableStateOf<String?>(null)
    private var pendingShare by mutableStateOf<SharedContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingShare = restoreShare(savedInstanceState)
        handleNotificationIntent(intent)
        handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            val app = application as InstaDmApplication
            val settings = app.settings.current
            val darkTheme = settings.themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                // enableEdgeToEdge() at startup follows the system theme; a
                // forced in-app theme needs matching bar scrims and icon tint.
                val transparent = Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                )
            }
            InstaDMTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                InstaDmApp(
                    pendingThreadId = pendingThreadId,
                    onPendingThreadHandled = { pendingThreadId = null },
                    pendingShare = pendingShare,
                    onShareHandled = { pendingShare = null },
                )
            }
        }

        val app = application as InstaDmApplication
        if (app.requestsNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleShareIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val share = pendingShare ?: return
        outState.putString(KEY_SHARE_TEXT, share.text)
        outState.putStringArrayList(
            KEY_SHARE_URIS,
            ArrayList(share.uris.map(Uri::toString)),
        )
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
        val app = application as InstaDmApplication
        if (app.sessionStore.hasStoredSession()) {
            RealtimeService.start(this)
        }
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val threadId = intent?.getStringExtra(MessageNotifier.EXTRA_THREAD_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        intent.removeExtra(MessageNotifier.EXTRA_THREAD_ID)
        pendingThreadId = threadId
    }

    private fun handleShareIntent(intent: Intent?) {
        val share = SharedContent.from(intent) ?: return
        pendingShare = share
        // A rotation or relaunch replays the intent; clear it once read so the
        // share is not applied twice.
        if (intent != null) intent.action = null
    }

    private fun restoreShare(state: Bundle?): SharedContent? {
        if (state == null) return null
        val text = state.getString(KEY_SHARE_TEXT).orEmpty()
        // shortcut: a content URI restored after process death may have lost its
        // read grant, so sending it then fails with the media error.
        val uris = state.getStringArrayList(KEY_SHARE_URIS).orEmpty().map(Uri::parse)
        return SharedContent(text, uris).takeUnless { it.isEmpty }
    }

    private companion object {
        const val KEY_SHARE_TEXT = "share_text"
        const val KEY_SHARE_URIS = "share_uris"
    }
}
