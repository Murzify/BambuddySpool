@file:Suppress("FunctionNaming")

package com.murzify.bambuddyspool.feature.printers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.projections.CacheAvailability
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.MutationAvailability
import com.murzify.bambuddyspool.core.projections.PrinterSlotProjection
import com.murzify.bambuddyspool.core.projections.PrinterSummaryProjection
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.printers_ams_read_only
import com.murzify.bambuddyspool.shared.resources.printers_assign_here
import com.murzify.bambuddyspool.shared.resources.printers_back
import com.murzify.bambuddyspool.shared.resources.printers_empty
import com.murzify.bambuddyspool.shared.resources.printers_external_slots
import com.murzify.bambuddyspool.shared.resources.printers_loading
import com.murzify.bambuddyspool.shared.resources.printers_mutation_unavailable
import com.murzify.bambuddyspool.shared.resources.printers_no_slots
import com.murzify.bambuddyspool.shared.resources.printers_refreshing
import com.murzify.bambuddyspool.shared.resources.printers_slots
import com.murzify.bambuddyspool.shared.resources.printers_title
import com.murzify.bambuddyspool.shared.resources.printers_unnamed
import org.jetbrains.compose.resources.stringResource

internal const val PRINTER_EXTERNAL_ASSIGN_TAG = "printers-external-assign"
internal const val PRINTER_AMS_TAG = "printers-ams-read-only"

@Composable
fun PrintersScreen(component: PrintersComponent, manualSpoolId: SpoolId?, acceptRoot: (RootIntent) -> Unit) {
    val state by component.state.collectAsState()
    state.selectedPrinterId?.let {
        PrinterDetailScreen(component, manualSpoolId, acceptRoot)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.printers_title), style = MaterialTheme.typography.headlineSmall)
        when (val projection = state.printers) {
            CacheProjectionState.InitialLoading -> Text(stringResource(Res.string.printers_loading))
            is CacheProjectionState.FatalErrorWithoutCache -> Text(
                stringResource(Res.string.printers_mutation_unavailable)
            )
            is CacheProjectionState.Content -> PrinterList(projection.value, component)
            is CacheProjectionState.ContentRefreshing -> {
                Text(stringResource(Res.string.printers_refreshing))
                PrinterList(projection.value, component)
            }
        }
    }
}

@Composable
private fun PrinterList(printers: List<PrinterSummaryProjection>, component: PrintersComponent) {
    if (printers.isEmpty()) {
        Text(stringResource(Res.string.printers_empty))
    } else {
        LazyColumn {
            items(printers, key = { it.id.value }) { printer ->
                Button(
                    onClick = { component.accept(PrintersIntent.OpenPrinter(printer.id)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(printer.name ?: stringResource(Res.string.printers_unnamed, printer.id.value))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PrinterDetailScreen(
    component: PrintersComponent,
    manualSpoolId: SpoolId?,
    acceptRoot: (RootIntent) -> Unit
) {
    val state by component.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { component.accept(PrintersIntent.ClosePrinter) }) {
            Text(stringResource(Res.string.printers_back))
        }
        Text(stringResource(Res.string.printers_slots), style = MaterialTheme.typography.headlineSmall)
        when (val projection = state.slots) {
            CacheProjectionState.InitialLoading -> Text(stringResource(Res.string.printers_loading))
            is CacheProjectionState.FatalErrorWithoutCache -> Text(
                stringResource(Res.string.printers_mutation_unavailable)
            )
            is CacheProjectionState.Content -> Slots(
                projection.value,
                projection.availability,
                manualSpoolId,
                acceptRoot
            )
            is CacheProjectionState.ContentRefreshing -> {
                Text(stringResource(Res.string.printers_refreshing))
                Slots(projection.value, projection.availability, manualSpoolId, acceptRoot)
            }
        }
    }
}

@Composable
private fun Slots(
    slots: List<PrinterSlotProjection>,
    availability: CacheAvailability,
    manualSpoolId: SpoolId?,
    acceptRoot: (RootIntent) -> Unit
) {
    if (slots.isEmpty()) Text(stringResource(Res.string.printers_no_slots))
    if (availability.mutation !is MutationAvailability.Available) {
        Text(stringResource(Res.string.printers_mutation_unavailable))
    }
    slots.forEach { slot ->
        val isExternal = slot.kind == SlotKind.External
        val canAssign = isExternal && manualSpoolId != null && availability.mutation is MutationAvailability.Available
        Text(
            slot.label ?: if (isExternal) {
                stringResource(Res.string.printers_external_slots)
            } else {
                stringResource(
                    Res.string.printers_ams_read_only
                )
            }
        )
        slot.assignedSpool?.let { Text(it.name ?: it.id.value.toString()) }
        if (isExternal) {
            Button(
                enabled = canAssign,
                onClick = {
                    val generation = (availability.mutation as? MutationAvailability.Available)?.snapshotGeneration
                        ?: return@Button
                    val spool = manualSpoolId ?: return@Button
                    acceptRoot(RootIntent.CreateManualAssignment(spool, slot.slot, generation))
                },
                modifier = Modifier.testTag(PRINTER_EXTERNAL_ASSIGN_TAG)
            ) { Text(stringResource(Res.string.printers_assign_here)) }
        } else {
            Text(stringResource(Res.string.printers_ams_read_only), modifier = Modifier.testTag(PRINTER_AMS_TAG))
        }
    }
}
