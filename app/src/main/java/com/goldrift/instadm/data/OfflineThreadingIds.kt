package com.goldrift.instadm.data

import java.util.concurrent.atomic.AtomicLong

/**
 * Generates Instagram-style offline threading IDs so an optimistic bubble can
 * be matched to the realtime echo of the same send.
 *
 * Mirrors `methods.GenerateEpochID` from the Go core: the high bits are the
 * current epoch millisecond, the next bits disambiguate IDs created in the same
 * millisecond, and the low bits keep the value odd.
 */
object OfflineThreadingIds {
    private val counter = AtomicLong()

    fun next(): String {
        val timestamp = System.currentTimeMillis()
        val sequence = counter.getAndIncrement() and 0x3FF
        return ((timestamp shl 22) or (sequence shl 12) or 42L).toString()
    }
}
