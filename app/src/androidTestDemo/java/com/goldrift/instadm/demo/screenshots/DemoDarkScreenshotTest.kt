package com.goldrift.instadm.demo.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoDarkScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val device = ScreenshotSession.device()
    private val session = ScreenshotSession(compose, device)

    @Before
    fun setUp() {
        session.reset()
    }

    @Test
    fun captureDarkScreens() {
        session.capture("01-inbox-dark")

        session.tap("Maya Chen")
        session.awaitText("8pm?")
        session.capture("02-chat-dark")

        compose.onAllNodesWithContentDescription("Open media")[0].performClick()
        session.awaitDescription("Close")
        Thread.sleep(700)
        session.capture("03-media-viewer-dark")
        compose.onNodeWithContentDescription("Close").performClick()

        session.awaitText("8pm?")
        session.node("8pm?").performTouchInput { longClick() }
        compose.waitForIdle()
        Thread.sleep(350)
        session.capture("04-actions-dark")
        session.back()

        session.tapBack()
        session.awaitText("Design crew")
        session.tap("Design crew")
        session.awaitText("the new palette is 🔥")
        Thread.sleep(800)
        session.capture("05-group-dark")
    }
}
