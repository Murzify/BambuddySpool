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
import kotlin.test.assertSame

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
    fun metroBuildsScopedMockRootGraph() {
        val services = mockPlatformServices()
        val graph = createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            platformServices = services
        )

        assertSame(services.secureStorage, graph.applicationGraph.secureStorage)
        assertSame(services.nfc, graph.applicationGraph.nfcService)
        assertSame(graph.rootComponent, graph.rootComponent)
    }
}
