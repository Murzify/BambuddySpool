package com.murzify.bambuddyspool.core.nfc

import com.murzify.bambuddyspool.core.platform.NfcObservation

/** An opaque in-memory identity for one accepted physical NFC scan. */
data class NfcSessionId(val value: Long)

/** The irreversible point for scan supersession. */
enum class NfcSessionPhase {
    BeforePost,
    AfterPost,
    Success,
    Error
}

/** One active NFC workflow. It is transient operation state and must never be saved. */
data class ActiveNfcSession(val id: NfcSessionId, val observation: NfcObservation, val phase: NfcSessionPhase)

/** Ephemeral coordinator state. At most one active and one newest pending scan may exist. */
data class NfcScanCoordinationState(
    val active: ActiveNfcSession? = null,
    val latestPending: NfcObservation? = null,
    val lastAccepted: NfcObservation? = null,
    val nextSessionId: Long = 1L
)

sealed interface NfcScanCoordinationEvent {
    data class Scan(val observation: NfcObservation) : NfcScanCoordinationEvent
    data class PostStarted(val sessionId: NfcSessionId) : NfcScanCoordinationEvent
    data class Completed(val sessionId: NfcSessionId, val succeeded: Boolean) : NfcScanCoordinationEvent
}

/** A routing result; only [Start] permits a workflow to proceed toward a POST. */
sealed interface NfcScanCoordinationEffect {
    data class Start(val session: ActiveNfcSession) : NfcScanCoordinationEffect
    data class SuppressedDuplicate(val observation: NfcObservation) : NfcScanCoordinationEffect
    data class ReplacedBeforePost(val replaced: NfcSessionId, val started: ActiveNfcSession) :
        NfcScanCoordinationEffect

    data class QueuedAfterPost(val observation: NfcObservation) : NfcScanCoordinationEffect
    data object PostBoundaryAccepted : NfcScanCoordinationEffect
    data object IgnoredStaleCallback : NfcScanCoordinationEffect
}

data class NfcScanCoordinationReduction(val state: NfcScanCoordinationState, val effect: NfcScanCoordinationEffect?)

/**
 * Pure serialized NFC-session policy. The owner keeps [NfcScanCoordinationState] only in memory and applies events
 * in order. A scan that arrives after POST may replace the one pending slot, but can never start until verification
 * of the active server mutation completes; this prevents parallel assignment POSTs.
 */
object NfcScanCoordinator {
    const val DUPLICATE_WINDOW_MILLIS: Long = 1_000L

    fun reduce(state: NfcScanCoordinationState, event: NfcScanCoordinationEvent): NfcScanCoordinationReduction =
        when (event) {
            is NfcScanCoordinationEvent.Scan -> onScan(state, event.observation)
            is NfcScanCoordinationEvent.PostStarted -> onPostStarted(state, event.sessionId)
            is NfcScanCoordinationEvent.Completed -> onCompleted(state, event.sessionId, event.succeeded)
        }

    @Suppress("ReturnCount")
    private fun onScan(state: NfcScanCoordinationState, observation: NfcObservation): NfcScanCoordinationReduction {
        require(observation.fingerprint.isNotBlank()) { "NFC fingerprints must not be blank." }
        require(!observation.payload.isNullOrBlank()) { "Accepted NFC payloads must not be blank." }
        require(observation.monotonicTimestampMillis >= 0) { "NFC timestamps must be monotonic and non-negative." }
        if (state.lastAccepted.isDuplicateOf(observation)) {
            return NfcScanCoordinationReduction(state, NfcScanCoordinationEffect.SuppressedDuplicate(observation))
        }
        val acceptedState = state.copy(lastAccepted = observation)
        val active = acceptedState.active
        if (active == null || active.phase == NfcSessionPhase.Success || active.phase == NfcSessionPhase.Error) {
            return start(acceptedState.copy(latestPending = null), observation)
        }
        if (active.phase == NfcSessionPhase.BeforePost) {
            val replacement = start(acceptedState.copy(latestPending = null), observation)
            return NfcScanCoordinationReduction(
                replacement.state,
                NfcScanCoordinationEffect.ReplacedBeforePost(active.id, requireNotNull(replacement.effect).session())
            )
        }
        return NfcScanCoordinationReduction(
            acceptedState.copy(latestPending = observation),
            NfcScanCoordinationEffect.QueuedAfterPost(observation)
        )
    }

    private fun onPostStarted(state: NfcScanCoordinationState, sessionId: NfcSessionId): NfcScanCoordinationReduction {
        val active = state.active
        if (active?.id != sessionId || active.phase != NfcSessionPhase.BeforePost) {
            return NfcScanCoordinationReduction(state, NfcScanCoordinationEffect.IgnoredStaleCallback)
        }
        return NfcScanCoordinationReduction(
            state.copy(active = active.copy(phase = NfcSessionPhase.AfterPost)),
            NfcScanCoordinationEffect.PostBoundaryAccepted
        )
    }

    @Suppress("ReturnCount")
    private fun onCompleted(
        state: NfcScanCoordinationState,
        sessionId: NfcSessionId,
        succeeded: Boolean
    ): NfcScanCoordinationReduction {
        val active = state.active
        if (active?.id != sessionId) {
            return NfcScanCoordinationReduction(state, NfcScanCoordinationEffect.IgnoredStaleCallback)
        }
        val pending = state.latestPending
        if (active.phase == NfcSessionPhase.AfterPost && pending != null) {
            return start(state.copy(active = null, latestPending = null), pending)
        }
        return NfcScanCoordinationReduction(
            state.copy(active = active.copy(phase = if (succeeded) NfcSessionPhase.Success else NfcSessionPhase.Error)),
            null
        )
    }

    private fun start(state: NfcScanCoordinationState, observation: NfcObservation): NfcScanCoordinationReduction {
        check(state.nextSessionId > 0) { "NFC session identifiers must not overflow." }
        val session = ActiveNfcSession(NfcSessionId(state.nextSessionId), observation, NfcSessionPhase.BeforePost)
        return NfcScanCoordinationReduction(
            state.copy(active = session, nextSessionId = state.nextSessionId + 1),
            NfcScanCoordinationEffect.Start(session)
        )
    }

    private fun NfcObservation?.isDuplicateOf(next: NfcObservation): Boolean = this != null &&
        fingerprint == next.fingerprint &&
        next.monotonicTimestampMillis >= monotonicTimestampMillis &&
        next.monotonicTimestampMillis - monotonicTimestampMillis < DUPLICATE_WINDOW_MILLIS

    private fun NfcScanCoordinationEffect.session(): ActiveNfcSession = when (this) {
        is NfcScanCoordinationEffect.Start -> session
        else -> error("Only Start has an NFC session.")
    }
}
