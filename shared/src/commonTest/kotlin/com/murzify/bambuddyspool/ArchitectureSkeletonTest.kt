package com.murzify.bambuddyspool

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.app.bootstrap.createRootGraph
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.app.root.RootReducer
import com.murzify.bambuddyspool.app.root.RootState
import com.murzify.bambuddyspool.core.platform.mockPlatformServices
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureSkeletonTest {

    @Test
    fun reducerIsPureAndEmitsNavigationEffect() {
        val initial = RootState()
        val first = RootReducer.reduce(initial, RootIntent.Select(RootDestination.Printers))
        val second = RootReducer.reduce(initial, RootIntent.Select(RootDestination.Printers))

        assertEquals(first, second)
        assertEquals(RootDestination.Printers, first.state.destination)
        assertEquals(RootDestination.Home, initial.destination)
    }

    @Test
    fun metroBuildsScopedRootGraphWithTestNfc() {
        val services = mockPlatformServices()
        val graph = createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            nfcService = services.nfc
        )

        assertEquals(services.nfc.isAvailable, graph.rootComponent.state.value.nfcState.isAvailable())
    }
}

private fun com.murzify.bambuddyspool.app.root.HomeNfcState.isAvailable(): Boolean =
    this == com.murzify.bambuddyspool.app.root.HomeNfcState.Available
