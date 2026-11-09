package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.withTypingSender
import com.goldrift.instadm.ui.chat.withoutTypingSender
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingSendersTest {
    @Test
    fun keepsArrivalOrderWithoutDuplicates() {
        val senders = emptyList<Long>()
            .withTypingSender(222)
            .withTypingSender(333)
            .withTypingSender(222)

        assertEquals(listOf(222L, 333L), senders)
    }

    @Test
    fun removesOnlyThatSender() {
        val senders = listOf(222L, 333L).withoutTypingSender(222)

        assertEquals(listOf(333L), senders)
    }

    @Test
    fun removingAnUnknownSenderLeavesTheListAlone() {
        assertEquals(listOf(222L), listOf(222L).withoutTypingSender(999))
    }
}
