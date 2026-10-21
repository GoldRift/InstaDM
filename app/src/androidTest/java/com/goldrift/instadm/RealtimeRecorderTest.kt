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
