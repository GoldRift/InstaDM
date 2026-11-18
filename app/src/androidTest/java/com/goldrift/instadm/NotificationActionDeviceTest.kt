package com.goldrift.instadm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.AppSettings
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.REACTION_LOG_KIND
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.service.ACTION_LIKE
import com.goldrift.instadm.service.ACTION_REPLY
import com.goldrift.instadm.service.NotificationActions
import com.goldrift.instadm.service.NotificationTarget
import com.goldrift.instadm.service.canLikeNotification
import com.goldrift.instadm.service.notificationMessageActions
import com.goldrift.instadm.ui.chat.ADMIN_KIND
import com.goldrift.instadm.ui.chat.CALL_KIND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationActionDeviceTest {
    private lateinit var db: InstaDmDatabase
    private lateinit var store: ChatStore
    private lateinit var scope: CoroutineScope
    private lateinit var outbox: Outbox
    private lateinit var dir: File
    private lateinit var protocol: FakeProtocol
    private lateinit var actions: NotificationActions
    private var settings = AppSettings()

    private val now = 1_800_000_000_000L
    private val tsOldest = 1_700_000_500_000L
    private val tsNewest = 1_700_000_600_000L
    private val tsPending = 1_700_000_700_000L
    private val tsFallback = 1_700_000_800_000L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InstaDmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ChatStore(db) { now }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = File(context.cacheDir, "notification-actions-${UUID.randomUUID()}")
        outbox = Outbox(
            db = db,
            chatStore = store,
            sender = NeverSends,
            mediaReader = MediaReader(context),
            outboxDir = dir,
            mediaCacheDir = dir,
            scope = scope,
            clock = { now },
            retryBackoffMs = 10,
        )
        protocol = FakeProtocol(cookies = true)
        settings = AppSettings()
        actions = NotificationActions(
            chatStore = store,
            repository = protocol,
            outbox = outbox,
            settings = { settings },
        )

        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        store.ingestThread(JSONObject(PENDING_THREAD))
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
        db.close()
    }

    @Test
    fun replyIsQueuedInTheOutboxAndMarksTheThreadRead() = runBlocking {
        val before = watermark("thread-1")
        actions.handle(ACTION_REPLY, target("mid.2", tsNewest), "on my way")

        val rows = store.observeThread("thread-1").first()
        val pending = rows.single { it.message.status == MessageStatus.PENDING }
        assertEquals("on my way", pending.message.text)
        assertTrue("outbox row missing after reply", db.outbox().nextPending() != null)
        assertEquals(tsNewest, watermark("thread-1"))
        assertTrue("watermark must not already be current before the reply", before < tsNewest)
    }

    @Test
    fun replyPublishesTheReceiptEvenWhenReceiptsAreOff() = runBlocking {
        settings = AppSettings(sendReadReceipts = false)

        actions.handle(ACTION_REPLY, target("mid.2", tsNewest), "on my way")

        assertEquals(tsNewest, watermark("thread-1"))
        assertEquals("mid.2", protocol.reads.single().messageId)
    }

    @Test
    fun replyOnAMessageRequestOnlyClearsTheBadge() = runBlocking {
        actions.handle(ACTION_REPLY, target("mid.pending", tsPending, PENDING_THREAD_ID), "hi")

        assertEquals(tsPending, watermark(PENDING_THREAD_ID))
        assertTrue(protocol.reads.isEmpty())
    }

    @Test
    fun replyPublishesTheNewestLineItAnswers() = runBlocking {
        actions.handle(ACTION_REPLY, target("mid.2", tsNewest), "on my way")

        val read = protocol.reads.single()
        assertEquals("thread-1", read.threadId)
        assertEquals("mid.2", read.messageId)
        assertEquals("long-thread-1", read.longThreadId)
        assertEquals(tsNewest, read.timestampMs)
    }

    @Test
    fun likeReactsWithTheDoubleTapEmojiAndStoresTheViewerReaction() = runBlocking {
        actions.handle(ACTION_LIKE, target("mid.2", tsNewest))

        val react = protocol.reacts.single()
        assertEquals("thread-1", react.threadId)
        assertEquals("mid.2", react.messageId)
        assertEquals(settings.doubleTapEmoji, react.emoji)
        assertFalse(react.remove)

        val reaction = store.observeThread("thread-1").first()
            .single { it.message.id == "mid.2" }
            .reactions
            .single()
        assertEquals(settings.doubleTapEmoji, reaction.emoji)
        assertTrue(reaction.isFromViewer)
    }

    @Test
    fun likeIsOfferedBeforeReplyAndNoticesOfferOnlyReply() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val like = context.getString(R.string.notification_action_like)
        val reply = context.getString(R.string.notification_action_reply)

        fun titles(kind: String): List<String> = notificationMessageActions(
            context = context,
            target = target("mid.2", tsNewest),
            requestCode = 1,
            canLike = canLikeNotification(kind),
        ).map { it.title.toString() }

        assertEquals(listOf(like, reply), titles("text"))
        assertEquals(listOf(reply), titles(REACTION_LOG_KIND))
        assertEquals(listOf(reply), titles(ADMIN_KIND))
        assertEquals(listOf(reply), titles(CALL_KIND))
    }

    @Test
    fun tombstonedMessagesAreNotLikedAndAreNotReadByAReply() = runBlocking {
        store.tombstoneMessage("mid.2")

        actions.handle(ACTION_LIKE, target("mid.2", tsNewest))
        assertTrue(protocol.reacts.isEmpty())

        actions.handle(ACTION_REPLY, target("mid.2", tsNewest), "on my way")
        assertEquals(tsNewest, watermark("thread-1"))
        assertTrue("deleted message must not carry a receipt", protocol.reads.isEmpty())
    }

    @Test
    fun replyOnAMissingMessageRowFallsBackToTheIntentTimestamp() = runBlocking {
        actions.handle(ACTION_REPLY, target("mid.gone", tsFallback), "on my way")

        assertEquals(tsFallback, watermark("thread-1"))
        val read = protocol.reads.single()
        assertEquals("mid.gone", read.messageId)
        assertEquals(tsFallback, read.timestampMs)
    }

    private fun target(
        messageId: String,
        timestampMs: Long,
        threadId: String = "thread-1",
    ) = NotificationTarget(
        threadId = threadId,
        longThreadId = longThreadId(threadId),
        messageId = messageId,
        timestampMs = timestampMs,
    )

    private fun longThreadId(threadId: String): String = when (threadId) {
        PENDING_THREAD_ID -> "long-thread-pending"
        else -> "long-thread-1"
    }

    private suspend fun watermark(threadId: String): Long =
        store.threadWithParticipants(threadId)!!.thread.readWatermarkMs

    private companion object {
        const val PENDING_THREAD_ID = "thread-pending"

        val MAILBOX = """
            {
              "viewer": { "ig_id": "viewer-ig", "username": "me", "name": "Me", "fbid": 111 },
              "threads": [
                {
                  "id": "thread-1",
                  "thread_fbid": "thread-1",
                  "thread_id": "long-thread-1",
                  "thread_key": 222,
                  "title": "Test Participant",
                  "participants": [
                    { "ig_id": "ig-other", "fbid": 222, "name": "Test Participant" }
                  ],
                  "last_activity_ms": 1700000600000,
                  "folder": "PRIMARY",
                  "system_folder": "INBOX"
                },
                {
                  "id": "thread-pending",
                  "thread_fbid": "thread-pending",
                  "thread_id": "long-thread-pending",
                  "thread_key": 333,
                  "title": "Request Sender",
                  "participants": [
                    { "ig_id": "ig-request", "fbid": 333, "name": "Request Sender" }
                  ],
                  "last_activity_ms": 1700000700000,
                  "folder": "PENDING",
                  "system_folder": "PENDING"
                }
              ]
            }
        """.trimIndent()

        val THREAD = """
            {
              "thread_id": "thread-1",
              "messages": [
                {
                  "id": "mid.1",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 1700000500000,
                  "kind": "text",
                  "text": "hello",
                  "is_from_viewer": false
                },
                {
                  "id": "mid.2",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 1700000600000,
                  "kind": "text",
                  "text": "newest",
                  "is_from_viewer": false
                }
              ]
            }
        """.trimIndent()

        val PENDING_THREAD = """
            {
              "thread_id": "thread-pending",
              "messages": [
                {
                  "id": "mid.pending",
                  "sender_fbid": 333,
                  "sender_name": "Request Sender",
                  "timestamp_ms": 1700000700000,
                  "kind": "text",
                  "text": "let me in",
                  "is_from_viewer": false
                }
              ]
            }
        """.trimIndent()
    }
}
