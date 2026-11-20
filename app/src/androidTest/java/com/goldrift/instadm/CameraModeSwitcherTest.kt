package com.goldrift.instadm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.ui.chat.CameraCaptureMode
import com.goldrift.instadm.ui.chat.CameraModeSwitcher
import com.goldrift.instadm.ui.theme.InstaDMTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented. Verifies Photo and Video button and label bounds do not shift
 * while pressed or after switching modes.
 */
@RunWith(AndroidJUnit4::class)
class CameraModeSwitcherTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun switchingModesKeepsPhotoAndVideoLabelsStationary() {
        val photoLabel = context.getString(R.string.camera_mode_photo)
        val videoLabel = context.getString(R.string.camera_mode_video)
        var mode by mutableStateOf(CameraCaptureMode.Photo)

        compose.mainClock.autoAdvance = false
        compose.setContent {
            InstaDMTheme(darkTheme = true, dynamicColor = false) {
                CameraModeSwitcher(
                    mode = mode,
                    onSelectMode = { mode = it },
                )
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }

        val initialPhotoButton = compose.onNodeWithText(photoLabel).getUnclippedBoundsInRoot()
        val initialVideoButton = compose.onNodeWithText(videoLabel).getUnclippedBoundsInRoot()
        val initialPhotoText =
            compose.onNodeWithText(photoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val initialVideoText =
            compose.onNodeWithText(videoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(initialPhotoButton.width, initialVideoButton.width)

        compose.onNodeWithText(videoLabel).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)

        assertEquals(initialPhotoButton, compose.onNodeWithText(photoLabel).getUnclippedBoundsInRoot())
        assertEquals(initialVideoButton, compose.onNodeWithText(videoLabel).getUnclippedBoundsInRoot())
        assertEquals(
            initialPhotoText,
            compose.onNodeWithText(photoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot(),
        )
        assertEquals(
            initialVideoText,
            compose.onNodeWithText(videoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot(),
        )

        compose.onNodeWithText(videoLabel).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        assertEquals(CameraCaptureMode.Video, mode)
        assertEquals(initialPhotoButton, compose.onNodeWithText(photoLabel).getUnclippedBoundsInRoot())
        assertEquals(initialVideoButton, compose.onNodeWithText(videoLabel).getUnclippedBoundsInRoot())
        assertEquals(
            initialPhotoText,
            compose.onNodeWithText(photoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot(),
        )
        assertEquals(
            initialVideoText,
            compose.onNodeWithText(videoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot(),
        )

        compose.mainClock.advanceTimeBy(300)
        assertEquals(initialPhotoText, compose.onNodeWithText(photoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot())
        assertEquals(initialVideoText, compose.onNodeWithText(videoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot())

        compose.onNodeWithText(photoLabel).performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(CameraCaptureMode.Photo, mode)
        assertEquals(initialPhotoText, compose.onNodeWithText(photoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot())
        assertEquals(initialVideoText, compose.onNodeWithText(videoLabel, useUnmergedTree = true).getUnclippedBoundsInRoot())
    }
}
