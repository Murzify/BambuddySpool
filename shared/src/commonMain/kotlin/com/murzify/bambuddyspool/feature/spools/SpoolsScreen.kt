@file:Suppress("FunctionNaming")

package com.murzify.bambuddyspool.feature.spools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.murzify.bambuddyspool.app.root.RootIntent
import com.murzify.bambuddyspool.core.projections.CacheAvailability
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.MutationAvailability
import com.murzify.bambuddyspool.core.projections.SpoolListFilters
import com.murzify.bambuddyspool.core.projections.SpoolSummaryProjection
import com.murzify.bambuddyspool.shared.resources.Res
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
import org.jetbrains.compose.resources.stringResource

internal const val SPOOL_SEARCH_TAG = "spools-search"
internal const val SPOOL_ASSIGN_TAG = "spools-assign"
internal const val SPOOL_LINK_TAG = "spools-link-tag"
internal const val SPOOL_RELINK_TAG = "spools-relink-tag"

@Composable
fun SpoolsScreen(component: SpoolsComponent, acceptRoot: (RootIntent) -> Unit) {
    val state by component.state.collectAsState()
    state.selectedSpoolId?.let { selected ->
        SpoolDetailScreen(component, acceptRoot)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.spools_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.query,
            onValueChange = { component.accept(SpoolsIntent.SearchChanged(it)) },
            label = { Text(stringResource(Res.string.spools_search)) },
            modifier = Modifier.fillMaxWidth().testTag(SPOOL_SEARCH_TAG)
        )
        SpoolFilters(state.filters) { component.accept(SpoolsIntent.FiltersChanged(it)) }
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
private fun SpoolFilters(filters: SpoolListFilters, changed: (SpoolListFilters) -> Unit) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.fillMaxWidth()
) {
    ToggleFilter(stringResource(Res.string.spools_include_inactive), filters.includeInactive) {
        changed(filters.copy(includeInactive = it))
    }
    ToggleFilter(stringResource(Res.string.spools_include_archived), filters.includeArchived) {
        changed(filters.copy(includeArchived = it))
    }
    ToggleFilter(stringResource(Res.string.spools_include_empty), filters.includeEmpty) {
        changed(filters.copy(includeEmpty = it))
    }
}

@Composable
private fun ToggleFilter(label: String, value: Boolean, changed: (Boolean) -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.heightIn(min = 48.dp)
) {
    Checkbox(value, changed)
    Text(label, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun SpoolList(
    spools: List<SpoolSummaryProjection>,
    availability: CacheAvailability,
    component: SpoolsComponent
) {
    if (availability.isStale) Text(stringResource(Res.string.spools_stale))
    if (spools.isEmpty()) {
        Text(stringResource(Res.string.spools_no_results))
    } else {
        LazyColumn {
            items(spools, key = { it.id.value }) { spool ->
                Button(onClick = {
                    component.accept(SpoolsIntent.OpenDetail(spool.id))
                }, modifier = Modifier.fillMaxWidth()) {
                    SpoolText(spool)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SpoolText(spool: SpoolSummaryProjection) = Column {
    Text(spool.name ?: stringResource(Res.string.spools_unnamed))
    Text(stringResource(Res.string.spools_id, spool.id.value))
    spool.manufacturer?.let { Text(it) }
    spool.material?.let { Text(it) }
    spool.colorName?.let { Text(it) }
    Text(remainingText(spool))
    Text(assignmentText(spool))
}

@Composable
private fun SpoolDetailScreen(component: SpoolsComponent, acceptRoot: (RootIntent) -> Unit) {
    val state by component.state.collectAsState()
    val detail = state.detailProjection
    val spool = detail.contentOrNull()
    val availability = detail.availabilityOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            component.accept(SpoolsIntent.CloseDetail)
        }) { Text(stringResource(Res.string.spools_back)) }
        Text(stringResource(Res.string.spools_details), style = MaterialTheme.typography.headlineSmall)
        when {
            detail is CacheProjectionState.InitialLoading -> Text(stringResource(Res.string.spools_loading))
            spool == null -> {
                Text(stringResource(Res.string.spools_deleted_tagged))
                Button(onClick = {
                    acceptRoot(RootIntent.StartTagLink(null))
                }, modifier = Modifier.testTag(SPOOL_RELINK_TAG)) {
                    Text(stringResource(Res.string.spools_relink_tag))
                }
            }
            else -> {
                SpoolText(spool)
                val enabled = availability?.mutation is MutationAvailability.Available
                if (!enabled) Text(stringResource(Res.string.spools_mutation_unavailable))
                Button(onClick = {
                    acceptRoot(RootIntent.StartManualAssignment(spool.id.value))
                }, enabled = enabled, modifier = Modifier.testTag(SPOOL_ASSIGN_TAG)) {
                    Text(stringResource(Res.string.spools_assign))
                }
                Button(onClick = {
                    acceptRoot(RootIntent.StartTagLink(spool.id.value))
                }, enabled = enabled, modifier = Modifier.testTag(SPOOL_LINK_TAG)) {
                    Text(stringResource(Res.string.spools_link_tag))
                }
            }
        }
    }
}

private fun CacheProjectionState<SpoolSummaryProjection?>.contentOrNull(): SpoolSummaryProjection? = when (this) {
    is CacheProjectionState.Content -> value
    is CacheProjectionState.ContentRefreshing -> value
    CacheProjectionState.InitialLoading, is CacheProjectionState.FatalErrorWithoutCache -> null
}

private fun CacheProjectionState<SpoolSummaryProjection?>.availabilityOrNull(): CacheAvailability? = when (this) {
    is CacheProjectionState.Content -> availability
    is CacheProjectionState.ContentRefreshing -> availability
    CacheProjectionState.InitialLoading, is CacheProjectionState.FatalErrorWithoutCache -> null
}

@Composable
private fun remainingText(spool: SpoolSummaryProjection): String = spool.remainingGrams?.let {
    stringResource(Res.string.spools_remaining_grams, it)
} ?: stringResource(Res.string.spools_remaining_unknown)

@Composable
private fun assignmentText(spool: SpoolSummaryProjection): String = spool.assignedSlot?.let {
    stringResource(Res.string.spools_assignment, it.printerName ?: it.printerId.value.toString())
} ?: stringResource(Res.string.spools_unassigned)
