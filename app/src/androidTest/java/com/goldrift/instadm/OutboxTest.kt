package com.goldrift.instadm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.OutboxSender
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.data.db.OutboxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OutboxTest {
    private lateinit var db: InstaDmDatabase
    private lateinit var store: ChatStore
    private lateinit var scope: CoroutineScope
    private lateinit var outbox: Outbox
    private lateinit var dir: File
    private lateinit var sender: FakeSender

    private val now = 1_800_000_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InstaDmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ChatStore(db) { now }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sender = FakeSender()
        dir = File(context.cacheDir, "outbox-test-${UUID.randomUUID()}")
        outbox = Outbox(
            db = db,
            chatStore = store,
            sender = sender,
            mediaReader = MediaReader(context),
            outboxDir = dir,
            scope = scope,
            clock = { now },
            retryBackoffMs = 10,
        )
        store.ingestMailbox(JSONObject(MAILBOX))
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
        db.close()
    }

    @Test
    fun queuedTextIsShownPendingThenReplacedByTheServerMessage() = runBlocking {
        val localId = outbox.enqueueText("thread-1", "hello")

        val pending = store.observeThread("thread-1").first().single()
        assertEquals(localId, pending.message.id)
        assertEquals(MessageStatus.PENDING, pending.message.status)
        assertEquals("hello", pending.message.text)
        assertEquals("hello", db.threads().thread("thread-1")!!.lastMessageText)

        val pendingEntry = db.outbox().nextPending()
        assertTrue("outbox row missing after enqueue", pendingEntry != null)
        outbox.drain()

        assertEquals("send was not called", 1, sender.textCalls)
        assertTrue("outbox row was not cleared", db.outbox().nextPending() == null)
        val remote = db.messages().message("mid.1")
        val local = db.messages().message(localId)
        assertTrue("remote=$remote local=$local", remote != null)
        val after = store.observeThread("thread-1").first()
        assertEquals("mid.1", after.single().message.id)
        assertEquals(MessageStatus.SENT, after.single().message.status)
        assertEquals("hello", after.single().message.text)
        assertEquals("mid.1", db.threads().thread("thread-1")!!.lastMessageId)
        assertEquals(1, sender.textCalls)
        assertTrue(db.outbox().nextPending() == null)
    }

    @Test
    fun failedSendStaysFailedAndRetryUsesTheSameOtid() = runBlocking {
        sender.failuresLeft = 10
        val localId = outbox.enqueueText("thread-1", "eventually sent")
        outbox.drain()

        val failed = withTimeout(5_000) {
            store.observeThread("thread-1").first { rows ->
                rows.singleOrNull()?.message?.status == MessageStatus.FAILED
            }
        }
        assertEquals(localId, failed.single().message.id)

        val entry = db.outbox().byLocalMessage(localId)!!
        assertEquals(3, entry.attempts)
        val otid = entry.otid

        sender.failuresLeft = 0
        outbox.retry(localId)
        val pendingEntry = db.outbox().nextPending()
        assertTrue("outbox row missing after enqueue", pendingEntry != null)
        outbox.drain()

        assertEquals("send was not called", 1, sender.textCalls)
        assertTrue("outbox row was not cleared", db.outbox().nextPending() == null)
        val remote = db.messages().message("mid.1")
        val local = db.messages().message(localId)
        assertTrue("remote=$remote local=$local", remote != null)
        val after = store.observeThread("thread-1").first()
        assertEquals("mid.1", after.single().message.id)
        assertEquals(MessageStatus.SENT, after.single().message.status)
        assertEquals(otid, sender.lastOtid)
    }

    @Test
    fun interruptedSendIsRetriedAfterRestart() = runBlocking {
        val localId = outbox.enqueueText("thread-1", "after crash")
        val entry = db.outbox().byLocalMessage(localId)!!
        db.outbox().updateState(entry.id, OutboxState.SENDING, 0, "", now)

        outbox.start()
        delay(500)

        assertEquals("send was not called", 1, sender.textCalls)
        val remote = db.messages().message("mid.1")
        val local = db.messages().message(localId)
        assertTrue("remote=$remote local=$local", remote != null)
        val rows = store.observeThread("thread-1").first()
        assertEquals("rows=${rows.map { it.message.id }}", "mid.1", rows.single().message.id)
        assertEquals(1, sender.textCalls)
    }

    private class FakeSender : OutboxSender {
        var failuresLeft = 0
        var textCalls = 0
        var lastOtid = ""

        override suspend fun sendText(
            threadId: String,
            text: String,
            replyTo: String,
            otid: String,
        ): JSONObject {
            if (failuresLeft > 0) {
                failuresLeft--
                error("network unavailable")
            }
            textCalls++
            lastOtid = otid
            return JSONObject()
                .put("message_id", "mid.$textCalls")
                .put("offline_threading_id", otid)
                .put("timestamp_ms", 1_800_000_100_000)
                .put("text", text)
        }

        override suspend fun sendMedia(
            threadId: String,
            fileName: String,
            mimeType: String,
            data: ByteArray,
            otid: String,
        ): JSONObject = error("media is not used in these tests")
    }

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
