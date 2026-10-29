package com.goldrift.instadm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.service.MessageNotifier
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.ui.InstaDmApp
import com.goldrift.instadm.ui.theme.InstaDMTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var pendingThreadId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            InstaDMTheme {
                InstaDmApp(
                    pendingThreadId = pendingThreadId,
                    onPendingThreadHandled = { pendingThreadId = null },
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
}
