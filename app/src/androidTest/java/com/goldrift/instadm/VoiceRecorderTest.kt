package com.goldrift.instadm

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.VoiceRecorder
import com.goldrift.instadm.data.VoiceRecordingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceRecorderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var recorder: VoiceRecorder? = null

    @After
    fun tearDown() {
        recorder?.release()
    }

    @Test
    fun recordsPauseResumeAndStop() = runBlocking {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        val voiceRecorder = VoiceRecorder(context).also { recorder = it }
        assertTrue("recorder did not start", voiceRecorder.start())
        assertEquals(VoiceRecordingState.Recording, voiceRecorder.state)

        delay(500)
        voiceRecorder.pause()
        assertEquals(VoiceRecordingState.Paused, voiceRecorder.state)
        val pausedAt = voiceRecorder.elapsedMs
        delay(250)
        assertEquals("elapsed time advanced while paused", pausedAt, voiceRecorder.elapsedMs)

        voiceRecorder.resume()
        assertEquals(VoiceRecordingState.Recording, voiceRecorder.state)
        delay(600)

        val recorded = voiceRecorder.stop()
        assertNotNull("recording was discarded", recorded)
        requireNotNull(recorded)
        assertTrue("empty recording", recorded.file.length() > 0)
        assertEquals("audio/mp4", recorded.mimeType)
        assertEquals("m4a", recorded.file.extension)
        assertTrue("duration=${recorded.durationMs}", recorded.durationMs >= 900)
        assertTrue("waveform=${recorded.waveform.size}", recorded.waveform.isNotEmpty())
        assertTrue(recorded.waveform.all { it in 0f..1f })
        assertTrue(recorded.file.delete())
    }
}
