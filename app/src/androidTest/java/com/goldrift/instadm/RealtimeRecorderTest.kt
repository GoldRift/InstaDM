package com.goldrift.instadm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.data.RealtimeRecorder
import com.goldrift.instadm.data.db.InstaDmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeRecorderTest {
    private lateinit var db: InstaDmDatabase
    private lateinit var store: ChatStore
    private lateinit var scope: CoroutineScope
    private lateinit var recorder: RealtimeRecorder

    private val now = 1_800_000_000_000L
    private var mailboxRefreshes = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InstaDmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ChatStore(db) { now }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        recorder = RealtimeRecorder(store, scope) { mailboxRefreshes++ }
        store.ingestMailbox(JSONObject(MAILBOX))
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun newMessageIsStoredAndUpdatesThePreview() = runBlocking {
        recorder.apply(
            RealtimeEvent(
                type = "message.new",
                threadId = "thread-1",
                message = incoming("message-2", "new message", 1_700_000_005_000),
            ),
        )

        val messages = store.observeThread("thread-1").first()
        assertEquals(1, messages.size)
        assertEquals("new message", messages.single().message.text)
        val thread = db.threads().thread("thread-1")!!
        assertEquals("message-2", thread.lastMessageId)
        assertEquals("new message", thread.lastMessageText)
        assertEquals(1_700_000_005_000L, thread.lastActivityMs)
        assertEquals(0, mailboxRefreshes)
    }

    @Test
    fun messageForAnUnknownThreadFetchesTheMailbox() = runBlocking {
        recorder.apply(
            RealtimeEvent(
                type = "message.new",
                threadId = "thread-9",
                message = incoming("message-9", "hello", 1_700_000_009_000),
            ),
        )

        assertEquals(1, mailboxRefreshes)
        assertEquals("hello", db.threads().thread("thread-9")!!.lastMessageText)
    }

    @Test
    fun editAndDeleteAreAppliedToTheStoredMessage() = runBlocking {
        recorder.apply(
            RealtimeEvent(
                type = "message.new",
                threadId = "thread-1",
                message = incoming("message-2", "original", 1_700_000_005_000),
            ),
        )
        recorder.apply(
            RealtimeEvent(
                type = "message.edit",
                threadId = "thread-1",
                message = incoming("message-2", "edited", 1_700_000_005_000),
            ),
        )
        assertEquals("edited", store.observeThread("thread-1").first().single().message.text)

        recorder.apply(
            RealtimeEvent(
                type = "message.delete",
                threadId = "thread-1",
                message = incoming("message-2", "", 1_700_000_005_000),
            ),
        )

        assertTrue(store.observeThread("thread-1").first().isEmpty())
        val thread = db.threads().thread("thread-1")!!
        assertEquals("", thread.lastMessageId)
        assertEquals("", thread.lastMessageText)
    }

    @Test
    fun reactionsAreStoredAndRemoved() = runBlocking {
        recorder.apply(
            RealtimeEvent(
                type = "message.new",
                threadId = "thread-1",
                message = incoming("message-2", "react to me", 1_700_000_005_000),
            ),
        )

        recorder.apply(
            RealtimeEvent(
                type = "reaction.add",
                threadId = "thread-1",
                message = incoming("message-2", "react to me", 1_700_000_005_000),
                reaction = "🔥",
                participantFbid = 222,
            ),
        )
        var reactions = store.observeThread("thread-1").first().single().reactions
        assertEquals(1, reactions.size)
        assertEquals("🔥", reactions.single().emoji)

        recorder.apply(
            RealtimeEvent(
                type = "reaction.remove",
                threadId = "thread-1",
                message = incoming("message-2", "react to me", 1_700_000_005_000),
                reaction = "🔥",
                participantFbid = 222,
            ),
        )
        reactions = store.observeThread("thread-1").first().single().reactions
        assertTrue(reactions.isEmpty())
    }

    @Test
    fun readReceiptsUpdateTheThreadAndMessages() = runBlocking {
        recorder.apply(
            RealtimeEvent(
                type = "message.new",
                threadId = "thread-1",
                message = incoming("message-2", "mine", 1_700_000_005_000, fromViewer = true),
            ),
        )

        recorder.apply(
            RealtimeEvent(
                type = "read.receipt",
                threadId = "thread-1",
                timestampMs = 1_700_000_005_000,
                fromViewer = false,
            ),
        )
        assertTrue(store.observeThread("thread-1").first().single().message.seen)

        recorder.apply(
            RealtimeEvent(
                type = "read.receipt",
                threadId = "thread-1",
                timestampMs = 1_700_000_006_000,
                fromViewer = true,
            ),
        )
        val thread = db.threads().thread("thread-1")!!
        assertEquals(1_700_000_006_000L, thread.readWatermarkMs)
        assertFalse(thread.markedUnread)
    }

    @Test
    fun incomingReactionFetchesTheActivityLogAndNotifies() = runBlocking {
        val notified = Channel<RealtimeEvent>(Channel.UNLIMITED)
        recorder = RealtimeRecorder(
            chatStore = store,
            scope = scope,
            onIncomingMessage = { notified.trySend(it) },
            syncThread = { threadId -> store.ingestThread(JSONObject(threadWithLog(threadId))) },
        ) { mailboxRefreshes++ }
        // A reaction row references the target message, so it must exist first.
        store.ingestRealtimeMessage(
            "thread-1",
            incoming("mid.target", "like me", 1_700_000_005_000, fromViewer = true),
        )

        recorder.apply(
            RealtimeEvent(
                type = "reaction.add",
                threadId = "thread-1",
                message = incoming("mid.target", "like me", 1_700_000_005_000),
                reaction = "❤",
                participantFbid = 222,
                timestampMs = 1_700_000_020_000,
            ),
        )

        val event = withTimeout(5_000) { notified.receive() }
        assertEquals("message.admin", event.type)
        assertEquals("mid.log", event.message?.id)
        assertEquals("reaction-log", event.message?.kind)
        assertEquals("Liked a message", event.message?.text)
        assertEquals(222L, event.message?.senderFbid)

        val thread = db.threads().thread("thread-1")!!
        assertEquals("Liked a message", thread.lastMessageText)
        assertEquals("reaction-log", thread.lastMessageKind)
        assertEquals("mid.log", thread.lastMessageId)
    }

    @Test
    fun viewerReactionDoesNotFetchOrNotify() = runBlocking {
        var fetches = 0
        val notified = Channel<RealtimeEvent>(Channel.UNLIMITED)
        recorder = RealtimeRecorder(
            chatStore = store,
            scope = scope,
            onIncomingMessage = { notified.trySend(it) },
            syncThread = { fetches++ },
        ) { mailboxRefreshes++ }
        store.ingestRealtimeMessage(
            "thread-1",
            incoming("mid.target", "like me", 1_700_000_005_000, fromViewer = true),
        )

        recorder.apply(
            RealtimeEvent(
                type = "reaction.add",
                threadId = "thread-1",
                message = incoming("mid.target", "like me", 1_700_000_005_000),
                reaction = "❤",
                participantFbid = 111,
                fromViewer = true,
                timestampMs = 1_700_000_020_000,
            ),
        )

        assertEquals(0, fetches)
        assertNull(notified.tryReceive().getOrNull())
    }

    private fun incoming(
        id: String,
        text: String,
        timestampMs: Long,
        fromViewer: Boolean = false,
    ) = RealtimeMessage(
        id = id,
        offlineThreadingId = "otid-$id",
        text = text,
        senderName = if (fromViewer) "Me" else "Test Participant",
        senderFbid = if (fromViewer) 111 else 222,
        isFromViewer = fromViewer,
        timestampMs = timestampMs,
        kind = "text",
    )

    private companion object {
        /** Newest page for thread-1 whose last entry is the reaction activity log. */
        fun threadWithLog(threadId: String): String = """
            {
              "thread": {
                "id": "$threadId",
                "thread_id": "long-thread-1",
                "thread_fbid": "thread-1",
                "title": "Test Participant"
              },
              "messages": [
                {
                  "id": "mid.target",
                  "sender_fbid": 111,
                  "sender_name": "Me",
                  "is_from_viewer": true,
                  "timestamp_ms": 1700000005000,
                  "kind": "text",
                  "text": "like me"
                },
                {
                  "id": "mid.log",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "is_from_viewer": false,
                  "timestamp_ms": 1700000020000,
                  "kind": "reaction-log",
                  "text": "Liked a message"
                }
              ],
              "oldest_cursor": "cursor-1",
              "has_more_older": false
            }
        """.trimIndent()

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
                  "last_activity_ms": 1700000000000,
                  "folder": "PRIMARY",
                  "system_folder": "INBOX"
                }
              ]
            }
        """.trimIndent()
    }
}
