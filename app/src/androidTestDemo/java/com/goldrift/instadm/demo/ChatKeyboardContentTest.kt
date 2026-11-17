package com.goldrift.instadm.demo

import android.content.ClipDescription
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Seam: the chat composer's keyboard handoff. Only a device can supply the
 * InputConnection an IME uses, so this lives here rather than at a JVM seam.
 */
@RunWith(AndroidJUnit4::class)
class ChatKeyboardContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun composerAcceptsAndConsumesAKeyboardGif() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as DemoApplication
        runBlocking { app.resetForScreenshots() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Maya Chen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Maya Chen").performClick()
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()

        var editorInfo: EditorInfo? = null
        var connection: InputConnection? = null
        compose.activity.runOnUiThread {
            val info = EditorInfo()
            editorInfo = info
            connection = compose.activity.window.decorView
                .findFocus()
                ?.onCreateInputConnection(info)
        }

        val info = editorInfo
        val input = connection
        assertNotNull(info)
        assertNotNull(input)
        assertTrue(EditorInfoCompat.getContentMimeTypes(info!!).contains("image/*"))

        val context = compose.activity
        val directory = File(context.cacheDir, "outgoing").apply { mkdirs() }
        val file = File(directory, "keyboard-test.gif").apply {
            writeBytes(
                byteArrayOf(
                    0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00,
                    0x01, 0x00, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
                    0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21,
                    0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
                    0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01,
                    0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
                ),
            )
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val content = InputContentInfoCompat(
            uri,
            ClipDescription("keyboard gif", arrayOf("image/gif")),
            null,
        )

        assertTrue(
            InputConnectionCompat.commitContent(
                input!!,
                info!!,
                content,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            ),
        )
    }
}
