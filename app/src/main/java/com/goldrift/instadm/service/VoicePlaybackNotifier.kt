package com.goldrift.instadm.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.goldrift.instadm.MainActivity
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.NowPlaying

/**
 * Says a voice note is still playing while the app is out of sight, so the sound
 * has a source the user can tap. It carries no playback controls and keeps no
 * service alive: the note already plays from the app's process.
 */
internal object VoicePlaybackNotifier {
    private const val ID = 1002
    private const val REQUEST_CODE = 7001

    fun show(context: Context, note: NowPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val appContext = context.applicationContext
        NotificationChannels.ensure(appContext)
        // Tapping it lands in the chat the note belongs to.
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MessageNotifier.EXTRA_THREAD_ID, note.threadId)
        }
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.PLAYBACK)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(note.title)
            .setContentText(appContext.getString(R.string.voice_playback_notification))
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(ID, notification) }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(ID)
    }
}
