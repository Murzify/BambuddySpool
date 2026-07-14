package com.murzify.bambuddyspool.feature.spools

import com.arkivanov.decompose.ComponentContext
import com.murzify.bambuddyspool.core.application.Reducer
import com.murzify.bambuddyspool.core.application.Reduction
import com.murzify.bambuddyspool.core.application.UdfComponent
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.PageRequest
import com.murzify.bambuddyspool.core.projections.PagedResult
import com.murzify.bambuddyspool.core.projections.SpoolListFilters
import com.murzify.bambuddyspool.core.projections.SpoolSearchQuery
import com.murzify.bambuddyspool.core.projections.SpoolSummaryProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Safe, restorable spool-list state. It deliberately contains no mutation authorization or NFC state. */
data class SpoolsState(
    val query: String = "",
    val filters: SpoolListFilters = SpoolListFilters(),
    val selectedSpoolId: SpoolId? = null,
    val projection: CacheProjectionState<PagedResult<SpoolSummaryProjection>> = CacheProjectionState.InitialLoading
)

sealed interface SpoolsIntent {
    data class SearchChanged(val value: String) : SpoolsIntent
    data class FiltersChanged(val value: SpoolListFilters) : SpoolsIntent
    data class OpenDetail(val spoolId: SpoolId) : SpoolsIntent
    data object CloseDetail : SpoolsIntent
}

internal object SpoolsReducer : Reducer<SpoolsState, SpoolsIntent, Nothing> {
    override fun reduce(state: SpoolsState, intent: SpoolsIntent): Reduction<SpoolsState, Nothing> = when (intent) {
        is SpoolsIntent.SearchChanged -> Reduction(state.copy(query = intent.value))
        is SpoolsIntent.FiltersChanged -> Reduction(state.copy(filters = intent.value))
        is SpoolsIntent.OpenDetail -> Reduction(state.copy(selectedSpoolId = intent.spoolId))
        SpoolsIntent.CloseDetail -> Reduction(state.copy(selectedSpoolId = null))
    }
}

@Serializable
private data class RestoredSpoolsState(
    val query: String,
    val filters: RestoredSpoolsFilters,
    val selectedSpoolId: Long?
)

@Serializable
private data class RestoredSpoolsFilters(
    val includeInactive: Boolean,
    val includeArchived: Boolean,
    val includeEmpty: Boolean
)

/**
 * Shared spool browser controller. Search is delegated to the Room projection's cancellable 150ms pipeline instead
 * of filtering an in-memory list, keeping the common UI practical for a 25k-record inventory.
 */
class SpoolsComponent(
    componentContext: ComponentContext,
    repository: CacheProjectionRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UdfComponent<SpoolsState, SpoolsIntent> {
    private val restored = componentContext.stateKeeper.consume("spool-browser", RestoredSpoolsState.serializer())
    private val mutableState = MutableStateFlow(restored.toState())
    private val queries = MutableStateFlow(mutableState.value.toSearchQuery())

    override val state: StateFlow<SpoolsState> = mutableState.asStateFlow()

    init {
        componentContext.stateKeeper.register("spool-browser", RestoredSpoolsState.serializer()) {
            mutableState.value.toRestoredState()
        }
        repository?.let(::observeRepository)
    }

    override fun accept(intent: SpoolsIntent) {
        mutableState.update { current -> SpoolsReducer.reduce(current, intent).state }
        when (intent) {
            is SpoolsIntent.SearchChanged, is SpoolsIntent.FiltersChanged ->
                queries.value =
                    mutableState.value.toSearchQuery()
            is SpoolsIntent.OpenDetail, SpoolsIntent.CloseDetail -> Unit
        }
    }

    private fun observeRepository(repository: CacheProjectionRepository) {
        scope.launch {
            repository.observeSpoolSearch(queries, SPOOL_PAGE).collectLatest { projection ->
                mutableState.update { it.copy(projection = projection) }
            }
        }
    }
}

private fun RestoredSpoolsState?.toState(): SpoolsState = this?.let {
    SpoolsState(
        query = query,
        filters = SpoolListFilters(
            includeInactive = filters.includeInactive,
            includeArchived = filters.includeArchived,
            includeEmpty = filters.includeEmpty
        ),
        selectedSpoolId = selectedSpoolId?.let(SpoolId::from)
    )
} ?: SpoolsState()

private fun SpoolsState.toRestoredState(): RestoredSpoolsState = RestoredSpoolsState(
    query = query,
    filters = RestoredSpoolsFilters(
        includeInactive = filters.includeInactive,
        includeArchived = filters.includeArchived,
        includeEmpty = filters.includeEmpty
    ),
    selectedSpoolId = selectedSpoolId?.value
)

private fun SpoolsState.toSearchQuery(): SpoolSearchQuery = SpoolSearchQuery(rawText = query, filters = filters)

private val SPOOL_PAGE = PageRequest(limit = 100, offset = 0)
