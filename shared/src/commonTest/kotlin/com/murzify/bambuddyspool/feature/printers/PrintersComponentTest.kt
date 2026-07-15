package com.murzify.bambuddyspool.feature.printers

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.projections.EmptyCacheProjectionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintersComponentTest {
    @Test
    fun selectedPrinterRestoresButNoOperationStateExists() {
        val keeper = StateKeeperDispatcher()
        val first = component(keeper)
        first.accept(PrintersIntent.OpenPrinter(requireNotNull(PrinterId.from(7))))

        val restored = component(StateKeeperDispatcher(keeper.save()))

        assertEquals(7, restored.state.value.selectedPrinterId?.value)
        assertNull(PrintersReducer.reduce(restored.state.value, PrintersIntent.ClosePrinter).state.selectedPrinterId)
    }

    private fun component(stateKeeper: StateKeeperDispatcher) = PrintersComponent(
        DefaultComponentContext(
            LifecycleRegistry().apply {
                onCreate()
                onStart()
                onResume()
            },
            stateKeeper = stateKeeper
        ),
        EmptyCacheProjectionRepository
    )
}
