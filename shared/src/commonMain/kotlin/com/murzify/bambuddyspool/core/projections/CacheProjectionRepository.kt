package com.murzify.bambuddyspool.core.projections

import com.murzify.bambuddyspool.core.database.BambuddyDatabase
import com.murzify.bambuddyspool.core.database.PrinterListProjection
import com.murzify.bambuddyspool.core.database.PrinterSlotAssignmentProjection
import com.murzify.bambuddyspool.core.database.SpoolListProjection
import com.murzify.bambuddyspool.core.database.SyncMetadataProjection
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface CacheProjectionRepository {
    fun observePrinters(): Flow<CacheProjectionState<List<PrinterSummaryProjection>>>

    fun observePrinterSlots(printerId: PrinterId): Flow<CacheProjectionState<List<PrinterSlotProjection>>>

    fun observeDefaultSpoolPage(page: PageRequest): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>>

    fun observeSpoolSearch(
        queries: Flow<SpoolSearchQuery>,
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>>

    fun observeSpool(spoolId: SpoolId): Flow<CacheProjectionState<SpoolSummaryProjection?>>
}

interface ObservableConnectionSettingsStore {
    fun observeSettings(): Flow<ConnectionSettings>
}

internal class RoomCacheProjectionRepository(
    private val dataSource: CacheProjectionDataSource,
    private val settingsStore: ObservableConnectionSettingsStore,
    private val refreshStates: Flow<CacheRefreshState>,
    private val searchDebounce: Duration = DEFAULT_SEARCH_DEBOUNCE
) : CacheProjectionRepository {
    constructor(
        database: BambuddyDatabase,
        settingsStore: ObservableConnectionSettingsStore,
        refreshStates: Flow<CacheRefreshState>,
        searchDebounce: Duration = DEFAULT_SEARCH_DEBOUNCE
    ) : this(
        dataSource = RoomCacheProjectionDataSource(database),
        settingsStore = settingsStore,
        refreshStates = refreshStates,
        searchDebounce = searchDebounce
    )

    override fun observePrinters(): Flow<CacheProjectionState<List<PrinterSummaryProjection>>> = combineProjectionState(
        dataSource.observePrinters(),
        dataSource.observeActivePrinterCount(),
        dataSource.observeSyncMetadata(),
        settingsStore.observeSettings(),
        refreshStates
    ) { rows, settings ->
        rows.map { it.toFeatureProjection(settings.defaultPrinterId) }
    }

    override fun observePrinterSlots(printerId: PrinterId): Flow<CacheProjectionState<List<PrinterSlotProjection>>> =
        combineProjectionState(
            dataSource.observePrinterSlots(printerId),
            dataSource.observeActivePrinterCount(),
            dataSource.observeSyncMetadata(),
            settingsStore.observeSettings(),
            refreshStates
        ) { rows, _ ->
            rows.map { it.toFeatureProjection() }
        }

    override fun observeDefaultSpoolPage(
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = observeSpoolPage(
        page = page,
        rows = dataSource.observeDefaultSpoolPage(page)
    )

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSpoolSearch(
        queries: Flow<SpoolSearchQuery>,
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = queries
        .debounce(searchDebounce)
        .map { it.normalized() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val rows = if (query.rawText.isBlank() && query.filters == SpoolListFilters()) {
                dataSource.observeDefaultSpoolPage(page)
            } else {
                dataSource.observeSpoolSearch(
                    ftsQuery = query.rawText.toFtsQueryOrNull(),
                    filters = query.filters,
                    page = page
                )
            }
            observeSpoolPage(page = page, rows = rows)
        }

    override fun observeSpool(spoolId: SpoolId): Flow<CacheProjectionState<SpoolSummaryProjection?>> =
        combineProjectionState(
            dataSource.observeSpool(spoolId),
            dataSource.observeSpoolCount(),
            dataSource.observeSyncMetadata(),
            settingsStore.observeSettings(),
            refreshStates
        ) { rows, _ ->
            rows.firstOrNull()?.toFeatureProjection()
        }

    private fun observeSpoolPage(
        page: PageRequest,
        rows: Flow<List<SpoolListProjection>>
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = combineProjectionState(
        rows,
        dataSource.observeSpoolCount(),
        dataSource.observeSyncMetadata(),
        settingsStore.observeSettings(),
        refreshStates
    ) { spoolRows, _ ->
        PagedResult(items = spoolRows.map { it.toFeatureProjection() }, page = page)
    }
}

internal interface CacheProjectionDataSource {
    fun observePrinters(): Flow<List<PrinterListProjection>>
    fun observeActivePrinterCount(): Flow<Int>
    fun observePrinterSlots(printerId: PrinterId): Flow<List<PrinterSlotAssignmentProjection>>
    fun observeDefaultSpoolPage(page: PageRequest): Flow<List<SpoolListProjection>>
    fun observeSpoolSearch(
        ftsQuery: String?,
        filters: SpoolListFilters,
        page: PageRequest
    ): Flow<List<SpoolListProjection>>

    fun observeSpool(spoolId: SpoolId): Flow<List<SpoolListProjection>>
    fun observeSpoolCount(): Flow<Int>
    fun observeSyncMetadata(): Flow<SyncMetadataProjection?>
}

internal class RoomCacheProjectionDataSource(private val database: BambuddyDatabase) : CacheProjectionDataSource {
    override fun observePrinters(): Flow<List<PrinterListProjection>> = database.printers().observePrinters()

    override fun observeActivePrinterCount(): Flow<Int> = database.printers().observeActivePrinterCount()

    override fun observePrinterSlots(printerId: PrinterId): Flow<List<PrinterSlotAssignmentProjection>> =
        database.printerSlots().observePrinterSlots(printerId.value)

    override fun observeDefaultSpoolPage(page: PageRequest): Flow<List<SpoolListProjection>> =
        database.spools().observeDefaultSpools(limit = page.limit, offset = page.offset)

    override fun observeSpoolSearch(
        ftsQuery: String?,
        filters: SpoolListFilters,
        page: PageRequest
    ): Flow<List<SpoolListProjection>> = database.spools().observeSpools(
        ftsQuery = ftsQuery,
        includeInactive = filters.includeInactive,
        includeArchived = filters.includeArchived,
        includeEmpty = filters.includeEmpty,
        limit = page.limit,
        offset = page.offset
    )

    override fun observeSpool(spoolId: SpoolId): Flow<List<SpoolListProjection>> =
        database.spools().observeSpool(spoolId.value)

    override fun observeSpoolCount(): Flow<Int> = database.spools().observeSpoolCount()

    override fun observeSyncMetadata(): Flow<SyncMetadataProjection?> = database.syncMetadata().observeSyncMetadata()
}

private fun <R, T> combineProjectionState(
    rows: Flow<R>,
    cachedRowCount: Flow<Int>,
    metadata: Flow<SyncMetadataProjection?>,
    settings: Flow<ConnectionSettings>,
    refreshStates: Flow<CacheRefreshState>,
    mapper: suspend (R, ConnectionSettings) -> T
): Flow<CacheProjectionState<T>> = combine(
    rows,
    cachedRowCount,
    metadata,
    settings,
    refreshStates
) { currentRows, rowCount, currentMetadata, currentSettings, refreshState ->
    val value = mapper(currentRows, currentSettings)
    val hasCache = rowCount > 0 || currentMetadata != null
    val availability = availability(
        metadata = currentMetadata,
        settings = currentSettings,
        refreshState = refreshState
    )

    when {
        hasCache && refreshState == CacheRefreshState.Refreshing -> CacheProjectionState.ContentRefreshing(
            value = value,
            availability = availability
        )
        hasCache -> CacheProjectionState.Content(value = value, availability = availability)
        refreshState is CacheRefreshState.Failed -> CacheProjectionState.FatalErrorWithoutCache(refreshState.error)
        else -> CacheProjectionState.InitialLoading
    }
}

private fun availability(
    metadata: SyncMetadataProjection?,
    settings: ConnectionSettings,
    refreshState: CacheRefreshState
): CacheAvailability {
    val nonBlockingError = (refreshState as? CacheRefreshState.Failed)?.error
    val mutation = mutationAvailability(metadata = metadata, settings = settings, refreshState = refreshState)
    return CacheAvailability(
        isStale = metadata == null || refreshState != CacheRefreshState.Idle,
        nonBlockingError = nonBlockingError,
        mutation = mutation
    )
}

private fun mutationAvailability(
    metadata: SyncMetadataProjection?,
    settings: ConnectionSettings,
    refreshState: CacheRefreshState
): MutationAvailability = when {
    settings.baseUrl == null -> MutationAvailability.Disabled(MutationDisabledReason.NoConfiguredConnection)
    metadata == null -> MutationAvailability.Disabled(MutationDisabledReason.NoCachedSnapshot)
    refreshState == CacheRefreshState.Refreshing -> MutationAvailability.Disabled(
        MutationDisabledReason.RefreshInProgress
    )
    refreshState is CacheRefreshState.Failed -> MutationAvailability.Disabled(
        refreshState.error.disabledReason()
    )
    else -> MutationAvailability.Available(metadata.domainGeneration())
}

private fun CacheProjectionError.disabledReason(): MutationDisabledReason = when (this) {
    CacheProjectionError.NoConfiguredConnection -> MutationDisabledReason.NoConfiguredConnection
    is CacheProjectionError.UnsupportedTopology -> MutationDisabledReason.UnsupportedTopology
    is CacheProjectionError.RefreshFailed -> when (reason) {
        RefreshFailureReason.Offline -> MutationDisabledReason.Offline
        RefreshFailureReason.AuthenticationRejected -> MutationDisabledReason.AuthenticationRejected
        else -> MutationDisabledReason.LastRefreshFailed
    }
}

private fun PrinterListProjection.toFeatureProjection(defaultPrinterId: PrinterId?): PrinterSummaryProjection =
    PrinterSummaryProjection(
        id = toDomainPrinter().id,
        name = name,
        externalSlotCount = externalSlotCount,
        assignedSlotCount = assignedSlotCount,
        isDefault = toDomainPrinter().id == defaultPrinterId
    )

private fun PrinterSlotAssignmentProjection.toFeatureProjection(): PrinterSlotProjection = PrinterSlotProjection(
    slot = toDomainSlot().key,
    printerName = printerName,
    kind = toDomainSlot().kind,
    label = label,
    assignedSpool = assignedSpoolId?.let {
        AssignedSpoolProjection(
            id = SpoolId.from(it) ?: error("Persisted spool ID must be positive"),
            name = assignedSpoolName,
            material = assignedMaterial,
            colorName = assignedColorName
        )
    }
)

private fun SpoolListProjection.toFeatureProjection(): SpoolSummaryProjection = SpoolSummaryProjection(
    id = toDomainSpool().id,
    name = displayName,
    manufacturer = manufacturer,
    material = material,
    colorName = colorName,
    remainingGrams = remainingGrams,
    archivedAtEpochMillis = archivedAtEpochMillis,
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    assignedSlot = assignedPrinterId?.let { printerId ->
        val amsId = assignedAmsId ?: error("Assigned spool row must include ams_id")
        val trayId = assignedTrayId ?: error("Assigned spool row must include tray_id")
        val id = PrinterId.from(printerId) ?: error("Persisted printer ID must be positive")
        AssignedSlotProjection(
            printerId = id,
            printerName = assignedPrinterName,
            slot = SlotKey.from(printerId = id, amsId = amsId, trayId = trayId)
                ?: error("Persisted SlotKey coordinates must be valid")
        )
    }
)

private fun SpoolSearchQuery.normalized(): SpoolSearchQuery = copy(rawText = rawText.trim())

private fun String.toFtsQueryOrNull(): String? {
    if (isBlank()) return null
    return splitToSequence(Regex("\\s+"))
        .map { it.filter(::isAllowedFtsCharacter) }
        .filter { it.isNotBlank() }
        .joinToString(separator = " ") { "$it*" }
        .ifBlank { null }
}

private fun isAllowedFtsCharacter(character: Char): Boolean =
    character.isLetterOrDigit() || character == '_' || character == '-'

private val DEFAULT_SEARCH_DEBOUNCE: Duration = 150.milliseconds
