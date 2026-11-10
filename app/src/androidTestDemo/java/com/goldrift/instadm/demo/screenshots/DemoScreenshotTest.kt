package com.goldrift.instadm.demo.screenshots

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val device = ScreenshotSession.device()
    private val session = ScreenshotSession(compose, device)

    @Before
    fun setUp() {
        session.reset()
    }

    @Test
    fun captureLightScreens() {
        session.capture("01-inbox-light")

        session.tap("Maya Chen")
        session.awaitText("8pm?")
        session.awaitText("Maya pinned a message.")
        session.capture("02-chat-light")

        compose.onAllNodesWithContentDescription("Open media")[0].performClick()
        session.awaitDescription("Close")
        Thread.sleep(700)
        session.capture("03-media-viewer-light")
        compose.onNodeWithContentDescription("Close").performClick()

        longPress("8pm?")
        session.capture("04-actions-light")
        session.back()

        compose.onNodeWithContentDescription("Play voice message").performClick()
        Thread.sleep(700)
        session.capture("05-voice-light")
        if (compose.onAllNodesWithContentDescription("Pause voice message")
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            compose.onNodeWithContentDescription("Pause voice message").performClick()
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("see you at 8")
        compose.onNodeWithContentDescription("Send message").performClick()
        session.awaitText("perfect — see you at 8!")
        hideKeyboardAndReturnToChat()
        session.capture("06-echo-light")

        longPress("see you at 8")
        session.tap("Edit")
        session.capture("07-edit-light")
        session.tap("Cancel")
        session.awaitGone("Edit message")

        longPress("see you at 8")
        session.tap("Unsend")
        session.capture("08-unsend-light")
        session.tap("Cancel")
        session.awaitGone("Unsend message?")

        session.tapBack()
        session.awaitText("Sofia Rossi")
        session.tap("Sofia Rossi")
        session.awaitText("design notes for the launch")
        session.capture("09-link-and-voice-light")

        session.tapBack()
        session.awaitText("Design crew")
        session.tap("Design crew")
        session.awaitText("the new palette is 🔥")
        session.awaitText("Maya added Sofia to the group.")
        session.awaitText("Dev started a video call.")
        Thread.sleep(800)
        session.capture("10-group-light")

        session.tapBack()
        session.awaitText("Dev Patel")
        session.tap("Dev Patel")
        session.awaitText("more coming tonight")
        Thread.sleep(800)
        session.capture("11-media-cards-light")

        session.tapBack()
        session.awaitDescription("Search")
        session.nodeWithDescription("Search").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("ma")
        session.awaitText("@maya.chen")
        device.pressBack()
        compose.waitForIdle()
        Thread.sleep(600)
        session.capture("12-search-light")

        device.pressBack()
        compose.waitForIdle()
        session.awaitText("Sofia Rossi")
        session.nodeWithDescription("Your profile photo").performClick()
        session.awaitText("Log out")
        Thread.sleep(600)
        session.capture("13-account-menu-light")

        session.tap("Settings")
        session.awaitText("Theme")
        Thread.sleep(600)
        session.capture("14-settings-light")
    }

    private fun hideKeyboardAndReturnToChat() {
        device.pressBack()
        compose.waitForIdle()
        val inChat = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        if (!inChat) {
            session.tap("Maya Chen")
            session.awaitText("8pm?")
        }
    }

    private fun longPress(text: String) {
        session.awaitText(text)
        session.node(text).performTouchInput { longClick() }
        compose.waitForIdle()
        Thread.sleep(350)
    }
}
