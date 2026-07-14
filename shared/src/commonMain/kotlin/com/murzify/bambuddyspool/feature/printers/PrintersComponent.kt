package com.murzify.bambuddyspool.feature.printers

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.murzify.bambuddyspool.core.application.Reducer
import com.murzify.bambuddyspool.core.application.Reduction
import com.murzify.bambuddyspool.core.application.UdfComponent
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.PrinterSlotProjection
import com.murzify.bambuddyspool.core.projections.PrinterSummaryProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Safe printer-navigation state; an active manual spool is owned transiently by RootComponent. */
data class PrintersState(
    val selectedPrinterId: PrinterId? = null,
    val printers: CacheProjectionState<List<PrinterSummaryProjection>> = CacheProjectionState.InitialLoading,
    val slots: CacheProjectionState<List<PrinterSlotProjection>> = CacheProjectionState.InitialLoading
)

sealed interface PrintersIntent {
    data class OpenPrinter(val printerId: PrinterId) : PrintersIntent
    data object ClosePrinter : PrintersIntent
}

internal object PrintersReducer : Reducer<PrintersState, PrintersIntent, Nothing> {
    override fun reduce(state: PrintersState, intent: PrintersIntent): Reduction<PrintersState, Nothing> =
        when (intent) {
            is PrintersIntent.OpenPrinter -> Reduction(state.copy(selectedPrinterId = intent.printerId))
            PrintersIntent.ClosePrinter -> Reduction(state.copy(selectedPrinterId = null))
        }
}

@Serializable
private data class RestoredPrintersState(val selectedPrinterId: Long?)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrintersComponent(componentContext: ComponentContext, repository: CacheProjectionRepository) :
    UdfComponent<PrintersState, PrintersIntent> {
    private val restored = componentContext.stateKeeper.consume(
        "printer-browser",
        RestoredPrintersState.serializer()
    )
    private val mutableState =
        MutableStateFlow(PrintersState(selectedPrinterId = restored?.selectedPrinterId?.let(PrinterId::from)))
    private val selectedPrinterIds = MutableStateFlow(mutableState.value.selectedPrinterId)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val state: StateFlow<PrintersState> = mutableState.asStateFlow()

    init {
        componentContext.stateKeeper.register("printer-browser", RestoredPrintersState.serializer()) {
            RestoredPrintersState(mutableState.value.selectedPrinterId?.value)
        }
        componentContext.lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            repository.observePrinters().collectLatest { projection ->
                mutableState.update { it.copy(printers = projection) }
            }
        }
        scope.launch {
            selectedPrinterIds.flatMapLatest { id ->
                id?.let(repository::observePrinterSlots) ?: flowOf(CacheProjectionState.InitialLoading)
            }.collectLatest { projection ->
                mutableState.update { it.copy(slots = projection) }
            }
        }
    }

    override fun accept(intent: PrintersIntent) {
        mutableState.update { PrintersReducer.reduce(it, intent).state }
        if (intent is PrintersIntent.OpenPrinter || intent is PrintersIntent.ClosePrinter) {
            selectedPrinterIds.value = mutableState.value.selectedPrinterId
        }
    }
}
