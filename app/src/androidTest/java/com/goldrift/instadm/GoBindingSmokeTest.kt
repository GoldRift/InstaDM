package com.goldrift.instadm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.core.mobile.WatchCallback
import com.goldrift.instadm.data.GoClient
import com.goldrift.instadm.data.RealtimeEvent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime smoke test for the gomobile AAR.
 *
 * The test is skipped unless a cookie file exists. By default it reads
 * /sdcard/Android/data/com.goldrift.instadm/files/cookies.json; override the path
 * with:
 *
 *   adb shell mkdir -p /sdcard/Android/data/com.goldrift.instadm/files
 *   adb push ~/.config/instadm/cookies.json \
 *     /sdcard/Android/data/com.goldrift.instadm/files/cookies.json
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.goldrift.instadm.GoBindingSmokeTest
 */
@RunWith(AndroidJUnit4::class)
class GoBindingSmokeTest {
    @Test
    fun mailboxAndThreadLoad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val overridePath = InstrumentationRegistry.getArguments().getString("instadmCookiesPath")
        val cookieFile = if (overridePath != null) {
            File(overridePath)
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "cookies.json")
        }

        assumeTrue("Cookie file not found at ${cookieFile.absolutePath}", cookieFile.isFile)

        GoClient(cookieFile.readText()).use { client ->
            val mailbox = client.mailbox(90)
            val protocolVersion = mailbox.optString("protocol_version")
            assertTrue("protocol_version is empty", protocolVersion.isNotEmpty())

            val threads = mailbox.optJSONArray("threads")
            assertNotNull("mailbox has no threads array", threads)
            assertTrue("mailbox has no threads", threads!!.length() > 0)

            val firstThread = threads.getJSONObject(0)
            val threadId = firstThread.getString("id")
            val thread = client.thread(threadId, 5, "", 90)
            assertNotNull("thread has no messages array", thread.optJSONArray("messages"))

            // A thread longer than the page advertises the cursor the app uses
            // for older pages; the paged response must carry the same shape.
            if (thread.optBoolean("has_more_older")) {
                val cursor = thread.optString("oldest_cursor")
                assertTrue("has_more_older without oldest_cursor", cursor.isNotBlank())

                val page = client.thread(threadId, 5, cursor, 90)
                assertNotNull("older page has no messages array", page.optJSONArray("messages"))
                assertTrue("older page has no thread_id", page.optString("thread_id").isNotEmpty())
            }
        }
    }

    @Test
    fun watchDeliversConnectedEvent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val overridePath = InstrumentationRegistry.getArguments().getString("instadmCookiesPath")
        val cookieFile = if (overridePath != null) {
            File(overridePath)
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "cookies.json")
        }

        assumeTrue("Cookie file not found at ${cookieFile.absolutePath}", cookieFile.isFile)

        val connected = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        GoClient(cookieFile.readText()).use { client ->
            val callback = object : WatchCallback {
                override fun onEvent(eventJSON: String) {
                    val event = RealtimeEvent.fromJson(eventJSON) ?: return
                    when (event.type) {
                        "connected" -> connected.countDown()
                        "auth.error", "watch.error" -> {
                            failure.set("${event.type}: ${event.error}")
                            connected.countDown()
                        }
                    }
                }
            }
            client.startWatch(callback)
            try {
                assertTrue(
                    "watch did not connect within 60s",
                    connected.await(60, TimeUnit.SECONDS),
                )
                assertNull("watch failed: ${failure.get()}", failure.get())
            } finally {
                client.stopWatch()
            }
        }
    }
}
