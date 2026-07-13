package com.murzify.bambuddyspool.core.projections

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.ConnectionSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class DefaultPrinterLifecycleTest {

    @Test
    fun exactlyOnePrinterBecomesDefaultAutomatically() = runBlocking {
        val settings = FakeConnectionSettingsStore(ConnectionSettings.Empty)
        val lifecycle = DefaultPrinterLifecycle(settings)

        val result = lifecycle.reconcile(listOf(printerId(1)))

        assertEquals(DefaultPrinterLifecycleResult.AutoSelected(printerId(1)), result)
        assertEquals(printerId(1), settings.current.defaultPrinterId)
    }

    @Test
    fun multiplePrintersDeferDefaultSelection() = runBlocking {
        val settings = FakeConnectionSettingsStore(ConnectionSettings.Empty)
        val lifecycle = DefaultPrinterLifecycle(settings)

        val result = lifecycle.reconcile(listOf(printerId(1), printerId(2)))

        assertEquals(DefaultPrinterLifecycleResult.Unchanged(null), result)
        assertEquals(null, settings.current.defaultPrinterId)
    }

    @Test
    fun deletedDefaultTransitionsToOnlyRemainingPrinter() = runBlocking {
        val settings = FakeConnectionSettingsStore(
            ConnectionSettings.Empty.copy(defaultPrinterId = printerId(7))
        )
        val lifecycle = DefaultPrinterLifecycle(settings)

        val result = lifecycle.reconcile(listOf(printerId(2)))

        assertEquals(
            DefaultPrinterLifecycleResult.ReplacedMissing(previous = printerId(7), current = printerId(2)),
            result
        )
        assertEquals(printerId(2), settings.current.defaultPrinterId)
    }

    @Test
    fun deletedDefaultClearsWhenSeveralPrintersRemain() = runBlocking {
        val settings = FakeConnectionSettingsStore(
            ConnectionSettings.Empty.copy(defaultPrinterId = printerId(7))
        )
        val lifecycle = DefaultPrinterLifecycle(settings)

        val result = lifecycle.reconcile(listOf(printerId(2), printerId(3)))

        val cleared = assertIs<DefaultPrinterLifecycleResult.ClearedMissing>(result)
        assertEquals(printerId(7), cleared.previous)
        assertEquals(null, settings.current.defaultPrinterId)
    }
}

private class FakeConnectionSettingsStore(initial: ConnectionSettings) : ConnectionSettingsStore {
    var current: ConnectionSettings = initial
        private set

    override suspend fun read(): ConnectionSettings = current

    override suspend fun replace(settings: ConnectionSettings) {
        current = settings
    }
}

private fun printerId(value: Long): PrinterId = PrinterId.from(value) ?: error("Test printer ID must be valid")
