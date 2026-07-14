package com.murzify.bambuddyspool.core.projections

import com.murzify.bambuddyspool.core.database.PrinterListProjection
import com.murzify.bambuddyspool.core.database.PrinterSlotAssignmentProjection
import com.murzify.bambuddyspool.core.database.SpoolListProjection
import com.murzify.bambuddyspool.core.database.SyncMetadataProjection
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.settings.BaseUrlParseResult
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.parseCanonicalBaseUrl
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CacheProjectionRepositoryTest {

    @Test
    fun contentRefreshingKeepsCachedRowsVisibleAndDisablesMutations() = runTest {
        val dataSource = FakeCacheProjectionDataSource(
            spoolRows = listOf(spoolRow(1)),
            spoolCount = 1,
            metadata = SyncMetadataProjection(lastSuccessfulSyncAtEpochMillis = 10, snapshotGeneration = 7)
        )
        val repository = repository(
            dataSource = dataSource,
            refreshState = CacheRefreshState.Refreshing
        )

        val state = repository.observeDefaultSpoolPage(PageRequest(limit = 50, offset = 0)).firstProjection()

        val refreshing =
            assertIs<CacheProjectionState.ContentRefreshing<PagedResult<SpoolSummaryProjection>>>(state)
        assertEquals(listOf(spoolId(1)), refreshing.value.items.map { it.id })
        assertTrue(refreshing.availability.isStale)
        assertEquals(
            MutationAvailability.Disabled(MutationDisabledReason.RefreshInProgress),
            refreshing.availability.mutation
        )
    }

    @Test
    fun staleCachedContentSurvivesOfflineRefreshFailure() = runTest {
        val dataSource = FakeCacheProjectionDataSource(
            spoolRows = listOf(spoolRow(2)),
            spoolCount = 1,
            metadata = SyncMetadataProjection(lastSuccessfulSyncAtEpochMillis = 10, snapshotGeneration = 4)
        )
        val repository = repository(
            dataSource = dataSource,
            refreshState = CacheRefreshState.Failed(
                CacheProjectionError.RefreshFailed(RefreshFailureReason.Offline)
            )
        )

        val state = repository.observeDefaultSpoolPage(PageRequest(limit = 25, offset = 0)).firstProjection()

        val content = assertIs<CacheProjectionState.Content<PagedResult<SpoolSummaryProjection>>>(state)
        assertTrue(content.availability.isStale)
        assertIs<CacheProjectionError.RefreshFailed>(content.availability.nonBlockingError)
        assertEquals(MutationAvailability.Disabled(MutationDisabledReason.Offline), content.availability.mutation)
        assertEquals(listOf(spoolId(2)), content.value.items.map { it.id })
    }

    @Test
    fun fatalErrorIsUsedOnlyWhenNoCacheExists() = runTest {
        val repository = repository(
            dataSource = FakeCacheProjectionDataSource(
                spoolRows = emptyList(),
                spoolCount = 0,
                metadata = null
            ),
            refreshState = CacheRefreshState.Failed(
                CacheProjectionError.RefreshFailed(RefreshFailureReason.AuthenticationRejected)
            )
        )

        val state = repository.observeDefaultSpoolPage(PageRequest(limit = 25, offset = 0)).firstProjection()

        val fatal = assertIs<CacheProjectionState.FatalErrorWithoutCache>(state)
        assertEquals(
            CacheProjectionError.RefreshFailed(RefreshFailureReason.AuthenticationRejected),
            fatal.error
        )
    }

    @Test
    fun searchDebouncesAndCancelsPreviousDatabaseQuery() = runTest {
        val dataSource = FakeCacheProjectionDataSource(
            spoolRows = listOf(spoolRow(1)),
            spoolCount = 1,
            metadata = SyncMetadataProjection(lastSuccessfulSyncAtEpochMillis = 10, snapshotGeneration = 1)
        )
        val repository = repository(dataSource = dataSource)
        val queries = MutableSharedFlow<SpoolSearchQuery>()
        val collectJob = launch {
            repository.observeSpoolSearch(queries, PageRequest(limit = 20, offset = 0)).collect { }
        }
        runCurrent()

        queries.emit(SpoolSearchQuery("pla"))
        advanceTimeBy(149.milliseconds)
        runCurrent()
        assertEquals(emptyList<String?>(), dataSource.searchCalls)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(listOf<String?>("pla*"), dataSource.searchCalls)

        queries.emit(SpoolSearchQuery("petg"))
        advanceTimeBy(150.milliseconds)
        runCurrent()

        assertEquals(listOf<String?>("pla*", "petg*"), dataSource.searchCalls)
        assertEquals(listOf<String?>("pla*"), dataSource.cancelledSearches)
        collectJob.cancel()
    }

    private fun repository(
        dataSource: FakeCacheProjectionDataSource,
        refreshState: CacheRefreshState = CacheRefreshState.Idle
    ): RoomCacheProjectionRepository = RoomCacheProjectionRepository(
        dataSource = dataSource,
        settingsStore = FakeObservableSettingsStore(
            ConnectionSettings(
                baseUrl = assertIs<BaseUrlParseResult.Success>(
                    parseCanonicalBaseUrl("https://bambuddy.local/api")
                ).value,
                defaultPrinterId = null,
                httpConsentOrigin = null,
                tlsOverrideHostname = null
            )
        ),
        refreshStates = MutableStateFlow(refreshState),
        searchDebounce = 150.milliseconds
    )
}

private suspend fun <T> Flow<T>.firstProjection(): T {
    var result: T? = null
    try {
        collect {
            result = it
            throw ProjectionCollected()
        }
    } catch (_: ProjectionCollected) {
        return result ?: error("Projection flow emitted null sentinel")
    }
    error("Projection flow completed without emitting")
}

private class ProjectionCollected : CancellationException()

private class FakeObservableSettingsStore(settings: ConnectionSettings) : ObservableConnectionSettingsStore {
    private val settingsFlow = MutableStateFlow(settings)

    override fun observeSettings(): Flow<ConnectionSettings> = settingsFlow
}

private class FakeCacheProjectionDataSource(
    spoolRows: List<SpoolListProjection> = emptyList(),
    spoolCount: Int = spoolRows.size,
    metadata: SyncMetadataProjection? = null
) : CacheProjectionDataSource {
    private val spoolRows = MutableStateFlow(spoolRows)
    private val spoolCount = MutableStateFlow(spoolCount)
    private val metadata = MutableStateFlow(metadata)
    val searchCalls: MutableList<String?> = mutableListOf()
    val cancelledSearches: MutableList<String?> = mutableListOf()

    override fun observePrinters(): Flow<List<PrinterListProjection>> = MutableStateFlow(emptyList())

    override fun observePrinterCount(): Flow<Int> = MutableStateFlow(0)

    override fun observePrinterSlots(printerId: PrinterId): Flow<List<PrinterSlotAssignmentProjection>> =
        MutableStateFlow(emptyList())

    override fun observeDefaultSpoolPage(page: PageRequest): Flow<List<SpoolListProjection>> = spoolRows

    override fun observeSpoolSearch(
        ftsQuery: String?,
        filters: SpoolListFilters,
        page: PageRequest
    ): Flow<List<SpoolListProjection>> = flow {
        searchCalls += ftsQuery
        emit(spoolRows.value)
        try {
            awaitCancellation()
        } finally {
            cancelledSearches += ftsQuery
        }
    }

    override fun observeSpool(spoolId: SpoolId): Flow<List<SpoolListProjection>> = spoolRows

    override fun observeSpoolCount(): Flow<Int> = spoolCount

    override fun observeSyncMetadata(): Flow<SyncMetadataProjection?> = metadata
}

private fun spoolRow(value: Long): SpoolListProjection = SpoolListProjection(
    spoolId = value,
    displayName = "Spool $value",
    manufacturer = "Maker",
    material = "PLA",
    colorName = "Black",
    remainingGrams = 100,
    archivedAtEpochMillis = null,
    lastUsedAtEpochMillis = value,
    assignedPrinterId = null,
    assignedPrinterName = null,
    assignedAmsId = null,
    assignedTrayId = null
)

private fun spoolId(value: Long): SpoolId = SpoolId.from(value) ?: error("Test spool ID must be valid")
