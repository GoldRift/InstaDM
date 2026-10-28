package com.goldrift.instadm.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.goldrift.instadm.MainActivity
import com.goldrift.instadm.R
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.ConversationParticipant
import com.goldrift.instadm.ui.chat.ConversationPreview
import com.goldrift.instadm.ui.chat.UNKNOWN_CONVERSATION_TITLE
import com.goldrift.instadm.ui.chat.initialsOf
import com.goldrift.instadm.ui.chat.label
import com.goldrift.instadm.ui.chat.render
import com.goldrift.instadm.ui.chat.toConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Posts one stacked notification per conversation. It is process-scoped and
 * consumes incoming events on its own IO coroutine so avatar downloads and
 * notification IPC never block the realtime recorder.
 */
class MessageNotifier(
    context: Context,
    private val chatStore: ChatStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val events = Channel<RealtimeEvent>(Channel.UNLIMITED)
    private val history = ThreadNotificationHistory()
    private val avatars = LruCache<String, Bitmap>(AVATAR_CACHE_ENTRIES)
    private val viewerPerson = Person.Builder()
        .setName(appContext.getString(R.string.chat_you))
        .build()

    init {
        scope.launch {
            for (event in events) {
                runCatching { post(event) }
                    .onFailure { Log.w(TAG, "notification failed: ${it.javaClass.simpleName}") }
            }
        }
    }

    fun enqueue(event: RealtimeEvent) {
        events.trySend(event)
    }

    private suspend fun post(event: RealtimeEvent) {
        val message = event.message ?: return
        val threadId = event.threadId
        if (threadId.isBlank() || message.id.isBlank() || message.isFromViewer) return
        if (AppVisibility.isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationChannels.ensure(appContext)

        val conversation = chatStore.threadWithParticipants(threadId)?.toConversation(previous = null)
        val isGroup = conversation?.isGroup == true
        val sender = senderFor(message, conversation, isGroup)
        val notificationId = messageNotificationId(threadId)
        val entries = history.record(
            threadId = threadId,
            message = NotifiedMessage(
                id = message.id,
                text = bodyFor(message),
                timestampMs = message.timestampMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                senderFbid = message.senderFbid,
                senderName = sender.name,
                senderAvatarUrl = sender.avatarUrl,
            ),
            append = isNotificationActive(notificationId),
        ).sortedBy { it.timestampMs }

        val title = titleFor(conversation, isGroup, entries.last().senderName)
        val avatar = conversationAvatar(conversation, isGroup, entries.last().senderName)
        publishConversationShortcut(threadId, title, avatar)

        val style = NotificationCompat.MessagingStyle(viewerPerson)
        style.setGroupConversation(isGroup)
        style.setConversationTitle(bold(title))

        val persons = mutableMapOf<String, Person>()
        for (entry in entries) {
            val key = personKey(entry)
            val person = persons[key] ?: personFor(entry).also { persons[key] = it }
            style.addMessage(entry.text, entry.timestampMs, person)
        }

        val builder = NotificationCompat.Builder(appContext, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatar)
            .setShortcutId(conversationShortcutId(threadId))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setWhen(entries.last().timestampMs)
            .setNumber(entries.size)
            .setContentIntent(contentIntent(threadId, notificationId))

        manager.notify(notificationId, builder.build())
    }

    /**
     * Conversation shortcuts give Android the per-thread icon used in the
     * conversation header; without one the header falls back to the app icon.
     * The same ID is set on the notification so the system pairs them.
     */
    private fun publishConversationShortcut(threadId: String, title: String, avatar: Bitmap) {
        val shortcutId = conversationShortcutId(threadId)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val person = Person.Builder()
            .setName(bold(title))
            .setKey(shortcutId)
            .setIcon(IconCompat.createWithBitmap(avatar))
            .build()
        val shortcut = ShortcutInfoCompat.Builder(appContext, shortcutId)
            .setShortLabel(title.take(10))
            .setLongLabel(title.take(25))
            .setIcon(IconCompat.createWithBitmap(avatar))
            .setIntent(intent)
            .setPerson(person)
            .setIsConversation()
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(appContext, shortcut) }
            .onFailure { Log.w(TAG, "shortcut failed: ${it.javaClass.simpleName}") }
    }

    private fun conversationShortcutId(threadId: String): String = "instadm.thread.$threadId"

    private fun titleFor(
        conversation: Conversation?,
        isGroup: Boolean,
        senderName: String,
    ): String = when {
        isGroup -> conversation?.contact?.name
            ?.takeIf { it.isNotBlank() && it != UNKNOWN_CONVERSATION_TITLE }
            ?: appContext.getString(R.string.notification_group_chat)
        else -> senderName.ifBlank { appContext.getString(R.string.chat_someone) }
    }

    private fun senderFor(
        message: RealtimeMessage,
        conversation: Conversation?,
        isGroup: Boolean,
    ): Sender {
        val participant = if (isGroup) participantFor(message, conversation) else null
        val name = message.senderName
            .ifBlank { participant?.name.orEmpty() }
            .ifBlank { if (isGroup) "" else conversation?.contact?.name.orEmpty() }
            .ifBlank { appContext.getString(R.string.chat_someone) }
        val avatarUrl = if (isGroup) {
            participant?.avatarUrl.orEmpty()
        } else {
            conversation?.contact?.avatarUrl.orEmpty()
        }
        return Sender(name, avatarUrl)
    }

    private fun participantFor(
        message: RealtimeMessage,
        conversation: Conversation?,
    ): ConversationParticipant? {
        val participants = conversation?.participants ?: return null
        if (message.senderFbid != 0L) {
            participants.firstOrNull { it.fbid == message.senderFbid }?.let { return it }
        }
        if (message.senderName.isBlank()) return null
        return participants.firstOrNull { it.name == message.senderName }
    }

    private fun bodyFor(message: RealtimeMessage): String =
        ConversationPreview(
            kind = message.kind,
            text = message.text,
            fromViewer = false,
        )
            .label(isGroup = false)
            ?.render { id, args -> appContext.getString(id, *args.toTypedArray()) }
            .orEmpty()
            .ifBlank { appContext.getString(R.string.notification_message) }
            .take(MAX_BODY_CHARS)

    private suspend fun personFor(message: NotifiedMessage): Person {
        val name = message.senderName.ifBlank { appContext.getString(R.string.chat_someone) }
        val avatar = loadAvatar(message.senderAvatarUrl, PERSON_AVATAR_SIZE_PX)
            ?: initialsAvatar(initialsOf(name), personKey(message), PERSON_AVATAR_SIZE_PX)
        return Person.Builder()
            .setName(bold(name))
            .setKey(personKey(message))
            .setIcon(IconCompat.createWithBitmap(avatar))
            .build()
    }

    private fun personKey(message: NotifiedMessage): String =
        message.senderFbid.takeIf { it != 0L }?.toString() ?: message.senderName

    private fun bold(text: CharSequence): CharSequence =
        SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    /**
     * The conversation picture shown by the notification. DMs use the contact's
     * photo, groups use their custom image or a collage of members, and the
     * initials placeholder keeps the app icon out of the slot when nothing
     * loads.
     */
    private suspend fun conversationAvatar(
        conversation: Conversation?,
        isGroup: Boolean,
        fallbackName: String,
    ): Bitmap {
        if (conversation == null) {
            return initialsAvatar(
                initials = initialsOf(fallbackName),
                seed = fallbackName,
                size = CONVERSATION_AVATAR_SIZE_PX,
            )
        }

        if (!isGroup) {
            val url = conversation.contact.avatarUrl.ifBlank {
                conversation.participants.firstOrNull { it.avatarUrl.isNotBlank() }
                    ?.avatarUrl
                    .orEmpty()
            }
            loadAvatar(url, CONVERSATION_AVATAR_SIZE_PX)?.let { return circleCrop(it) }
            return initialsAvatar(
                initials = conversation.contact.initials,
                seed = conversation.contact.id,
                size = CONVERSATION_AVATAR_SIZE_PX,
            )
        }

        loadAvatar(conversation.contact.avatarUrl, CONVERSATION_AVATAR_SIZE_PX)
            ?.let { return circleCrop(it) }
        groupCollage(conversation.participants.map { it.avatarUrl })?.let { return it }
        return initialsAvatar(
            initials = conversation.contact.initials,
            seed = conversation.contact.id,
            size = CONVERSATION_AVATAR_SIZE_PX,
        )
    }

    private suspend fun groupCollage(urls: List<String>): Bitmap? {
        val unique = urls.filter { it.isNotBlank() }.distinct().take(4)
        if (unique.isEmpty()) return null
        val photos = coroutineScope {
            unique.map { url -> async { loadAvatar(url, CONVERSATION_AVATAR_SIZE_PX) } }.awaitAll()
        }.filterNotNull()
        if (photos.isEmpty()) return null
        if (photos.size == 1) return circleCrop(photos.first())

        val size = CONVERSATION_AVATAR_SIZE_PX
        val edge = size.toFloat()
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        when (photos.size) {
            2 -> {
                drawCropped(canvas, photos[0], 0f, 0f, edge / 2f, edge)
                drawCropped(canvas, photos[1], edge / 2f, 0f, edge, edge)
            }
            3 -> {
                drawCropped(canvas, photos[0], 0f, 0f, edge / 2f, edge)
                drawCropped(canvas, photos[1], edge / 2f, 0f, edge, edge / 2f)
                drawCropped(canvas, photos[2], edge / 2f, edge / 2f, edge, edge)
            }
            else -> {
                drawCropped(canvas, photos[0], 0f, 0f, edge / 2f, edge / 2f)
                drawCropped(canvas, photos[1], edge / 2f, 0f, edge, edge / 2f)
                drawCropped(canvas, photos[2], 0f, edge / 2f, edge / 2f, edge)
                drawCropped(canvas, photos[3], edge / 2f, edge / 2f, edge, edge)
            }
        }
        return circleCrop(square)
    }

    private fun circleCrop(source: Bitmap): Bitmap {
        val size = CONVERSATION_AVATAR_SIZE_PX
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawCropped(
            Canvas(square),
            source,
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
        )
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val radius = size / 2f
        canvas.clipPath(Path().apply { addCircle(radius, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(square, 0f, 0f, null)
        return output
    }

    private fun drawCropped(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return
        val scale = maxOf(width / bitmap.width, height / bitmap.height)
        val sourceWidth = (width / scale).toInt().coerceIn(1, bitmap.width)
        val sourceHeight = (height / scale).toInt().coerceIn(1, bitmap.height)
        val sourceLeft = (bitmap.width - sourceWidth) / 2
        val sourceTop = (bitmap.height - sourceHeight) / 2
        canvas.drawBitmap(
            bitmap,
            Rect(sourceLeft, sourceTop, sourceLeft + sourceWidth, sourceTop + sourceHeight),
            RectF(left, top, right, bottom),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun initialsAvatar(initials: String, seed: String, size: Int): Bitmap {
        val label = initials.trim().ifBlank { "?" }.take(2)
        val cacheKey = "initials:$size:$seed:$label"
        avatars.get(cacheKey)?.let { return it }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AVATAR_COLORS[Math.floorMod(seed.hashCode(), AVATAR_COLORS.size)]
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, background)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ON_AVATAR_COLOR
            textAlign = Paint.Align.CENTER
            textSize = size * 0.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val baseline = radius - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2f
        canvas.drawText(label, radius, baseline, text)

        avatars.put(cacheKey, bitmap)
        return bitmap
    }

    private suspend fun loadAvatar(url: String, size: Int): Bitmap? {
        if (url.isBlank()) return null
        val cacheKey = "$size:$url"
        avatars.get(cacheKey)?.let { return it }
        val request = ImageRequest.Builder(appContext)
            .data(url)
            .size(size)
            .allowHardware(false)
            .build()
        val bitmap = runCatching {
            withTimeoutOrNull(AVATAR_TIMEOUT_MS) {
                (appContext.imageLoader.execute(request) as? SuccessResult)
                    ?.drawable
                    ?.toBitmap()
            }
        }.getOrNull()
        if (bitmap != null) avatars.put(cacheKey, bitmap)
        return bitmap
    }

    // A swiped notification leaves no platform entry; starting a fresh stack
    // keeps dismissed lines from reappearing on the next message.
    private fun isNotificationActive(notificationId: Int): Boolean = runCatching {
        manager.activeNotifications.any { it.id == notificationId }
    }.getOrDefault(false)

    private fun contentIntent(threadId: String, requestCode: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class Sender(val name: String, val avatarUrl: String)

    companion object {
        const val EXTRA_THREAD_ID = "com.goldrift.instadm.extra.THREAD_ID"

        private const val TAG = "MessageNotifier"
        private const val PERSON_AVATAR_SIZE_PX = 72
        private const val CONVERSATION_AVATAR_SIZE_PX = 128
        private const val AVATAR_CACHE_ENTRIES = 32
        private const val AVATAR_TIMEOUT_MS = 4_000L
        private const val MAX_BODY_CHARS = 500

        private val AVATAR_COLORS = intArrayOf(
            0xFFFA903E.toInt(),
            0xFFFE63B8.toInt(),
            0xFFEE675C.toInt(),
            0xFFC58AF9.toInt(),
            0xFF8AB4F8.toInt(),
            0xFF81C995.toInt(),
            0xFFFDD663.toInt(),
            0xFF78D9EC.toInt(),
        )
        private val ON_AVATAR_COLOR = 0xFF202124.toInt()
    }
}
