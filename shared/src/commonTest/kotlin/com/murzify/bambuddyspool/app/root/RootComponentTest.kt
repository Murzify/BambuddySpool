package com.murzify.bambuddyspool.app.root

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.core.platform.NfcObservation
import com.murzify.bambuddyspool.core.platform.NfcService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RootComponentTest {
    @Test
    fun safeDestinationAndDetailRestoreButTransientWorkflowDoesNot() {
        val firstStateKeeper = StateKeeperDispatcher()
        val first = component(firstStateKeeper)

        first.accept(RootIntent.OpenDetail(RootDestination.Spools, 42))
        first.accept(RootIntent.ShowTransient(RootTransientWorkflow.Processing))

        val restored = component(StateKeeperDispatcher(firstStateKeeper.save()))

        assertEquals(RootDestination.Spools, restored.state.value.destination)
        assertEquals(42, restored.state.value.selectedDetailId)
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

    private fun component(stateKeeper: StateKeeperDispatcher): RootComponent = RootComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry(), stateKeeper = stateKeeper),
        nfcService = object : NfcService {
            override val isAvailable = true
            override suspend fun read(): NfcObservation = error("Not used by root navigation tests")
        }
    )
}
