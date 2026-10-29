package com.goldrift.instadm.demo.screenshots

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.goldrift.instadm.MainActivity
import com.goldrift.instadm.demo.DemoApplication
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Forces landscape before the activity is created so the two-pane layout applies. */
class LandscapeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.setOrientationLeft()
                Thread.sleep(900)
                try {
                    base.evaluate()
                } finally {
                    device.unfreezeRotation()
                }
            }
        }
}

/** Small wrapper around Compose test + UiDevice for deterministic captures. */
internal class ScreenshotSession(
    private val rule: ComposeContentTestRule,
    private val device: UiDevice,
) {
    fun reset() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as DemoApplication
        runBlocking { app.resetDemoData() }
        awaitText("Maya Chen")
    }

    fun awaitText(text: String, timeoutMs: Long = 15_000) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun awaitDescription(description: String, timeoutMs: Long = 15_000) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun hasText(text: String): Boolean =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    fun awaitGone(text: String, timeoutMs: Long = 10_000) {
        rule.waitUntil(timeoutMs) { !hasText(text) }
    }

    fun tapBack() {
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
    }

    fun tap(text: String) {
        awaitText(text)
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    fun node(text: String): SemanticsNodeInteraction = rule.onNodeWithText(text)

    fun nodeWithDescription(description: String): SemanticsNodeInteraction =
        rule.onNodeWithContentDescription(description)

    fun back() {
        device.pressBack()
        rule.waitForIdle()
    }

    fun capture(name: String) {
        rule.waitForIdle()
        device.waitForIdle()
        Thread.sleep(450)
        // The test runner uninstalls the app afterwards, so copy each capture
        // out of the app sandbox into Downloads for the host to pull.
        device.executeShellCommand("mkdir -p $REMOTE_DIRECTORY")
        device.takeScreenshot(File("$REMOTE_DIRECTORY/$name.png"))
    }

    companion object {
        const val REMOTE_DIRECTORY = "/sdcard/Download/instadm-demo"

        fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }
}
