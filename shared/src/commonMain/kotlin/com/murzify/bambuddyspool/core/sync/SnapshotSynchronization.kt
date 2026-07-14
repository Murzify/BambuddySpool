package com.murzify.bambuddyspool.core.sync

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterSlot
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.UnsupportedTopology as DomainUnsupportedTopology
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.topology.KnownSlotTopologyResolver
import com.murzify.bambuddyspool.core.topology.SlotTopologyResolution
import com.murzify.bambuddyspool.core.topology.SlotTopologyResolver
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Coordinates complete server-authoritative snapshot refreshes for one application scope.
 *
 * Concurrent triggers join one active refresh. Caller-owned [scope] defines that refresh's lifetime; cancellation
 * remains cooperative and is never translated into a sync failure. Publication is delegated to [SnapshotStore],
 * which must atomically compare and advance the snapshot generation.
 */
class AtomicSnapshotSynchronizer(
    private val repository: BambuddyRepository,
    private val store: SnapshotStore,
    private val clock: SyncClock,
    private val scope: CoroutineScope,
    private val topologyResolver: SlotTopologyResolver = KnownSlotTopologyResolver(),
    private val foregroundDebounce: Duration = 2.seconds,
    private val statusConcurrency: Int = MAX_STATUS_CONCURRENCY
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<SnapshotSyncResult>? = null

    init {
        require(statusConcurrency > 0) { "statusConcurrency must be positive" }
    }

    suspend fun sync(trigger: SnapshotSyncTrigger): SnapshotSyncResult {
        val active = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async {
                runSync(trigger)
            }.also { deferred ->
                inFlight = deferred
            }
        }
        return active.await().also {
            mutex.withLock {
                if (inFlight === active) {
                    inFlight = null
                }
            }
        }
    }

    private suspend fun runSync(trigger: SnapshotSyncTrigger): SnapshotSyncResult {
        try {
            if (trigger == SnapshotSyncTrigger.Foreground) {
                delay(foregroundDebounce)
            }

            val startedGeneration = store.currentGeneration()
            val printers = repository.getPrinters().networkValueOrReturnFailure()
            val statuses = fetchStatuses(printers).valueOrReturnFailure()
            val spools = repository.getSpools(includeArchived = true).networkValueOrReturnFailure()
            val assignments = repository.getAssignments().networkValueOrReturnFailure()

            val snapshot = buildSnapshot(
                printers = printers,
                statuses = statuses,
                spools = spools,
                assignments = assignments,
                updatedAtEpochMillis = clock.nowEpochMillis()
            ).valueOrReturnFailure()

            return when (val publish = store.publishSnapshot(snapshot, onlyIfCurrentGeneration = startedGeneration)) {
                SnapshotPublishResult.Published -> SnapshotSyncResult.Success
                SnapshotPublishResult.StaleGeneration -> SnapshotSyncResult.Failure(SnapshotSyncFailure.StaleGeneration)
                is SnapshotPublishResult.Rejected -> SnapshotSyncResult.Failure(
                    SnapshotSyncFailure.StoreRejected(publish.reason)
                )
            }
        } catch (abort: SnapshotSyncAbort) {
            return SnapshotSyncResult.Failure(abort.failure)
        }
    }

    private suspend fun fetchStatuses(printers: List<Printer>): SnapshotBuildResult<List<PrinterStatus>> =
        coroutineScope {
            val semaphore = Semaphore(statusConcurrency)
            val deferred = printers.map { printer ->
                async {
                    semaphore.withPermit {
                        when (val result = repository.getPrinterStatus(printer.id)) {
                            is BambuddyNetworkResult.Success -> SnapshotBuildResult.Success(result.value)
                            is BambuddyNetworkResult.Failure -> SnapshotBuildResult.Failure(
                                SnapshotSyncFailure.Network(result.error)
                            )
                        }
                    }
                }
            }
            val statuses = mutableListOf<PrinterStatus>()
            for (status in deferred) {
                when (val result = status.await()) {
                    is SnapshotBuildResult.Success -> statuses += result.value
                    is SnapshotBuildResult.Failure -> return@coroutineScope result
                }
            }
            SnapshotBuildResult.Success(statuses)
        }

    private fun buildSnapshot(
        printers: List<Printer>,
        statuses: List<PrinterStatus>,
        spools: List<Spool>,
        assignments: List<Assignment>,
        updatedAtEpochMillis: Long
    ): SnapshotBuildResult<DomainSnapshot> {
        val validationFailure = validateSnapshotInputs(
            printers = printers,
            statuses = statuses,
            spools = spools,
            assignments = assignments
        )
        if (validationFailure != null) {
            return SnapshotBuildResult.Failure(validationFailure)
        }

        val statusByPrinter = statuses.associateBy { it.printer.id }
        val slots = resolveSlots(
            printers = printers,
            statusByPrinter = statusByPrinter,
            assignments = assignments
        ).valueOrReturnFailure()

        return SnapshotBuildResult.Success(
            DomainSnapshot(
                printers = printers,
                slots = slots,
                spools = spools,
                assignments = assignments,
                updatedAtEpochMillis = updatedAtEpochMillis
            )
        )
    }

    private fun validateSnapshotInputs(
        printers: List<Printer>,
        statuses: List<PrinterStatus>,
        spools: List<Spool>,
        assignments: List<Assignment>
    ): SnapshotSyncFailure? {
        val context = SnapshotValidationContext(
            printers = printers,
            statusesByPrinter = statuses.associateBy { it.printer.id },
            spools = spools,
            assignments = assignments
        )
        val structuralFailure = validateSnapshotStructure(context)
        if (structuralFailure != null) {
            return structuralFailure
        }

        return when (
            val resolvedSlots = resolveSlots(
                printers = printers,
                statusByPrinter = context.statusesByPrinter,
                assignments = assignments
            )
        ) {
            is SnapshotBuildResult.Failure -> resolvedSlots.failure
            is SnapshotBuildResult.Success -> validateAssignmentReferences(
                context = context,
                slotKeys = resolvedSlots.value.map { it.key }.toSet()
            )
        }
    }

    private fun validateSnapshotStructure(context: SnapshotValidationContext): SnapshotSyncFailure? = when {
        context.printerIds.size != context.printers.size -> invalid("Duplicate printer IDs in snapshot").failure
        context.spoolIds.size != context.spools.size -> invalid("Duplicate spool IDs in snapshot").failure
        context.statusesByPrinter.size != context.printers.size ||
            context.statusesByPrinter.keys != context.printerIds -> invalid(
            "Printer status set does not match printer list"
        ).failure
        context.assignmentSlots.size != context.assignments.size -> invalid(
            "Duplicate assignments for one slot"
        ).failure
        else -> null
    }

    private fun validateAssignmentReferences(
        context: SnapshotValidationContext,
        slotKeys: Set<SlotKey>
    ): SnapshotSyncFailure? = context.assignments.firstNotNullOfOrNull { assignment ->
        when {
            assignment.slot.printerId !in context.printerIds -> invalid(
                "Assignment references an unknown printer"
            ).failure
            assignment.spoolId !in context.spoolIds -> invalid("Assignment references an unknown spool").failure
            assignment.slot !in slotKeys -> invalid("Assignment references an unresolved slot").failure
            else -> null
        }
    }

    private fun resolveSlots(
        printers: List<Printer>,
        statusByPrinter: Map<PrinterId, PrinterStatus>,
        assignments: List<Assignment>
    ): SnapshotBuildResult<List<PrinterSlot>> {
        val slots = mutableListOf<PrinterSlot>()
        for (printer in printers) {
            val status = statusByPrinter.getValue(printer.id)
            when (val resolution = topologyResolver.resolve(printer, status, assignments)) {
                is SlotTopologyResolution.Supported -> slots += resolution.slots
                is SlotTopologyResolution.Unsupported -> return SnapshotBuildResult.Failure(
                    SnapshotSyncFailure.UnsupportedTopology(resolution.failure)
                )
            }
        }
        return SnapshotBuildResult.Success(slots)
    }

    private fun <T> BambuddyNetworkResult<T>.networkValueOrReturnFailure(): T = when (this) {
        is BambuddyNetworkResult.Success -> value
        is BambuddyNetworkResult.Failure -> returnFailure(SnapshotSyncFailure.Network(error))
    }

    private fun <T> SnapshotBuildResult<T>.valueOrReturnFailure(): T = when (this) {
        is SnapshotBuildResult.Success -> value
        is SnapshotBuildResult.Failure -> returnFailure(failure)
    }
}

/**
 * Atomic persistence boundary for a complete validated domain snapshot.
 *
 * [publishSnapshot] must either publish the entire snapshot while advancing its generation, reject a stale expected
 * generation, or leave the previous cache intact. Implementations must not expose partial snapshots.
 */
interface SnapshotStore {
    suspend fun currentGeneration(): SnapshotGeneration

    suspend fun publishSnapshot(
        snapshot: DomainSnapshot,
        onlyIfCurrentGeneration: SnapshotGeneration
    ): SnapshotPublishResult
}

/** Time source injected to keep synchronization metadata deterministic in tests. */
fun interface SyncClock {
    fun nowEpochMillis(): Long
}

enum class SnapshotSyncTrigger {
    Initial,
    Manual,
    Foreground,
    PostMutation
}

sealed interface SnapshotSyncResult {
    data object Success : SnapshotSyncResult
    data class Failure(val reason: SnapshotSyncFailure) : SnapshotSyncResult
}

sealed interface SnapshotSyncFailure {
    data class Network(val error: BambuddyNetworkError) : SnapshotSyncFailure
    data class InvalidSnapshot(val reason: String) : SnapshotSyncFailure
    data class UnsupportedTopology(val failure: DomainUnsupportedTopology) : SnapshotSyncFailure

    data class StoreRejected(val reason: String) : SnapshotSyncFailure
    data object StaleGeneration : SnapshotSyncFailure
}

data class DomainSnapshot(
    val printers: List<Printer>,
    val slots: List<PrinterSlot>,
    val spools: List<Spool>,
    val assignments: List<Assignment>,
    val updatedAtEpochMillis: Long
)

sealed interface SnapshotPublishResult {
    data object Published : SnapshotPublishResult
    data object StaleGeneration : SnapshotPublishResult
    data class Rejected(val reason: String) : SnapshotPublishResult
}

private sealed interface SnapshotBuildResult<out T> {
    data class Success<T>(val value: T) : SnapshotBuildResult<T>
    data class Failure(val failure: SnapshotSyncFailure) : SnapshotBuildResult<Nothing>
}

private data class SnapshotValidationContext(
    val printers: List<Printer>,
    val statusesByPrinter: Map<PrinterId, PrinterStatus>,
    val spools: List<Spool>,
    val assignments: List<Assignment>
) {
    val printerIds: Set<PrinterId> = printers.map { it.id }.toSet()
    val spoolIds: Set<SpoolId> = spools.map { it.id }.toSet()
    val assignmentSlots: Set<SlotKey> = assignments.map { it.slot }.toSet()
}

private fun invalid(reason: String): SnapshotBuildResult.Failure = SnapshotBuildResult.Failure(
    SnapshotSyncFailure.InvalidSnapshot(reason)
)

private fun returnFailure(failure: SnapshotSyncFailure): Nothing = throw SnapshotSyncAbort(failure)

private class SnapshotSyncAbort(val failure: SnapshotSyncFailure) : Throwable()

private const val MAX_STATUS_CONCURRENCY = 4
