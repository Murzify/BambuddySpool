package com.murzify.bambuddyspool

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveTagMutationReaderLifecycleTest {

    @Test
    fun physicalMutationRetainsReaderUntilWriterAndReadBackComplete() {
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

        assertTrue(lifecycle.finishMutation())
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
    fun failedPhysicalMutationStillDisablesReaderExactlyOnce() {
        val lifecycle = LiveTagMutationReaderLifecycle()

        assertTrue(lifecycle.beginRead())
        assertTrue(lifecycle.beginMutation())

        assertTrue(lifecycle.finishMutation())
        assertFalse(lifecycle.finishMutation())
        assertFalse(lifecycle.isReaderEnabled)
    }
}
