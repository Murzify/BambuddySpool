package com.murzify.bambuddyspool

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.murzify.bambuddyspool.app.bootstrap.createRootGraph
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.app.root.HomeNfcState
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.core.platform.IosMockServiceUnavailableException
import com.murzify.bambuddyspool.core.platform.iosMockPlatformServices
import com.murzify.bambuddyspool.core.security.SecretValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class IosMockShellTest {
    @Test
    fun sharedRootStartsAndNavigatesWithIosMocks() {
        val graph = createRootGraph(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            platformServices = iosMockPlatformServices()
        )

        assertEquals(HomeNfcState.Unavailable, graph.rootComponent.state.value.nfcState)
        RootDestination.entries.forEach { destination ->
            graph.rootComponent.accept(RootIntent.Select(destination))
            assertEquals(destination, graph.rootComponent.state.value.destination)
        }
    }

    @Test
    fun unsupportedIosServicesAreFailClosedAndDoNotStoreCredentials() = runTest {
        val services = iosMockPlatformServices()

        assertFalse(services.nfc.isAvailable)
        assertFalse(services.secureStorage.hasToken())
        assertFailsWith<IosMockServiceUnavailableException> { services.nfc.read() }
        assertFailsWith<IosMockServiceUnavailableException> {
            services.secureStorage.replaceToken(requireNotNull(SecretValue.fromPlainText("test-token")))
        }
        assertFalse(services.secureStorage.hasToken())
        assertFalse(services.clipboard.copyRedacted("redacted"))
    }
}
