package com.murzify.bambuddyspool

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveTagMutationReaderLifecycleTest {

    @Test
    fun terminalMutationResultRetainsReaderAndSuppressesCallbacksUntilDismissed() {
        val lifecycle = LiveTagMutationReaderLifecycle()

        assertTrue(lifecycle.beginRead())
        assertTrue(lifecycle.isReaderEnabled)
        assertTrue(lifecycle.acceptTag())
        assertTrue(lifecycle.beginMutation())

        assertTrue(lifecycle.isReaderEnabled)
        assertFalse(lifecycle.acceptTag())
        assertFalse(lifecycle.beginRead())
        assertFalse(lifecycle.cancelRead())
        assertTrue(lifecycle.isReaderEnabled)

        assertFalse(lifecycle.finishMutation())
        assertTrue(lifecycle.isReaderEnabled)
        assertFalse(lifecycle.acceptTag())
        assertFalse(lifecycle.beginRead())
        assertTrue(lifecycle.cancelRead())
        assertFalse(lifecycle.isReaderEnabled)
        assertFalse(lifecycle.finishMutation())
    }

    @Test
    fun cancellationBeforePhysicalMutationConsumesTheReaderSession() {
        val lifecycle = LiveTagMutationReaderLifecycle()

        assertTrue(lifecycle.beginRead())
        assertTrue(lifecycle.cancelRead())

        assertFalse(lifecycle.isReaderEnabled)
        assertFalse(lifecycle.acceptTag())
        assertFalse(lifecycle.beginMutation())
    }

    @Test
    fun retryResumesAwaitingTagWithoutRedundantReaderToggle() {
        val lifecycle = LiveTagMutationReaderLifecycle()

        assertTrue(lifecycle.beginRead())
        assertTrue(lifecycle.beginMutation())

        assertFalse(lifecycle.finishMutation())
        assertTrue(lifecycle.isReaderEnabled)
        assertFalse(lifecycle.beginRead())
        assertTrue(lifecycle.acceptTag())
        assertTrue(lifecycle.cancelRead())
        assertFalse(lifecycle.isReaderEnabled)
    }

    @Test
    fun hostPauseConsumesTerminalReaderOwnershipExactlyOnce() {
        val lifecycle = LiveTagMutationReaderLifecycle()

        assertTrue(lifecycle.beginRead())
        assertTrue(lifecycle.beginMutation())
        assertFalse(lifecycle.finishMutation())

        assertTrue(lifecycle.pause())
        assertFalse(lifecycle.pause())
        assertFalse(lifecycle.finishMutation())
        assertFalse(lifecycle.isReaderEnabled)
    }
}
