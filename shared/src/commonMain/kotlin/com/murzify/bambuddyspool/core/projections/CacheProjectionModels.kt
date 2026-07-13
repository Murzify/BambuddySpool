package com.murzify.bambuddyspool.core.projections

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.UnsupportedTopology
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError

sealed interface CacheProjectionState<out T> {
    data object InitialLoading : CacheProjectionState<Nothing>
    data class Content<T>(val value: T, val availability: CacheAvailability) : CacheProjectionState<T>
    data class ContentRefreshing<T>(val value: T, val availability: CacheAvailability) : CacheProjectionState<T>
    data class FatalErrorWithoutCache(val error: CacheProjectionError) : CacheProjectionState<Nothing>
}

data class CacheAvailability(
    val isStale: Boolean,
    val nonBlockingError: CacheProjectionError?,
    val mutation: MutationAvailability
)

sealed interface MutationAvailability {
    data class Available(val snapshotGeneration: SnapshotGeneration) : MutationAvailability
    data class Disabled(val reason: MutationDisabledReason) : MutationAvailability
}

enum class MutationDisabledReason {
    NoConfiguredConnection,
    NoCachedSnapshot,
    RefreshInProgress,
    StaleCache,
    LastRefreshFailed,
    AuthenticationRejected,
    Offline,
    UnsupportedTopology
}

sealed interface CacheProjectionError {
    data object NoConfiguredConnection : CacheProjectionError
    data class RefreshFailed(val reason: RefreshFailureReason) : CacheProjectionError
    data class UnsupportedTopology(val failure: com.murzify.bambuddyspool.core.domain.UnsupportedTopology) :
        CacheProjectionError
}

enum class RefreshFailureReason {
    Offline,
    AuthenticationRejected,
    IncompatibleResponse,
    ServerRejected,
    TlsOrSecurityPolicy,
    ResponseTooLarge,
    Unknown
}

sealed interface CacheRefreshState {
    data object Idle : CacheRefreshState
    data object Refreshing : CacheRefreshState
    data class Failed(val error: CacheProjectionError) : CacheRefreshState
}

data class PageRequest(val limit: Int, val offset: Int) {
    init {
        require(limit in 1..MAX_PAGE_LIMIT) { "limit must be in 1..$MAX_PAGE_LIMIT" }
        require(offset >= 0) { "offset must be non-negative" }
    }
}

data class SpoolListFilters(
    val includeInactive: Boolean = false,
    val includeArchived: Boolean = false,
    val includeEmpty: Boolean = false
)

data class SpoolSearchQuery(val rawText: String, val filters: SpoolListFilters = SpoolListFilters())

data class PagedResult<T>(val items: List<T>, val page: PageRequest)

data class PrinterSummaryProjection(
    val id: PrinterId,
    val name: String?,
    val externalSlotCount: Int,
    val assignedSlotCount: Int,
    val isDefault: Boolean
)

data class PrinterSlotProjection(
    val slot: SlotKey,
    val printerName: String?,
    val kind: SlotKind,
    val label: String?,
    val assignedSpool: AssignedSpoolProjection?
)

@Suppress("MaxLineLength")
data class AssignedSpoolProjection(val id: SpoolId, val name: String?, val material: String?, val colorName: String?)

data class SpoolSummaryProjection(
    val id: SpoolId,
    val name: String?,
    val manufacturer: String?,
    val material: String?,
    val colorName: String?,
    val remainingGrams: Int?,
    val archivedAtEpochMillis: Long?,
    val lastUsedAtEpochMillis: Long?,
    val assignedSlot: AssignedSlotProjection?
)

@Suppress("MaxLineLength")
data class AssignedSlotProjection(val printerId: PrinterId, val printerName: String?, val slot: SlotKey)

internal fun BambuddyNetworkError.toCacheProjectionError(): CacheProjectionError = CacheProjectionError.RefreshFailed(
    reason = when (this) {
        BambuddyNetworkError.MissingCredential -> RefreshFailureReason.AuthenticationRejected
        is BambuddyNetworkError.HttpClientError -> if (statusCode == HTTP_UNAUTHORIZED ||
            statusCode == HTTP_FORBIDDEN
        ) {
            RefreshFailureReason.AuthenticationRejected
        } else {
            RefreshFailureReason.ServerRejected
        }
        is BambuddyNetworkError.HttpServerError,
        is BambuddyNetworkError.HttpUnexpectedStatus -> RefreshFailureReason.ServerRejected
        is BambuddyNetworkError.Transport -> RefreshFailureReason.Offline
        is BambuddyNetworkError.Contract -> RefreshFailureReason.IncompatibleResponse
        is BambuddyNetworkError.SecurityPolicy -> RefreshFailureReason.TlsOrSecurityPolicy
        is BambuddyNetworkError.ResponseTooLarge -> RefreshFailureReason.ResponseTooLarge
    }
)

@Suppress("MaxLineLength")
internal fun UnsupportedTopology.toCacheProjectionError(): CacheProjectionError =
    CacheProjectionError.UnsupportedTopology(this)

internal const val MAX_PAGE_LIMIT: Int = 250
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
