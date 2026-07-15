package com.murzify.bambuddyspool.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationTimingTest {
    @Test
    fun recorderUsesMonotonicElapsedTimeAndRetainsNothingOutsideTheInstance() {
        val clock = MutableClock(100)
        val timing = InMemoryAssignmentTiming(clock)

        clock.now = 650
        timing.mark(AssignmentTimingStage.ContextRefreshed)
        clock.now = 2_250
        timing.mark(AssignmentTimingStage.Verified)

        assertEquals(
            listOf(
                AssignmentTimingSample(AssignmentTimingStage.ContextRefreshed, 550),
                AssignmentTimingSample(AssignmentTimingStage.Verified, 2_150)
            ),
            timing.samples()
        )
        assertEquals(2_000, PerformanceBudgets.ASSIGNMENT_VERIFIED_MILLIS)
        assertEquals(1_000, PerformanceBudgets.NFC_COLD_LAUNCH_PROCESSING_MILLIS)
    }

    @Test
    fun degradationTablePreservesViewingAndFailsClosedForMutations() {
        val offline = GracefulDegradationPolicy.resolve(DegradationCondition.ServerOffline)
        val stale = GracefulDegradationPolicy.resolve(DegradationCondition.PrinterStatusStale)
        val disabled = GracefulDegradationPolicy.resolve(DegradationCondition.NfcDisabled)
        val cacheFailure = GracefulDegradationPolicy.resolve(DegradationCondition.RoomCacheFailure)

        assertTrue(offline.cachedViewing)
        assertTrue(offline.localNfcRead)
        assertFalse(offline.assignmentMutation)
        assertFalse(stale.assignmentMutation)
        assertTrue(disabled.manualAssignment)
        assertTrue(disabled.settingsAction)
        assertFalse(cacheFailure.cachedViewing)
        assertTrue(cacheFailure.rebuildCacheFromServer)
    }

    @Test
    fun coldLaunchHookIsOneShotAndUsesTheSameMonotonicSource() {
        val clock = MutableClock(500)
        val timing = ColdLaunchProcessingTiming(startedAtMillis = 200, clock = clock)

        timing.markProcessingComposed()
        clock.now = 900
        timing.markProcessingComposed()

        assertEquals(300, timing.elapsedMillisOrNull())
    }

    private class MutableClock(var now: Long) : MonotonicClock {
        override fun nowMillis(): Long = now
    }
}
