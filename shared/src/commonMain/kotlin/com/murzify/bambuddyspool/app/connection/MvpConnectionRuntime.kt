package com.murzify.bambuddyspool.app.connection

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.projections.CacheProjectionError
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.ObservableConnectionSettingsStore
import com.murzify.bambuddyspool.core.projections.PageRequest
import com.murzify.bambuddyspool.core.projections.PagedResult
import com.murzify.bambuddyspool.core.projections.PrinterSlotProjection
import com.murzify.bambuddyspool.core.projections.PrinterSummaryProjection
import com.murzify.bambuddyspool.core.projections.SpoolSearchQuery
import com.murzify.bambuddyspool.core.projections.SpoolSummaryProjection
import com.murzify.bambuddyspool.core.security.SecretValue
import com.murzify.bambuddyspool.core.security.SecureTokenStore
import com.murzify.bambuddyspool.core.settings.CanonicalBaseUrl
import com.murzify.bambuddyspool.core.settings.ConnectionReplacementService
import com.murzify.bambuddyspool.core.settings.ConnectionReplacementTransaction
import com.murzify.bambuddyspool.core.settings.ConnectionSettings
import com.murzify.bambuddyspool.core.settings.ConnectionSettingsStore
import com.murzify.bambuddyspool.core.settings.ConnectionValidationFailureReason
import com.murzify.bambuddyspool.core.settings.ConnectionValidationResult
import com.murzify.bambuddyspool.core.settings.ConnectionValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Temporary production-graph bridge for the owner-approved MVP sequence.
 *
 * It intentionally has no network transport and no durable token implementation. The form can therefore be
 * navigated and exercised safely, but its Test and Save actions fail closed until SEC-001 and SEC-002 provide the
 * Keystore and transport-policy implementations. Only non-secret settings presentation is observable here.
 */
internal class MvpConnectionRuntime(private val scope: CoroutineScope) :
    ConnectionSettingsStore,
    ObservableConnectionSettingsStore {
    private val mutableSettings = MutableStateFlow(ConnectionSettings.Empty)
    val settings: StateFlow<ConnectionSettings> = mutableSettings.asStateFlow()
    val cache: CacheProjectionRepository = ConnectionAwareEmptyCacheProjectionRepository(settings)
    val form = ConnectionFormComponent(
        service = ConnectionReplacementService(
            settingsStore = this,
            tokenStore = RejectedTokenStore,
            validator = SecurityGateValidator,
            replacementTransaction = RejectedReplacementTransaction
        ),
        scope = scope
    )

    override suspend fun read(): ConnectionSettings = mutableSettings.value

    override suspend fun replace(settings: ConnectionSettings) {
        mutableSettings.value = settings
    }

    override fun observeSettings(): Flow<ConnectionSettings> = settings

    fun close() {
        scope.cancel()
    }
}

/**
 * The production graph observes this cache boundary instead of an unqualified loading placeholder.
 *
 * No snapshot is created until a future secure connection and sync implementation exists. Consequently, a missing
 * connection is an explicit typed cache state while a nominally configured future connection remains loading.
 */
private class ConnectionAwareEmptyCacheProjectionRepository(private val settings: StateFlow<ConnectionSettings>) :
    CacheProjectionRepository {
    override fun observePrinters(): Flow<CacheProjectionState<List<PrinterSummaryProjection>>> = cacheState()

    override fun observePrinterSlots(printerId: PrinterId): Flow<CacheProjectionState<List<PrinterSlotProjection>>> =
        cacheState()

    override fun observeDefaultSpoolPage(
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = cacheState()

    override fun observeSpoolSearch(
        queries: Flow<SpoolSearchQuery>,
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = cacheState()

    override fun observeSpool(spoolId: SpoolId): Flow<CacheProjectionState<SpoolSummaryProjection?>> = cacheState()

    private fun <T> cacheState(): Flow<CacheProjectionState<T>> = settings.map { current ->
        if (current.baseUrl == null) {
            CacheProjectionState.FatalErrorWithoutCache(CacheProjectionError.NoConfiguredConnection)
        } else {
            CacheProjectionState.InitialLoading
        }
    }
}

private object SecurityGateValidator : ConnectionValidator {
    override suspend fun validateConnection(baseUrl: CanonicalBaseUrl, token: SecretValue): ConnectionValidationResult =
        ConnectionValidationResult.Failure(
            ConnectionValidationFailureReason.SecurityPolicyNotReady
        )

    override suspend fun validateToken(
        activeBaseUrl: CanonicalBaseUrl,
        token: SecretValue
    ): ConnectionValidationResult = ConnectionValidationResult.Failure(
        ConnectionValidationFailureReason.SecurityPolicyNotReady
    )
}

/** Never retains a token; it exists only to make an accidental bypass fail loudly during development. */
private object RejectedTokenStore : SecureTokenStore {
    override suspend fun replaceToken(value: SecretValue): Nothing = error("SEC-001 is required before token storage.")

    override suspend fun clearToken(): Nothing = error("SEC-001 is required before token storage.")

    override suspend fun hasToken(): Boolean = false

    override suspend fun currentTokenForReplacement(): SecretValue? = null
}

/** Replacements cannot be committed until the secure durable transaction exists. */
private object RejectedReplacementTransaction : ConnectionReplacementTransaction {
    override suspend fun commit(settings: ConnectionSettings, token: SecretValue): Nothing =
        error("SEC-001 and SEC-002 are required before connection replacement.")
}
