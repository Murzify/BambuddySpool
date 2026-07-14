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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RootComponentTest {
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

    private fun component(stateKeeper: StateKeeperDispatcher): RootComponent = RootComponent(
        componentContext = DefaultComponentContext(activeLifecycle(), stateKeeper = stateKeeper),
        nfcService = object : NfcService {
            override val isAvailable = true
            override suspend fun read(): NfcObservation = error("Not used by root navigation tests")
        },
        spoolProjectionRepository = EmptyCacheProjectionRepository
    )

    private fun activeLifecycle(): LifecycleRegistry = LifecycleRegistry().apply {
        onCreate()
        onStart()
        onResume()
    }
}
