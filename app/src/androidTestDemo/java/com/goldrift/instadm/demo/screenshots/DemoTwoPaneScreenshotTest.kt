package com.goldrift.instadm.demo.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoTwoPaneScreenshotTest {
    @get:Rule(order = 0)
    val landscape = LandscapeRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val session = ScreenshotSession(compose, ScreenshotSession.device())

    @Before
    fun setUp() {
        session.reset()
    }

    @Test
    fun captureTwoPaneScreens() {
        session.awaitText("Choose a conversation to start messaging")
        session.capture("01-two-pane-empty-light")

        session.tap("Maya Chen")
        session.awaitText("8pm?")
        session.capture("02-two-pane-chat-light")
    }
}
