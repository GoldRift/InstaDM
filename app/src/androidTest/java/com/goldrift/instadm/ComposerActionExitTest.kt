package com.goldrift.instadm

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.ui.chat.ChatScreen
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.VoicePlayer
import com.goldrift.instadm.ui.theme.InstaDMTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The composer's mic-to-send swap needs the real screen, so this is instrumented
 * and run by hand rather than as part of a connected run.
 *
 * The clock is frozen because the assertion belongs to one frame of the exit;
 * with it running the frame the test lands on is up to the machine.
 */
@RunWith(AndroidJUnit4::class)
class ComposerActionExitTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val voicePlayer = VoicePlayer(context)

    @Test
    fun leavingMicIsOutOfTheSemanticsTreeWhileItFadesOut() {
        val micLabel = context.getString(R.string.chat_mic_record)
        val sendLabel = context.getString(R.string.chat_send)
        val conversation = Conversation(
            id = "composer-action-exit",
            contact = Contact(id = "maya", name = "Maya Chen", initials = "MC"),
        )

        compose.mainClock.autoAdvance = false
        compose.setContent {
            InstaDMTheme(darkTheme = false, dynamicColor = false) {
                ChatScreen(
                    conversation = conversation,
                    onSend = { _, _ -> },
                    onBack = {},
                    voicePlayer = voicePlayer,
                )
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithContentDescription(micLabel).assertExists()

        compose.onNode(hasSetTextAction()).performTextInput("hi")
        compose.mainClock.advanceTimeByFrame()
        // A frame plus this slice is far inside the fast effects spring the exit
        // runs on, so the mic is still composed when it is asked for below.
        compose.mainClock.advanceTimeBy(50)

        compose.onNodeWithContentDescription(sendLabel).assertExists()
        compose.onNodeWithContentDescription(micLabel).assertDoesNotExist()
    }
}
