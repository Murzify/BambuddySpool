package com.murzify.bambuddyspool.core.nfc

import com.murzify.bambuddyspool.core.platform.NfcObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NfcScanCoordinatorTest {
    @Test
    fun acceptedDuplicateFingerprintWithinOneSecondIsSuppressedUsingMonotonicTime() {
        val first = reduce(NfcScanCoordinationState(), scan("a", 1_000L))
        val duplicate = reduce(first.state, scan("a", 1_999L))
        val next = reduce(duplicate.state, scan("a", 2_000L))

        assertIs<NfcScanCoordinationEffect.Start>(first.effect)
        assertIs<NfcScanCoordinationEffect.SuppressedDuplicate>(duplicate.effect)
        assertIs<NfcScanCoordinationEffect.ReplacedBeforePost>(next.effect)
        assertEquals(2L, next.state.active?.id?.value)
    }

    @Test
    fun newScanBeforePostReplacesWorkflowAndStalePostCallbackCannotReopenIt() {
        val first = reduce(NfcScanCoordinationState(), scan("first", 1L))
        val firstId = requireNotNull(first.state.active).id
        val replacement = reduce(first.state, scan("second", 2L))
        val replacementId = requireNotNull(replacement.state.active).id
        val stalePost = reduce(replacement.state, NfcScanCoordinationEvent.PostStarted(firstId))

        assertIs<NfcScanCoordinationEffect.ReplacedBeforePost>(replacement.effect)
        assertEquals("second", requireNotNull(replacement.state.active).observation.fingerprint)
        assertEquals(NfcSessionPhase.BeforePost, requireNotNull(replacement.state.active).phase)
        assertEquals(replacement.state, stalePost.state)
        assertIs<NfcScanCoordinationEffect.IgnoredStaleCallback>(stalePost.effect)
        assertEquals(2L, replacementId.value)
    }

    @Test
    fun afterPostRetainsOnlyNewestPendingScanAndNeverStartsParallelPost() {
        val first = reduce(NfcScanCoordinationState(), scan("first", 1L))
        val firstId = requireNotNull(first.state.active).id
        val afterPost = reduce(first.state, NfcScanCoordinationEvent.PostStarted(firstId))
        val pendingOne = reduce(afterPost.state, scan("second", 2L))
        val pendingTwo = reduce(pendingOne.state, scan("third", 3L))

        assertIs<NfcScanCoordinationEffect.QueuedAfterPost>(pendingOne.effect)
        assertIs<NfcScanCoordinationEffect.QueuedAfterPost>(pendingTwo.effect)
        assertEquals(firstId, pendingTwo.state.active?.id)
        assertEquals(NfcSessionPhase.AfterPost, pendingTwo.state.active?.phase)
        assertEquals("third", pendingTwo.state.latestPending?.fingerprint)

        val completed = reduce(pendingTwo.state, NfcScanCoordinationEvent.Completed(firstId, succeeded = true))

        val start = assertIs<NfcScanCoordinationEffect.Start>(completed.effect)
        assertEquals("third", start.session.observation.fingerprint)
        assertEquals(2L, start.session.id.value)
        assertEquals(NfcSessionPhase.BeforePost, completed.state.active?.phase)
        assertNull(completed.state.latestPending)
    }

    @Test
    fun newScanReplacesSuccessImmediatelyWithProcessingSession() {
        val first = reduce(NfcScanCoordinationState(), scan("first", 1L))
        val firstId = requireNotNull(first.state.active).id
        val completed = reduce(first.state, NfcScanCoordinationEvent.Completed(firstId, succeeded = true))
        val next = reduce(completed.state, scan("next", 2L))

        assertEquals(NfcSessionPhase.Success, completed.state.active?.phase)
        val start = assertIs<NfcScanCoordinationEffect.Start>(next.effect)
        assertEquals("next", start.session.observation.fingerprint)
        assertEquals(NfcSessionPhase.BeforePost, next.state.active?.phase)
    }

    private fun scan(fingerprint: String, timestamp: Long): NfcScanCoordinationEvent.Scan =
        NfcScanCoordinationEvent.Scan(
            NfcObservation(
                fingerprint = fingerprint,
                payload = "bambuddy-spool://spool/1",
                monotonicTimestampMillis = timestamp
            )
        )

    private fun reduce(state: NfcScanCoordinationState, event: NfcScanCoordinationEvent): NfcScanCoordinationReduction =
        NfcScanCoordinator.reduce(state, event)
}
