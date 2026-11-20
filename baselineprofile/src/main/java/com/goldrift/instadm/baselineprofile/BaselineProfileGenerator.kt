package com.goldrift.instadm.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Baseline and Startup Profiles covering cold launch, inbox scroll,
 * opening a thread, and transcript scroll.
 *
 * Run on a connected API 33+ device (or rooted API 28+ emulator):
 *   ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        rule.collect(
            packageName = targetPackageName(),
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            waitForInboxOrIdle()
        }
    }

    @Test
    fun generateUserJourneys() {
        rule.collect(
            packageName = targetPackageName(),
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            scrollInboxAndOpenThread()
        }
    }

    private fun MacrobenchmarkScope.waitForInboxOrIdle() {
        device.wait(Until.hasObject(By.res("inbox_list")), WAIT_TIMEOUT_MS)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollInboxAndOpenThread() {
        val inboxList = device.wait(Until.findObject(By.res("inbox_list")), WAIT_TIMEOUT_MS)
            ?: return
        inboxList.setGestureMargin(device.displayWidth / 5)
        inboxList.fling(Direction.DOWN)
        device.waitForIdle()
        inboxList.fling(Direction.UP)
        device.waitForIdle()

        val firstRow = device.wait(Until.findObject(By.res("conversation_row")), WAIT_TIMEOUT_MS)
            ?: return
        firstRow.click()
        device.waitForIdle()

        val messageList = device.wait(Until.findObject(By.res("message_list")), WAIT_TIMEOUT_MS)
            ?: return
        messageList.setGestureMargin(device.displayWidth / 5)
        // Reversed LazyColumn: fling UP scrolls into older messages, DOWN returns to newest.
        messageList.fling(Direction.UP)
        device.waitForIdle()
        messageList.fling(Direction.DOWN)
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L

        fun targetPackageName(): String =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: "com.goldrift.instadm"
    }
}
