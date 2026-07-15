@file:Suppress("MaxLineLength", "TooGenericExceptionCaught")

package com.murzify.bambuddyspool.app.connection

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.network.BambuddyCredentialProvider
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.network.ConfiguredBambuddyNetworkSecurityPolicy
import com.murzify.bambuddyspool.core.network.KtorBambuddyRepository
import com.murzify.bambuddyspool.core.network.createBambuddyHttpClient
import com.murzify.bambuddyspool.core.projections.CacheAvailability
import com.murzify.bambuddyspool.core.projections.CacheProjectionRepository
import com.murzify.bambuddyspool.core.projections.CacheProjectionState
import com.murzify.bambuddyspool.core.projections.MutationAvailability
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
import com.murzify.bambuddyspool.core.settings.UrlScheme
import com.murzify.bambuddyspool.core.sync.AtomicSnapshotSynchronizer
import com.murzify.bambuddyspool.core.sync.DomainSnapshot
import com.murzify.bambuddyspool.core.sync.SnapshotPublishResult
import com.murzify.bambuddyspool.core.sync.SnapshotStore
import com.murzify.bambuddyspool.core.sync.SnapshotSyncResult
import com.murzify.bambuddyspool.core.sync.SnapshotSyncTrigger
import com.murzify.bambuddyspool.core.sync.SyncClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owner-authorized read-only MVP bridge.
 *
 * Settings are intentionally process-local until the missing platform DataStore/Room factories are introduced.
 * The API token is never retained here: Android supplies the Keystore-backed [SecureTokenStore].  All repository
 * calls are GET requests performed by [AtomicSnapshotSynchronizer]; assignment and tag mutation paths are not
 * bound to this runtime.
 */
internal class MvpConnectionRuntime(
    private val scope: CoroutineScope,
    private val tokenStore: SecureTokenStore = RejectedTokenStore,
    private val repositorySessions: MvpRepositorySessionFactory = KtorMvpRepositorySessionFactory
) : ConnectionSettingsStore {
    private val mutableSettings = MutableStateFlow(ConnectionSettings.Empty)
    val settings: StateFlow<ConnectionSettings> = mutableSettings.asStateFlow()
    private val snapshotCache = ReadOnlySnapshotCache()
    val cache: CacheProjectionRepository = snapshotCache
    val form = ConnectionFormComponent(
        service = ConnectionReplacementService(
            settingsStore = this,
            tokenStore = tokenStore,
            validator = ReadOnlyMvpValidator(),
            replacementTransaction = ReadOnlyMvpReplacementTransaction()
        ),
        scope = scope
    )

    override suspend fun read(): ConnectionSettings = mutableSettings.value

    override suspend fun replace(settings: ConnectionSettings) {
        mutableSettings.value = settings
    }

    fun close() = scope.cancel()

    private inner class ReadOnlyMvpValidator : ConnectionValidator {
        override suspend fun validateConnection(
            baseUrl: CanonicalBaseUrl,
            token: SecretValue
        ): ConnectionValidationResult {
            // HTTP has no explicit origin consent at Test time, so policy rejects it before credentials are sent.
            val candidate = ConnectionSettings(baseUrl, null, null, null)
            if (baseUrl.scheme == UrlScheme.Http) {
                return ConnectionValidationResult.Failure(ConnectionValidationFailureReason.Unreachable)
            }
            val session = repositorySessions.create(baseUrl, token, candidate)
            return try {
                val result = session.repository.validateAuth()
                result.toValidationResult()
            } finally {
                session.close()
            }
        }

        override suspend fun validateToken(
            activeBaseUrl: CanonicalBaseUrl,
            token: SecretValue
        ): ConnectionValidationResult = validateConnection(activeBaseUrl, token)
    }

    private inner class ReadOnlyMvpReplacementTransaction : ConnectionReplacementTransaction {
        override suspend fun commit(settings: ConnectionSettings, token: SecretValue) {
            val previousSettings = read()
            val previousToken = tokenStore.currentTokenForReplacement()
            tokenStore.replaceToken(token)
            replace(settings)
            try {
                val session = repositorySessions.create(
                    settings.baseUrl ?: error("Connection URL is required."),
                    token,
                    settings
                )
                try {
                    val synchronizer = AtomicSnapshotSynchronizer(
                        repository = session.repository,
                        store = snapshotCache,
                        clock = SyncClock { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                        scope = scope
                    )
                    check(synchronizer.sync(SnapshotSyncTrigger.Initial) is SnapshotSyncResult.Success) {
                        "Initial read-only snapshot synchronization failed."
                    }
                } finally {
                    session.close()
                }
            } catch (failure: Throwable) {
                replace(previousSettings)
                if (previousToken == null) tokenStore.clearToken() else tokenStore.replaceToken(previousToken)
                throw failure
            }
        }
    }
}

/** Test seam for deterministic read-only connection checks; production always supplies the policy-bound Ktor session. */
internal fun interface MvpRepositorySessionFactory {
    fun create(
        baseUrl: CanonicalBaseUrl,
        token: SecretValue,
        settings: ConnectionSettings
    ): MvpRepositorySession
}

internal interface MvpRepositorySession {
    val repository: BambuddyRepository
    fun close()
}

private object KtorMvpRepositorySessionFactory : MvpRepositorySessionFactory {
    override fun create(
        baseUrl: CanonicalBaseUrl,
        token: SecretValue,
        settings: ConnectionSettings
    ): MvpRepositorySession {
        val client = createBambuddyHttpClient()
        return object : MvpRepositorySession {
            override val repository: BambuddyRepository = KtorBambuddyRepository(
                client = client,
                baseUrl = baseUrl,
                credentials = BambuddyCredentialProvider { token },
                securityPolicy = ConfiguredBambuddyNetworkSecurityPolicy(settings)
            )

            override fun close() = client.close()
        }
    }
}

private fun BambuddyNetworkResult<Unit>.toValidationResult(): ConnectionValidationResult = when (this) {
    is BambuddyNetworkResult.Success -> ConnectionValidationResult.Valid
    is BambuddyNetworkResult.Failure -> ConnectionValidationResult.Failure(
        when (error) {
            is BambuddyNetworkError.HttpClientError -> ConnectionValidationFailureReason.AuthenticationRejected
            is BambuddyNetworkError.Contract -> ConnectionValidationFailureReason.IncompatibleResponse
            is BambuddyNetworkError.SecurityPolicy -> ConnectionValidationFailureReason.Unreachable
            else -> ConnectionValidationFailureReason.Unreachable
        }
    )
}

/** Small process-local cache used only while no platform Room factory exists. It never enables mutations. */
private class ReadOnlySnapshotCache : SnapshotStore, CacheProjectionRepository {
    private val mutex = Mutex()
    private val snapshot = MutableStateFlow<DomainSnapshot?>(null)
    private var generation = SnapshotGeneration.from(0) ?: error("Initial generation is invalid.")

    override suspend fun currentGeneration(): SnapshotGeneration = mutex.withLock { generation }

    override suspend fun publishSnapshot(
        snapshot: DomainSnapshot,
        onlyIfCurrentGeneration: SnapshotGeneration
    ): SnapshotPublishResult = mutex.withLock {
        if (generation != onlyIfCurrentGeneration) return@withLock SnapshotPublishResult.StaleGeneration
        generation = SnapshotGeneration.nextAfter(generation) ?: return@withLock SnapshotPublishResult.Rejected("Generation overflow")
        this.snapshot.value = snapshot
        SnapshotPublishResult.Published
    }

    override fun observePrinters(): Flow<CacheProjectionState<List<PrinterSummaryProjection>>> = snapshot.map { value ->
        value?.let { current ->
            content(current.printers.map { printer ->
                PrinterSummaryProjection(
                    id = printer.id,
                    name = printer.name,
                    externalSlotCount = current.slots.count { it.key.printerId == printer.id },
                    assignedSlotCount = current.assignments.count { it.slot.printerId == printer.id },
                    isDefault = false
                )
            })
        } ?: CacheProjectionState.InitialLoading
    }

    override fun observePrinterSlots(printerId: PrinterId): Flow<CacheProjectionState<List<PrinterSlotProjection>>> =
        snapshot.map { value -> value?.let { content(it.slots.filter { slot -> slot.key.printerId == printerId }.map { slot ->
            val assignment = it.assignments.firstOrNull { candidate -> candidate.slot == slot.key }
            PrinterSlotProjection(slot.key, it.printers.firstOrNull { printer -> printer.id == printerId }?.name, slot.kind, slot.label,
                assignment?.let { assigned -> it.spools.firstOrNull { spool -> spool.id == assigned.spoolId }?.let { spool ->
                    com.murzify.bambuddyspool.core.projections.AssignedSpoolProjection(spool.id, spool.name, spool.material, spool.colorName)
                } })
        }) } ?: CacheProjectionState.InitialLoading }

    override fun observeDefaultSpoolPage(page: PageRequest): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> =
        snapshot.map { value -> value?.let { content(it.toSpoolPage(page, "")) } ?: CacheProjectionState.InitialLoading }

    override fun observeSpoolSearch(
        queries: Flow<SpoolSearchQuery>,
        page: PageRequest
    ): Flow<CacheProjectionState<PagedResult<SpoolSummaryProjection>>> = combine(snapshot, queries) { value, query ->
        value?.let { content(it.toSpoolPage(page, query.rawText)) } ?: CacheProjectionState.InitialLoading
    }

    override fun observeSpool(spoolId: SpoolId): Flow<CacheProjectionState<SpoolSummaryProjection?>> = snapshot.map { value ->
        value?.let { content(it.toSpool(spoolId)) } ?: CacheProjectionState.InitialLoading
    }

    private fun DomainSnapshot.toSpoolPage(page: PageRequest, query: String): PagedResult<SpoolSummaryProjection> = PagedResult(
        items = spools.filter { it.name.orEmpty().contains(query, ignoreCase = true) }.drop(page.offset).take(page.limit).map { spool -> toSpool(spool.id) ?: error("Snapshot spool disappeared") },
        page = page
    )

    private fun DomainSnapshot.toSpool(id: SpoolId): SpoolSummaryProjection? = spools.firstOrNull { it.id == id }?.let { spool ->
        SpoolSummaryProjection(spool.id, spool.name, spool.manufacturer, spool.material, spool.colorName, spool.remainingGrams, null, null, null)
    }

    private fun <T> content(value: T): CacheProjectionState.Content<T> = CacheProjectionState.Content(
        value,
        CacheAvailability(false, null, MutationAvailability.Disabled(com.murzify.bambuddyspool.core.projections.MutationDisabledReason.NoCachedSnapshot))
    )
}

private object RejectedTokenStore : SecureTokenStore {
    override suspend fun replaceToken(value: SecretValue): Nothing = error("Android SEC-001 storage is required.")
    override suspend fun clearToken(): Nothing = error("Android SEC-001 storage is required.")
    override suspend fun hasToken(): Boolean = false
    override suspend fun currentTokenForReplacement(): SecretValue? = null
}
