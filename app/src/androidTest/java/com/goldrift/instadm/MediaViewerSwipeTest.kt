package com.goldrift.instadm

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.ui.chat.MediaViewerItem
import com.goldrift.instadm.ui.chat.MediaViewerOverlay
import com.goldrift.instadm.ui.chat.MediaViewerRequest
import com.goldrift.instadm.ui.chat.VoicePlayer
import com.goldrift.instadm.ui.theme.InstaDMTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaViewerSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val voicePlayer = VoicePlayer(context)

    @Test
    fun horizontalSwipeAdvancesToNextChatMediaAndUpdatesMetadata() {
        val file1 = File(context.cacheDir, "viewer-swipe-1.jpg").apply { writeBytes(ByteArray(0)) }
        val file2 = File(context.cacheDir, "viewer-swipe-2.jpg").apply { writeBytes(ByteArray(0)) }
        val request = MediaViewerRequest(
            threadId = "thread-1",
            items = listOf(
                MediaViewerItem(
                    messageId = "mid.1",
                    media = MessageMedia(type = "image", url = "https://cdn.test/1.jpg", partIndex = 0),
                    senderName = "You",
                    timestamp = "1:00 PM",
                ),
                MediaViewerItem(
                    messageId = "mid.2",
                    media = MessageMedia(type = "image", url = "https://cdn.test/2.jpg", partIndex = 0),
                    senderName = "Dev Patel",
                    timestamp = "1:05 PM",
                ),
            ),
            initialIndex = 0,
        )

        compose.setContent {
            InstaDMTheme(darkTheme = true, dynamicColor = false) {
                MediaViewerOverlay(
                    request = request,
                    loadMedia = { _, messageId, _ -> if (messageId == "mid.1") file1 else file2 },
                    voicePlayer = voicePlayer,
                    dynamicColor = false,
                    onDismiss = {},
                )
            }
        }

        // Controls stay hidden until the media is tapped.
        compose.onNodeWithText("You").assertDoesNotExist()

        compose.onRoot().performTouchInput { click() }
        compose.waitForIdle()

        compose.onNodeWithText("You").assertExists()
        compose.onNodeWithText("1:00 PM").assertExists()
        compose.onNodeWithText("1 / 2").assertDoesNotExist()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Dev Patel").assertExists()
        compose.onNodeWithText("1:05 PM").assertExists()
        compose.onNodeWithText("2 / 2").assertDoesNotExist()

        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithText("You").assertExists()
        compose.onNodeWithText("1:00 PM").assertExists()
    }
}
