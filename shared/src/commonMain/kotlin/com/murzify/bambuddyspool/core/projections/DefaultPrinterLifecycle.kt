package com.murzify.bambuddyspool.core.projections

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.settings.ConnectionSettingsStore

class DefaultPrinterLifecycle(private val settingsStore: ConnectionSettingsStore) {

    suspend fun reconcile(activePrinterIds: List<PrinterId>): DefaultPrinterLifecycleResult {
        val distinctPrinterIds = activePrinterIds.distinct()
        val current = settingsStore.read()
        val currentDefault = current.defaultPrinterId

        val nextDefault = when {
            currentDefault != null && currentDefault in distinctPrinterIds -> currentDefault
            distinctPrinterIds.size == 1 -> distinctPrinterIds.single()
            else -> null
        }

        if (nextDefault == currentDefault) {
            return DefaultPrinterLifecycleResult.Unchanged(currentDefault)
        }

        settingsStore.replace(current.copy(defaultPrinterId = nextDefault))
        return when {
            currentDefault == null && nextDefault != null -> DefaultPrinterLifecycleResult.AutoSelected(nextDefault)
            currentDefault != null && nextDefault != null -> DefaultPrinterLifecycleResult.ReplacedMissing(
                previous = currentDefault,
                current = nextDefault
            )
            else -> DefaultPrinterLifecycleResult.ClearedMissing(previous = currentDefault)
        }
    }
}

sealed interface DefaultPrinterLifecycleResult {
    data class Unchanged(val defaultPrinterId: PrinterId?) : DefaultPrinterLifecycleResult
    data class AutoSelected(val printerId: PrinterId) : DefaultPrinterLifecycleResult
    data class ReplacedMissing(val previous: PrinterId, val current: PrinterId) : DefaultPrinterLifecycleResult
    data class ClearedMissing(val previous: PrinterId?) : DefaultPrinterLifecycleResult
}
