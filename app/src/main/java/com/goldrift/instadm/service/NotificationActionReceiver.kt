package com.goldrift.instadm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.goldrift.instadm.InstaDmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs the notification's reply and like actions. Android can deliver these to
 * a process that has nothing loaded, so everything comes from the intent and the
 * app's own singletons.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Best effort: Android kills the process at 90 s, so a slow network drops the action.
        val pending = goAsync()
        val app = context.applicationContext as? InstaDmApplication
        val action = intent.action
        val target = NotificationTarget.from(intent)
        if (app == null || action == null || target == null) {
            pending.finish()
            return
        }
        val replyText = if (action == ACTION_REPLY) {
            RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(EXTRA_REPLY_TEXT)
                ?.toString()
        } else {
            null
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationActions(
                    chatStore = app.chatStore,
                    repository = app.repository,
                    outbox = app.outbox,
                    settings = { app.settings.current },
                ).handle(action, target, replyText)
            } catch (throwable: Throwable) {
                Log.w(TAG, "notification action failed: ${throwable.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NotificationActionReceiver"
    }
}
