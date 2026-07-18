package com.murzify.bambuddyspool.app.root

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.platform.NfcObservation
import com.murzify.bambuddyspool.core.platform.NfcService
import com.murzify.bambuddyspool.core.projections.EmptyCacheProjectionRepository
import com.murzify.bambuddyspool.core.nfc.NfcReadClassification
import com.murzify.bambuddyspool.core.nfc.UnsupportedTagReason
import com.murzify.bambuddyspool.feature.tagmutation.LiveTagMutationBridge
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationOperation
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationRead
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationState
import com.murzify.bambuddyspool.core.domain.TagMutationOutcome
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RootComponentTest {
    @Test
    fun tagLinkRequiresALiveReadAndLeavesNoAuthorizationForRecreation() {
        val stateKeeper = StateKeeperDispatcher()
        val bridge = RecordingTagBridge()
        val root = component(stateKeeper, bridge)

        root.accept(RootIntent.StartTagLink(4))

        assertEquals(RootTransientWorkflow.TagMutation, root.state.value.transientWorkflow)
        assertEquals(requireNotNull(SpoolId.from(4)), root.state.value.pendingTagMutationSpoolId)
        assertEquals(1, bridge.beginReads)
        root.onTagMutationRead(TagMutationRead("tag-a", NfcReadClassification.Empty))
        assertEquals(TagMutationState.LinkReady::class, root.state.value.tagMutation::class)

        root.onTagMutationRead(
            TagMutationRead("tag-b", NfcReadClassification.UnsupportedTag(UnsupportedTagReason.NdefUnavailable))
        )
        assertEquals(TagMutationState.Failed::class, root.state.value.tagMutation::class)
        assertEquals(0, bridge.writes)

        val restored = component(StateKeeperDispatcher(stateKeeper.save()), RecordingTagBridge())
        assertNull(restored.state.value.pendingTagMutationSpoolId)
        assertEquals(TagMutationState.Idle, restored.state.value.tagMutation)
    }

    @Test
    fun tagRetryRequiresTheSameFingerprintBeforeItCanReturnToConfirmation() {
        val spool = requireNotNull(SpoolId.from(4))
        val failed = TagMutationState.Failed(
            reason = TagMutationFailure.PhysicalWriteUnverified,
            retry = TagMutationOperation.Link(spool, "bambuddy-spool://spool/4"),
            fingerprint = "tag-a"
        )
        val awaiting = RootReducer.reduce(
            RootState(pendingTagMutationSpoolId = spool, tagMutation = failed),
            RootIntent.RetryTagMutation
        ).state
        assertEquals(TagMutationState.AwaitingReadBeforeRetry::class, awaiting.tagMutation::class)

        val wrong = RootReducer.reduce(
            awaiting,
            RootIntent.TagMutationRead(TagMutationRead("tag-b", NfcReadClassification.Empty))
        ).state
        assertEquals(TagMutationState.Failed::class, wrong.tagMutation::class)
        assertEquals(
            TagMutationFailure.DifferentTagDetected,
            (wrong.tagMutation as TagMutationState.Failed).reason
        )
    }
    @Test
    fun independentDestinationHistoriesSurviveSwitchingAndRecreationWithoutTransientWorkflow() {
        val firstStateKeeper = StateKeeperDispatcher()
        val first = component(firstStateKeeper)

        first.accept(RootIntent.OpenDetail(RootDestination.Spools, 42))
        first.accept(RootIntent.OpenDetail(RootDestination.Spools, 99))
        first.accept(RootIntent.OpenDetail(RootDestination.Printers, 7))
        first.accept(RootIntent.Select(RootDestination.Spools))
        first.accept(RootIntent.ShowTransient(RootTransientWorkflow.Processing))

        assertEquals(
            listOf(DestinationRoute.List, DestinationRoute.Detail(42), DestinationRoute.Detail(99)),
            first.destinationHistory(RootDestination.Spools)
        )
        assertEquals(
            listOf(DestinationRoute.List, DestinationRoute.Detail(7)),
            first.destinationHistory(RootDestination.Printers)
        )

        val restored = component(StateKeeperDispatcher(firstStateKeeper.save()))

        assertEquals(RootDestination.Spools, restored.state.value.destination)
        assertEquals(
            listOf(DestinationRoute.List, DestinationRoute.Detail(42), DestinationRoute.Detail(99)),
            restored.destinationHistory(RootDestination.Spools)
        )
        assertEquals(
            listOf(DestinationRoute.List, DestinationRoute.Detail(7)),
            restored.destinationHistory(RootDestination.Printers)
        )
        assertNull(restored.state.value.transientWorkflow)
    }

    @Test
    fun homeStatusAndWorkflowAreExplicitNonNavigationState() {
        val initial = RootState()
        val update = RootReducer.reduce(
            initial,
            RootIntent.UpdateHomeStatus(HomeConnectionState.Stale, HomeNfcState.Disabled)
        )
        val workflow = RootReducer.reduce(update.state, RootIntent.ShowTransient(RootTransientWorkflow.Error))

        assertEquals(HomeConnectionState.Stale, workflow.state.connectionState)
        assertEquals(HomeNfcState.Disabled, workflow.state.nfcState)
        assertEquals(RootTransientWorkflow.Error, workflow.state.transientWorkflow)
    }

    @Test
    fun manualSelectionCreatesTransientSharedAssignmentIntentAndNeverRestoresIt() {
        val stateKeeper = StateKeeperDispatcher()
        val first = component(stateKeeper)
        val spool = requireNotNull(SpoolId.from(4))
        val slot = requireNotNull(SlotKey.from(1, 255, 0))
        val generation = requireNotNull(SnapshotGeneration.from(2))

        first.accept(RootIntent.StartManualAssignment(spool.value))
        first.accept(RootIntent.CreateManualAssignment(spool, slot, generation))

        assertEquals(RootDestination.Printers, first.state.value.destination)
        assertEquals(spool, first.state.value.assignmentIntent?.spoolId)
        assertEquals(slot, first.state.value.assignmentIntent?.slot)
        assertNull(first.state.value.pendingManualSpoolId)

        val restored = component(StateKeeperDispatcher(stateKeeper.save()))
        assertNull(restored.state.value.assignmentIntent)
        assertNull(restored.state.value.pendingManualSpoolId)
    }

    @Test
    fun nfcAssignmentKeepsTheLiveSessionWhileManualAssignmentClearsIt() {
        val first = component(StateKeeperDispatcher())
        val spool = requireNotNull(SpoolId.from(4))
        val slot = requireNotNull(SlotKey.from(1, 255, 0))
        val generation = requireNotNull(SnapshotGeneration.from(2))
        val observation = NfcObservation("0102", "bambuddy-spool://spool/4", 10L)

        first.accept(RootIntent.BeginNfcScan(observation))
        first.accept(
            RootIntent.StartAssignment(
                com.murzify.bambuddyspool.feature.assignment.AssignmentIntent.nfc(spool, slot, generation)
            )
        )
        assertEquals(1L, first.state.value.activeNfcSessionId?.value)
        assertEquals(observation, first.state.value.pendingNfcObservation)

        first.accept(
            RootIntent.StartAssignment(
                com.murzify.bambuddyspool.feature.assignment.AssignmentIntent.manual(spool, slot, generation)
            )
        )
        assertNull(first.state.value.activeNfcSessionId)
        assertNull(first.state.value.pendingNfcObservation)
    }

    @Test
    fun nfcScanStartsProcessingAndNeverRestoresItsPlatformNeutralObservation() {
        val stateKeeper = StateKeeperDispatcher()
        val first = component(stateKeeper)
        val observation = NfcObservation(
            fingerprint = "0102",
            payload = "bambuddy-spool://spool/4",
            monotonicTimestampMillis = 10L
        )

        first.accept(RootIntent.BeginNfcScan(observation))

        assertEquals(RootTransientWorkflow.Processing, first.state.value.transientWorkflow)
        assertEquals(observation, first.state.value.pendingNfcObservation)
        assertEquals(1L, first.state.value.activeNfcSessionId?.value)

        val restored = component(StateKeeperDispatcher(stateKeeper.save()))
        assertNull(restored.state.value.transientWorkflow)
        assertNull(restored.state.value.pendingNfcObservation)
        assertNull(restored.state.value.activeNfcSessionId)
    }

    private fun component(
        stateKeeper: StateKeeperDispatcher,
        bridge: LiveTagMutationBridge = RecordingTagBridge()
    ): RootComponent = RootComponent(
        componentContext = DefaultComponentContext(activeLifecycle(), stateKeeper = stateKeeper),
        nfcService = object : NfcService {
            override val isAvailable = true
            override suspend fun read(): NfcObservation = error("Not used by root navigation tests")
        },
        spoolProjectionRepository = EmptyCacheProjectionRepository,
        liveTagMutationBridge = bridge
    )

    private fun activeLifecycle(): LifecycleRegistry = LifecycleRegistry().apply {
        onCreate()
        onStart()
        onResume()
    }
}

private class RecordingTagBridge : LiveTagMutationBridge {
    var beginReads = 0
    var writes = 0
    override fun beginRead() { beginReads++ }
    override fun cancelRead() = Unit
    override suspend fun mutate(expectedFingerprint: String, operation: TagMutationOperation): TagMutationOutcome {
        writes++
        error("No test should authorize a physical write before confirmation.")
    }
}
