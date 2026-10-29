package com.goldrift.instadm.demo

import android.content.Context
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.ProtocolApi
import com.goldrift.instadm.data.RealtimeMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline stand-in for the Go protocol. Reads come from [DemoSeeder]; writes
 * are accepted and applied by the Room/outbox code paths exactly like a real
 * response would be.
 */
internal class DemoProtocol(
    private val context: Context,
    private val chatStore: ChatStore,
    private val seeder: DemoSeeder,
    private val scope: CoroutineScope,
) : ProtocolApi {
    private val olderPagesServed = mutableSetOf<String>()
    private val echoCounter = AtomicInteger()

    override suspend fun hasCookies(): Boolean {
        seeder.ensureSeeded()
        return true
    }

    override suspend fun signIn(cookiesJson: String): JSONObject = seeder.mailbox()

    override suspend fun loadMailbox(): JSONObject = seeder.mailbox()

    override suspend fun loadThread(threadId: String): JSONObject = seeder.thread(threadId)

    override suspend fun loadOlder(threadId: String, cursor: String): JSONObject {
        val page = seeder.olderPage(threadId)
        if (page != null && olderPagesServed.add(threadId)) return page
        return JSONObject()
            .put("thread_id", threadId)
            .put("messages", JSONArray())
            .put("has_more_older", false)
            .put("oldest_cursor", "")
    }

    override suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
    ): JSONObject {
        scheduleReply(threadId)
        return sentMessage(threadId, text, otid)
    }

    override suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject {
        scheduleReply(threadId)
        return JSONObject()
            .put("message_id", "demo_sent_${otid.ifBlank { echoCounter.incrementAndGet().toString() }}")
            .put("timestamp_ms", System.currentTimeMillis())
            .put("offline_threading_id", otid)
    }

    override suspend fun markRead(
        threadId: String,
        messageId: String,
        longThreadId: String,
        timestampMs: Long,
    ): JSONObject = ok()

    override suspend fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean,
    ): JSONObject = ok()

    override suspend fun edit(threadId: String, messageId: String, text: String): JSONObject =
        JSONObject().put("ok", true).put("text", text)

    override suspend fun unsend(threadId: String, messageId: String): JSONObject = ok()

    override suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ): File = withContext(Dispatchers.IO) {
        seeder.ensureSeeded()
        val name = mediaAsset(media)
        val target = File(context.cacheDir, "demo-media/$name")
        if (!target.isFile) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            context.assets.open("demo/$name").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        target
    }

    override suspend fun clearMediaCache() {
        withContext(Dispatchers.IO) {
            File(context.cacheDir, "demo-media").deleteRecursively()
        }
    }

    private fun sentMessage(threadId: String, text: String, otid: String): JSONObject = JSONObject()
        .put("message_id", "demo_sent_${otid.ifBlank { echoCounter.incrementAndGet().toString() }}")
        .put("timestamp_ms", System.currentTimeMillis())
        .put("text", text)
        .put("offline_threading_id", otid)

    private fun scheduleReply(threadId: String) {
        scope.launch {
            delay(1_400)
            val person = seeder.replyPerson(threadId) ?: return@launch
            val text = replyFor(threadId)
            chatStore.ingestRealtimeMessage(
                threadId,
                RealtimeMessage(
                    id = "demo_echo_${echoCounter.incrementAndGet()}",
                    offlineThreadingId = "demo_echo_otid_${echoCounter.get()}",
                    text = text,
                    senderName = person.name,
                    senderFbid = person.fbid,
                    isFromViewer = false,
                    timestampMs = System.currentTimeMillis(),
                    kind = "text",
                ),
            )
        }
    }

    private fun replyFor(threadId: String): String = when (threadId) {
        "demo_maya" -> "perfect — see you at 8!"
        "demo_design" -> "love it, let's do it"
        "demo_dev" -> "nice, send the rest when you can"
        "demo_sofia" -> "thank you! 💛"
        "demo_supper" -> "I'll add it to the list"
        else -> "got it!"
    }

    private fun mediaAsset(media: MessageMedia): String {
        if (media.isReel) return "clip.mp4"
        val candidates = listOf(
            media.url,
            media.previewUrl,
            media.fallbackUrl,
            media.previewFallbackUrl,
        )
        val asset = candidates.firstNotNullOfOrNull { value ->
            value.substringAfter("android_asset/demo/", "")
                .takeIf { it.isNotBlank() && !it.contains('/') }
        }
        return asset ?: when {
            media.isAudio -> "voice_note.m4a"
            media.isVideo -> "clip.mp4"
            media.isSticker -> "sticker_heart.webp"
            else -> "terrace.jpg"
        }
    }

    private fun ok(): JSONObject = JSONObject().put("ok", true)
}
