package com.goldrift.instadm.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.goldrift.instadm.MainActivity
import com.goldrift.instadm.R
import com.goldrift.instadm.core.mobile.WatchCallback
import com.goldrift.instadm.data.GoClient
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.RealtimeStatus
import com.goldrift.instadm.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the DGW watch loop alive while the UI is not visible. The service owns
 * its own Go client and publishes events into [RealtimeBus]; the process-scoped
 * [MessageNotifier] consumes them to post per-conversation notifications.
 */
class RealtimeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeEpoch = AtomicInteger()

    @Volatile
    private var client: GoClient? = null

    @Volatile
    private var watchCallback: WatchCallback? = null

    @Volatile
    private var starting = false

    @Volatile
    private var reloading = false

    @Volatile
    private var sessionCookies: String? = null

    @Volatile
    private var destroyed = false

    @Volatile
    private var keepStatusOnDestroy = false

    @Volatile
    private var lastStatusText: String? = null

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        NotificationChannels.ensure(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            keepStatusOnDestroy = false
            RealtimeBus.setStatus(RealtimeStatus.Stopped)
            stopSelf()
            return START_NOT_STICKY
        }

        keepStatusOnDestroy = false
        val initialStatus = RealtimeBus.status.value
        val initialText = if (initialStatus == RealtimeStatus.Stopped) {
            getString(R.string.realtime_connecting)
        } else {
            statusText(initialStatus)
        }
        startForegroundNow(initialText)
        if (intent?.action == ACTION_RESTART) {
            reloadWatchIfSessionChanged()
        } else {
            requestWatch()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        serviceScope.cancel()
        stopWatch()
        if (!keepStatusOnDestroy) {
            RealtimeBus.setStatus(RealtimeStatus.Stopped)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }

    private fun requestWatch() {
        if (client != null || starting || destroyed) return
        starting = true
        serviceScope.launch {
            try {
                startWatch()
            } finally {
                starting = false
            }
        }
    }

    /**
     * The UI asks for a reload when the user presses refresh and may have
     * replaced cookies.json. Reconnect only when the file actually changed;
     * an unchanged file leaves the healthy watch alone.
     */
    private fun reloadWatchIfSessionChanged() {
        if (reloading || destroyed) return
        reloading = true
        serviceScope.launch {
            try {
                // Let an in-flight start finish so its client and cookies are
                // visible to this check.
                while (starting && !destroyed) {
                    delay(50)
                }

                val cookies = SessionStore(this@RealtimeService).readCookies()
                if (cookies == null) {
                    stopWatch()
                    keepStatusOnDestroy = true
                    RealtimeBus.setStatus(RealtimeStatus.NoSession)
                    updateForeground(getString(R.string.realtime_no_session))
                    Log.i(TAG, "no session file; stopping service")
                    stopSelf()
                    return@launch
                }

                if (client != null && cookies == sessionCookies) {
                    Log.i(TAG, "session unchanged; keeping watch")
                    return@launch
                }

                stopWatch()
                requestWatch()
            } finally {
                reloading = false
            }
        }
    }

    private fun startWatch() {
        if (destroyed) return

        val epoch = activeEpoch.incrementAndGet()
        RealtimeBus.setStatus(RealtimeStatus.Connecting)
        updateForeground(getString(R.string.realtime_connecting))

        val cookies = SessionStore(this).readCookies()
        if (cookies == null) {
            keepStatusOnDestroy = true
            RealtimeBus.setStatus(RealtimeStatus.NoSession)
            updateForeground(getString(R.string.realtime_no_session))
            Log.i(TAG, "no session file; stopping service")
            stopSelf()
            return
        }

        try {
            val goClient = GoClient(cookies)
            val callback = object : WatchCallback {
                override fun onEvent(eventJSON: String) {
                    if (activeEpoch.get() == epoch) {
                        handleEvent(eventJSON)
                    }
                }
            }
            watchCallback = callback
            sessionCookies = cookies
            client = goClient
            if (destroyed) {
                stopWatch()
                return
            }
            goClient.startWatch(callback)
            Log.i(TAG, "watch started")
        } catch (error: Throwable) {
            keepStatusOnDestroy = true
            RealtimeBus.setStatus(RealtimeStatus.Error)
            updateForeground(getString(R.string.realtime_error))
            Log.w(TAG, "watch failed to start: ${error.javaClass.simpleName}")
            stopWatch()
            stopSelf()
        }
    }

    private fun stopWatch() {
        activeEpoch.incrementAndGet()
        val active = client
        client = null
        watchCallback = null
        sessionCookies = null
        if (active != null) {
            runCatching { active.stopWatch() }
            runCatching { active.close() }
        }
    }

    private fun handleEvent(raw: String) {
        val event = RealtimeEvent.fromJson(raw) ?: return
        if (event.type != "seq_id") {
            Log.i(TAG, "event ${event.type} thread=${event.threadId}")
        }

        when (event.type) {
            "connected" -> {
                RealtimeBus.setStatus(RealtimeStatus.Connected)
                updateForeground(getString(R.string.realtime_connected))
            }

            "disconnected", "resnapshot" -> {
                RealtimeBus.setStatus(RealtimeStatus.Reconnecting)
                updateForeground(getString(R.string.realtime_reconnecting))
            }

            "auth.error" -> {
                RealtimeBus.setStatus(RealtimeStatus.Error)
                updateForeground(getString(R.string.realtime_error))
            }

            "watch.error" -> {
                keepStatusOnDestroy = true
                RealtimeBus.setStatus(RealtimeStatus.Error)
                updateForeground(getString(R.string.realtime_error))
                Log.w(TAG, "watch stopped with an error")
                stopSelf()
            }

            else -> when (event.type) {
                "message.new", "message.admin" -> {
                    RealtimeBus.publish(event)
                }

                "message.edit", "message.delete", "read.receipt",
                "reaction.add", "reaction.remove",
                -> {
                    RealtimeBus.publish(event)
                }
            }
        }
    }

    private fun startForegroundNow(text: String) {
        lastStatusText = text
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            buildStatusNotification(text),
            type,
        )
    }

    private fun updateForeground(text: String) {
        if (lastStatusText == text) return
        lastStatusText = text
        getSystemService(NotificationManager::class.java)
            .notify(FOREGROUND_ID, buildStatusNotification(text))
    }

    private fun buildStatusNotification(text: String): Notification =
        NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(appPendingIntent(0))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, getString(R.string.realtime_stop), stopPendingIntent())
            .build()

    private fun appPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, RealtimeService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun statusText(status: RealtimeStatus): String = when (status) {
        RealtimeStatus.Connected -> getString(R.string.realtime_connected)
        RealtimeStatus.Connecting -> getString(R.string.realtime_connecting)
        RealtimeStatus.Reconnecting -> getString(R.string.realtime_reconnecting)
        RealtimeStatus.NoSession -> getString(R.string.realtime_no_session)
        RealtimeStatus.Error -> getString(R.string.realtime_error)
        RealtimeStatus.Stopped -> getString(R.string.realtime_stopped)
    }

    companion object {
        private const val TAG = "RealtimeService"
        private const val ACTION_START = "com.goldrift.instadm.action.START_REALTIME"
        private const val ACTION_RESTART = "com.goldrift.instadm.action.RESTART_REALTIME"
        private const val ACTION_STOP = "com.goldrift.instadm.action.STOP_REALTIME"
        private const val FOREGROUND_ID = 1001
        private const val STOP_REQUEST_CODE = 1002

        fun start(context: Context) {
            val intent = Intent(context, RealtimeService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Recreates the watch only if cookies.json changed, so a replaced
         * session is picked up without reconnecting on every refresh tap.
         * Call this from a foreground UI action.
         */
        fun restart(context: Context) {
            val intent = Intent(context, RealtimeService::class.java).setAction(ACTION_RESTART)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
