package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.PrepareAction
import com.goldrift.instadm.ui.chat.VoicePrepareGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLifecycleTest {

    @Test
    fun readyStartsPlaybackWhenNotInterrupted() {
        val gate = VoicePrepareGate()
        val gen = gate.beginPrepare()
        assertFalse(gate.isPrepared)

        val action = gate.onPrepared(gen)
        assertEquals(PrepareAction.Ready(startPlayback = true, seekToMs = null), action)
        assertTrue(gate.isPrepared)
    }

    @Test
    fun staleCallbackAfterStopOrSwitchIsRejected() {
        val gate = VoicePrepareGate()
        val first = gate.beginPrepare()
        val second = gate.beginPrepare()

        assertEquals(PrepareAction.Stale, gate.onPrepared(first))
        assertFalse(gate.isPrepared)

        gate.invalidate()
        assertEquals(PrepareAction.Stale, gate.onPrepared(second))
        assertFalse(gate.isPrepared)
    }

    @Test
    fun pauseAndSeekDuringPrepareAreAppliedWhenReady() {
        val gate = VoicePrepareGate()
        val gen = gate.beginPrepare()

        gate.onPause()
        gate.onSeek(4_200)
        assertEquals(PrepareAction.Ready(startPlayback = false, seekToMs = 4_200), gate.onPrepared(gen))
    }

    @Test
    fun resumeAfterPauseDuringPrepareStartsWhenReady() {
        val gate = VoicePrepareGate()
        val gen = gate.beginPrepare()

        gate.onPause()
        gate.onResume()
        assertEquals(PrepareAction.Ready(startPlayback = true, seekToMs = null), gate.onPrepared(gen))
    }
}
