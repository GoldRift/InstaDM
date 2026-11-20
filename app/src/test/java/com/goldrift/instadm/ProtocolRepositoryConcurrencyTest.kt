package com.goldrift.instadm

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.ProtocolBackend
import com.goldrift.instadm.data.ProtocolRepository
import com.goldrift.instadm.data.SessionBacking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProtocolRepositoryConcurrencyTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cachedMediaReturnsWithoutCreatingClientOrBlockingOnRpc() = runBlocking(Dispatchers.Default) {
        val cacheDir = tempFolder.newFolder("media")
        val cached = File(cacheDir, "t1_m1_0.jpg").apply { writeText("cached-image") }
        val session = FakeSessionBacking(cacheDir, cookies = """{"sessionid":"s"}""")
        val created = AtomicInteger(0)
        val repo = ProtocolRepository(session) {
            created.incrementAndGet()
            FakeProtocolBackend()
        }

        val result = repo.downloadMedia(
            threadId = "t1",
            messageId = "m1",
            media = MessageMedia(partIndex = 0, type = "image", url = ""),
        )

        assertEquals(cached.absolutePath, result.absolutePath)
        assertEquals(0, created.get())
    }

    @Test
    fun interactiveRpcDoesNotBlockBehindInFlightMediaUpload() = runBlocking(Dispatchers.Default) {
        val cacheDir = tempFolder.newFolder("media")
        val session = FakeSessionBacking(cacheDir, cookies = """{"sessionid":"s"}""")
        val uploadEntered = CountDownLatch(1)
        val releaseUpload = CountDownLatch(1)
        val repo = ProtocolRepository(session) {
            FakeProtocolBackend(
                onSendMedia = {
                    uploadEntered.countDown()
                    assertTrue("upload timed out", releaseUpload.await(5, TimeUnit.SECONDS))
                    JSONObject("""{"message_id":"mid.media"}""")
                },
                onSendText = {
                    JSONObject("""{"message_id":"mid.text"}""")
                },
            )
        }

        val upload = async {
            repo.sendMedia("t1", "video.mp4", "video/mp4", byteArrayOf(1, 2, 3), otid = "1")
        }
        assertTrue("upload did not start", uploadEntered.await(5, TimeUnit.SECONDS))

        val textResult = repo.sendText("t1", "hello", replyTo = "", otid = "2")
        assertEquals("mid.text", textResult.getString("message_id"))

        releaseUpload.countDown()
        assertEquals("mid.media", upload.await().getString("message_id"))
    }

    @Test
    fun interactiveRpcAndParallelDownloadsRunConcurrently() = runBlocking(Dispatchers.Default) {
        val cacheDir = tempFolder.newFolder("media")
        val session = FakeSessionBacking(cacheDir, cookies = """{"sessionid":"s"}""")
        val twoDownloadsRunning = CountDownLatch(2)
        val releaseDownloads = CountDownLatch(1)
        val repo = ProtocolRepository(session) {
            FakeProtocolBackend(
                onDownloadMedia = { destPath ->
                    twoDownloadsRunning.countDown()
                    assertTrue(
                        "downloads did not overlap",
                        twoDownloadsRunning.await(5, TimeUnit.SECONDS),
                    )
                    assertTrue("downloads timed out", releaseDownloads.await(5, TimeUnit.SECONDS))
                    File(destPath).writeText("payload")
                    JSONObject("""{"mime_type":"audio/mp4"}""")
                },
                onSendText = {
                    JSONObject("""{"message_id":"mid.fast"}""")
                },
            )
        }

        val dl1 = async {
            repo.downloadMedia("t1", "m1", MessageMedia(partIndex = 0, type = "audio", url = ""))
        }
        val dl2 = async {
            repo.downloadMedia("t1", "m2", MessageMedia(partIndex = 0, type = "audio", url = ""))
        }
        assertTrue(
            "two downloads were serialized",
            twoDownloadsRunning.await(5, TimeUnit.SECONDS),
        )

        val textResult = repo.sendText("t1", "unblocked", replyTo = "", otid = "3")
        assertEquals("mid.fast", textResult.getString("message_id"))

        releaseDownloads.countDown()
        assertEquals("t1_m1_0.m4a", dl1.await().name)
        assertEquals("t1_m2_0.m4a", dl2.await().name)
    }

    @Test
    fun concurrentDownloadsForSameMediaDeduplicateToOneTransfer() = runBlocking(Dispatchers.Default) {
        val cacheDir = tempFolder.newFolder("media")
        val session = FakeSessionBacking(cacheDir, cookies = """{"sessionid":"s"}""")
        val downloadCalls = AtomicInteger(0)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = ProtocolRepository(session) {
            FakeProtocolBackend(
                onDownloadMedia = { destPath ->
                    downloadCalls.incrementAndGet()
                    started.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    File(destPath).writeText("same-voice")
                    JSONObject("""{"mime_type":"audio/mp4"}""")
                },
            )
        }

        val first = async {
            repo.downloadMedia("t1", "m1", MessageMedia(partIndex = 0, type = "audio", url = ""))
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val second = async {
            repo.downloadMedia("t1", "m1", MessageMedia(partIndex = 0, type = "audio", url = ""))
        }
        release.countDown()

        val f1 = first.await()
        val f2 = second.await()
        assertEquals(f1.absolutePath, f2.absolutePath)
        assertEquals(1, downloadCalls.get())
    }

    @Test
    fun signOutPreventsInFlightRpcFromPersistingRotatedCookies() = runBlocking(Dispatchers.Default) {
        val cacheDir = tempFolder.newFolder("media")
        val session = FakeSessionBacking(cacheDir, cookies = """{"sessionid":"old"}""")
        val rpcEntered = CountDownLatch(1)
        val releaseRpc = CountDownLatch(1)
        val repo = ProtocolRepository(session) {
            FakeProtocolBackend(
                exportedCookies = """{"sessionid":"rotated"}""",
                onSendText = {
                    rpcEntered.countDown()
                    assertTrue(releaseRpc.await(5, TimeUnit.SECONDS))
                    JSONObject("""{"message_id":"mid.1"}""")
                },
            )
        }

        val inFlight = async { repo.sendText("t1", "hi", replyTo = "", otid = "1") }
        assertTrue(rpcEntered.await(5, TimeUnit.SECONDS))

        repo.signOut()
        releaseRpc.countDown()
        inFlight.await()

        assertEquals(null, session.readCookies())
    }

    private class FakeSessionBacking(
        override val mediaCacheDir: File,
        private var cookies: String? = null,
    ) : SessionBacking {
        override val cookieLocation: String = "/fake/cookies.json"

        @Synchronized
        override fun readCookies(): String? = cookies

        @Synchronized
        override fun saveCookies(cookiesJson: String) {
            cookies = cookiesJson
        }

        @Synchronized
        override fun clearCookies() {
            cookies = null
        }
    }

    private class FakeProtocolBackend(
        private val exportedCookies: String = "",
        private val onSendText: () -> JSONObject = { JSONObject() },
        private val onSendMedia: () -> JSONObject = { JSONObject() },
        private val onDownloadMedia: (destPath: String) -> JSONObject = { JSONObject() },
    ) : ProtocolBackend {
        override fun exportCookiesJson(): String = exportedCookies
        override fun mailbox(limit: Int): JSONObject = JSONObject()
        override fun mailboxPage(cursor: String, limit: Int): JSONObject = JSONObject()
        override fun thread(threadId: String, limit: Int, afterCursor: String): JSONObject = JSONObject()
        override fun searchUsers(query: String): JSONObject = JSONObject()
        override fun sendNewDM(recipientFbid: String, text: String, otid: String): JSONObject = JSONObject()
        override fun sendText(threadId: String, text: String, replyTo: String, otid: String): JSONObject =
            onSendText()
        override fun sendMedia(
            threadId: String,
            filename: String,
            mimeType: String,
            data: ByteArray,
            otid: String,
            voice: Boolean,
            waveformJson: String,
        ): JSONObject = onSendMedia()
        override fun markRead(
            threadId: String,
            messageId: String,
            longThreadId: String,
            timestampMs: Long,
        ): JSONObject = JSONObject()
        override fun react(threadId: String, messageId: String, emoji: String, remove: Boolean): JSONObject =
            JSONObject()
        override fun edit(threadId: String, messageId: String, text: String): JSONObject = JSONObject()
        override fun unsend(threadId: String, messageId: String): JSONObject = JSONObject()
        override fun downloadMedia(
            threadId: String,
            messageId: String,
            part: Int,
            destPath: String,
        ): JSONObject = onDownloadMedia(destPath)
        override fun downloadSharedMedia(
            mediaId: String,
            shortcode: String,
            destPath: String,
        ): JSONObject = onDownloadMedia(destPath)
        override fun close() = Unit
    }
}
