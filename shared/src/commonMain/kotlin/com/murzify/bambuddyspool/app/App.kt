@file:Suppress(
    "FunctionNaming", // Compose functions use UpperCamelCase by convention.
    "TooManyFunctions" // Root rendering keeps responsive navigation and its destination adapters together.
)

package com.murzify.bambuddyspool.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.navigation.RootDestination
import com.murzify.bambuddyspool.app.root.HomeConnectionState
import com.murzify.bambuddyspool.app.root.HomeNfcState
import com.murzify.bambuddyspool.app.root.RootComponent
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.app.root.RootState
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.MutationAvailability
import com.murzify.bambuddyspool.core.projections.SpoolListFilters
import com.murzify.bambuddyspool.core.projections.SpoolSummaryProjection
import com.murzify.bambuddyspool.feature.spools.SpoolsComponent
import com.murzify.bambuddyspool.feature.spools.SpoolsIntent
import com.murzify.bambuddyspool.shared.resources.Res
import com.murzify.bambuddyspool.shared.resources.home_connection_not_configured
import com.murzify.bambuddyspool.shared.resources.home_connection_offline
import com.murzify.bambuddyspool.shared.resources.home_connection_online
import com.murzify.bambuddyspool.shared.resources.home_connection_stale
import com.murzify.bambuddyspool.shared.resources.home_hold_tag
import com.murzify.bambuddyspool.shared.resources.home_nfc_available
import com.murzify.bambuddyspool.shared.resources.home_nfc_disabled
import com.murzify.bambuddyspool.shared.resources.home_nfc_unavailable
import com.murzify.bambuddyspool.shared.resources.navigation_home
import com.murzify.bambuddyspool.shared.resources.navigation_printers
import com.murzify.bambuddyspool.shared.resources.navigation_settings
import com.murzify.bambuddyspool.shared.resources.navigation_spools
import com.murzify.bambuddyspool.shared.resources.placeholder_printers
import com.murzify.bambuddyspool.shared.resources.placeholder_settings
import com.murzify.bambuddyspool.shared.resources.spools_assign
import com.murzify.bambuddyspool.shared.resources.spools_assignment
import com.murzify.bambuddyspool.shared.resources.spools_back
import com.murzify.bambuddyspool.shared.resources.spools_deleted_tagged
import com.murzify.bambuddyspool.shared.resources.spools_details
import com.murzify.bambuddyspool.shared.resources.spools_id
import com.murzify.bambuddyspool.shared.resources.spools_include_archived
import com.murzify.bambuddyspool.shared.resources.spools_include_empty
import com.murzify.bambuddyspool.shared.resources.spools_include_inactive
import com.murzify.bambuddyspool.shared.resources.spools_link_tag
import com.murzify.bambuddyspool.shared.resources.spools_loading
import com.murzify.bambuddyspool.shared.resources.spools_mutation_unavailable
import com.murzify.bambuddyspool.shared.resources.spools_no_results
import com.murzify.bambuddyspool.shared.resources.spools_refreshing
import com.murzify.bambuddyspool.shared.resources.spools_relink_tag
import com.murzify.bambuddyspool.shared.resources.spools_remaining_grams
import com.murzify.bambuddyspool.shared.resources.spools_remaining_unknown
import com.murzify.bambuddyspool.shared.resources.spools_search
import com.murzify.bambuddyspool.shared.resources.spools_stale
import com.murzify.bambuddyspool.shared.resources.spools_title
import com.murzify.bambuddyspool.shared.resources.spools_unassigned
import com.murzify.bambuddyspool.shared.resources.spools_unnamed
import com.murzify.bambuddyspool.shared.resources.workflow_confirmation
import com.murzify.bambuddyspool.shared.resources.workflow_error
import com.murzify.bambuddyspool.shared.resources.workflow_processing
import com.murzify.bambuddyspool.shared.resources.workflow_success
import com.murzify.bambuddyspool.shared.resources.workflow_tag_mutation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Renders the shared application root supplied by a platform shell. */
@Composable
@Suppress("FunctionNaming") // Compose entry points use UpperCamelCase by convention.
fun App(root: RootComponent) {
    val state by root.state.collectAsState()
    MaterialTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth < 600.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    RootContent(state = state, root = root, modifier = Modifier.weight(1f))
                    BottomNavigation(state.destination, root::accept)
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    RailNavigation(state.destination, root::accept)
                    RootContent(state = state, root = root, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BottomNavigation(selected: RootDestination, accept: (RootIntent) -> Unit) = NavigationBar {
    RootDestination.entries.forEach { destination ->
        NavigationBarItem(
            selected = selected == destination,
            onClick = { accept(RootIntent.Select(destination)) },
            icon = {},
            label = { Text(stringResource(destination.label())) }
        )
    }
}

@Composable
private fun RailNavigation(selected: RootDestination, accept: (RootIntent) -> Unit) = NavigationRail {
    RootDestination.entries.forEach { destination ->
        NavigationRailItem(
            selected = selected == destination,
            onClick = { accept(RootIntent.Select(destination)) },
            icon = {},
            label = { Text(stringResource(destination.label())) }
        )
    }
}

@Composable
private fun RootContent(state: RootState, root: RootComponent, modifier: Modifier) =
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.destination) {
                RootDestination.Home -> HomeScreen(state)
                RootDestination.Spools -> SpoolsScreen(root.spoolsComponent, root::accept)
                RootDestination.Printers -> Text(stringResource(Res.string.placeholder_printers))
                RootDestination.Settings -> Text(stringResource(Res.string.placeholder_settings))
            }
            state.transientWorkflow?.let { workflow ->
                Text(text = stringResource(workflow.label()))
            }
        }
    }

@Composable
private fun SpoolsScreen(component: SpoolsComponent, acceptRoot: (RootIntent) -> Unit) {
    val state by component.state.collectAsState()
    state.selectedSpoolId?.let { selected ->
        SpoolDetailScreen(component, selected.value, acceptRoot)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.spools_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.query,
            onValueChange = { component.accept(SpoolsIntent.SearchChanged(it)) },
            label = { Text(stringResource(Res.string.spools_search)) },
            modifier = Modifier.fillMaxWidth()
        )
        SpoolFilters(filters = state.filters, onChanged = { component.accept(SpoolsIntent.FiltersChanged(it)) })
        when (val projection = state.projection) {
            CacheProjectionState.InitialLoading -> Text(stringResource(Res.string.spools_loading))
            is CacheProjectionState.FatalErrorWithoutCache -> Text(
                stringResource(Res.string.spools_mutation_unavailable)
            )
            is CacheProjectionState.Content -> SpoolList(projection.value.items, projection.availability, component)
            is CacheProjectionState.ContentRefreshing -> {
                Text(stringResource(Res.string.spools_refreshing))
                SpoolList(projection.value.items, projection.availability, component)
            }
        }
    }
}

@Composable
private fun SpoolFilters(filters: SpoolListFilters, onChanged: (SpoolListFilters) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterCheckbox(stringResource(Res.string.spools_include_inactive), filters.includeInactive) {
            onChanged(filters.copy(includeInactive = it))
        }
        FilterCheckbox(stringResource(Res.string.spools_include_archived), filters.includeArchived) {
            onChanged(filters.copy(includeArchived = it))
        }
        FilterCheckbox(stringResource(Res.string.spools_include_empty), filters.includeEmpty) {
            onChanged(filters.copy(includeEmpty = it))
        }
    }
}

@Composable
private fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically
) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(label, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun SpoolList(
    spools: List<SpoolSummaryProjection>,
    availability: com.murzify.bambuddyspool.core.projections.CacheAvailability,
    component: SpoolsComponent
) {
    if (availability.isStale) Text(stringResource(Res.string.spools_stale))
    if (spools.isEmpty()) {
        Text(stringResource(Res.string.spools_no_results))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(spools, key = { it.id.value }) { spool ->
            SpoolRow(spool) { component.accept(SpoolsIntent.OpenDetail(spool.id)) }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SpoolRow(spool: SpoolSummaryProjection, onOpen: () -> Unit) =
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(spool.name ?: stringResource(Res.string.spools_unnamed))
            Text(stringResource(Res.string.spools_id, spool.id.value))
            spool.manufacturer?.let { Text(it) }
            spool.material?.let { Text(it) }
            spool.colorName?.let { Text(it) }
            Text(remainingText(spool))
            Text(assignmentText(spool))
        }
    }

@Composable
private fun SpoolDetailScreen(component: SpoolsComponent, spoolId: Long, acceptRoot: (RootIntent) -> Unit) {
    val state by component.state.collectAsState()
    val projection = state.projection as? CacheProjectionState.Content
    val spool = projection?.value?.items?.firstOrNull { it.id.value == spoolId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            component.accept(SpoolsIntent.CloseDetail)
        }) { Text(stringResource(Res.string.spools_back)) }
        Text(stringResource(Res.string.spools_details), style = MaterialTheme.typography.headlineSmall)
        if (spool == null) {
            Text(stringResource(Res.string.spools_deleted_tagged))
            Button(onClick = { acceptRoot(RootIntent.StartTagLink(null)) }) {
                Text(stringResource(Res.string.spools_relink_tag))
            }
        } else {
            Text(spool.name ?: stringResource(Res.string.spools_unnamed))
            Text(stringResource(Res.string.spools_id, spool.id.value))
            spool.manufacturer?.let { Text(it) }
            spool.material?.let { Text(it) }
            spool.colorName?.let { Text(it) }
            Text(remainingText(spool))
            Text(assignmentText(spool))
            val mutationsAvailable = projection.availability.mutation is MutationAvailability.Available
            if (!mutationsAvailable) Text(stringResource(Res.string.spools_mutation_unavailable))
            Button(
                onClick = { acceptRoot(RootIntent.StartManualAssignment(spool.id.value)) },
                enabled = mutationsAvailable
            ) { Text(stringResource(Res.string.spools_assign)) }
            Button(
                onClick = { acceptRoot(RootIntent.StartTagLink(spool.id.value)) },
                enabled = mutationsAvailable
            ) { Text(stringResource(Res.string.spools_link_tag)) }
        }
    }
}

@Composable
private fun remainingText(spool: SpoolSummaryProjection): String = spool.remainingGrams?.let {
    stringResource(Res.string.spools_remaining_grams, it)
} ?: stringResource(Res.string.spools_remaining_unknown)

@Composable
private fun assignmentText(spool: SpoolSummaryProjection): String = spool.assignedSlot?.let {
    stringResource(Res.string.spools_assignment, it.printerName ?: it.printerId.value.toString())
} ?: stringResource(Res.string.spools_unassigned)

@Composable
private fun HomeScreen(state: RootState) {
    Text(stringResource(Res.string.home_hold_tag))
    Text(stringResource(state.connectionState.label()))
    Text(stringResource(state.nfcState.label()))
}

private fun RootDestination.label(): StringResource = when (this) {
    RootDestination.Home -> Res.string.navigation_home
    RootDestination.Spools -> Res.string.navigation_spools
    RootDestination.Printers -> Res.string.navigation_printers
    RootDestination.Settings -> Res.string.navigation_settings
}

private fun HomeConnectionState.label(): StringResource = when (this) {
    HomeConnectionState.NotConfigured -> Res.string.home_connection_not_configured
    HomeConnectionState.Online -> Res.string.home_connection_online
    HomeConnectionState.Offline -> Res.string.home_connection_offline
    HomeConnectionState.Stale -> Res.string.home_connection_stale
}

private fun HomeNfcState.label(): StringResource = when (this) {
    HomeNfcState.Available -> Res.string.home_nfc_available
    HomeNfcState.Unavailable -> Res.string.home_nfc_unavailable
    HomeNfcState.Disabled -> Res.string.home_nfc_disabled
}

private fun com.murzify.bambuddyspool.app.root.RootTransientWorkflow.label(): StringResource = when (this) {
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Processing -> Res.string.workflow_processing
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Confirmation -> Res.string.workflow_confirmation
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Success -> Res.string.workflow_success
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.Error -> Res.string.workflow_error
    com.murzify.bambuddyspool.app.root.RootTransientWorkflow.TagMutation -> Res.string.workflow_tag_mutation
}
