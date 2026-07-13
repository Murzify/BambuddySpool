package com.murzify.bambuddyspool.core.sync

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterSlot
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
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

class AtomicSnapshotSynchronizer(
    private val repository: BambuddyRepository,
    private val store: SnapshotStore,
    private val clock: SyncClock,
    private val scope: CoroutineScope,
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
            return invalid(validationFailure)
        }

        val statusByPrinter = statuses.associateBy { it.printer.id }
        val slots = printers.flatMap { printer ->
            resolveKnownExternalSlots(printer = printer, status = statusByPrinter.getValue(printer.id))
        }

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
    ): String? {
        val printerIds = printers.map { it.id }.toSet()
        val spoolIds = spools.map { it.id }.toSet()
        val statusByPrinter = statuses.associateBy { it.printer.id }
        val statusSetMatchesPrinters = statusByPrinter.size == printers.size && statusByPrinter.keys == printerIds
        val slots = if (statusSetMatchesPrinters) {
            printers.flatMap { printer ->
                resolveKnownExternalSlots(printer = printer, status = statusByPrinter.getValue(printer.id))
            }
        } else {
            emptyList()
        }
        val slotKeys = slots.map { it.key }.toSet()
        val assignmentSlots = assignments.map { it.slot }.toSet()
        var failure: String? = when {
            printerIds.size != printers.size -> "Duplicate printer IDs in snapshot"
            spoolIds.size != spools.size -> "Duplicate spool IDs in snapshot"
            !statusSetMatchesPrinters -> "Printer status set does not match printer list"
            slotKeys.size != slots.size -> "Duplicate slot keys in snapshot"
            assignmentSlots.size != assignments.size -> "Duplicate assignments for one slot"
            else -> null
        }
        if (failure == null) {
            failure = assignments.firstNotNullOfOrNull { assignment ->
                when {
                    assignment.slot.printerId !in printerIds -> "Assignment references an unknown printer"
                    assignment.spoolId !in spoolIds -> "Assignment references an unknown spool"
                    assignment.slot !in slotKeys -> "Assignment references an unresolved slot"
                    else -> null
                }
            }
        }
        return failure
    }

    private fun resolveKnownExternalSlots(printer: Printer, status: PrinterStatus): List<PrinterSlot> {
        if (status.printer.id != printer.id) {
            return emptyList()
        }
        return status.virtualTrays
            .filter { it.id == CONFIRMED_A1_EXTERNAL_AMS_ID }
            .mapNotNull { tray ->
                SlotKey.from(
                    printerId = printer.id,
                    amsId = CONFIRMED_A1_EXTERNAL_AMS_ID,
                    trayId = CONFIRMED_A1_EXTERNAL_TRAY_ID
                )?.let { key ->
                    PrinterSlot(
                        key = key,
                        kind = SlotKind.External,
                        label = tray.label ?: CONFIRMED_A1_EXTERNAL_LABEL,
                        assignedSpoolId = null
                    )
                }
            }
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

interface SnapshotStore {
    suspend fun currentGeneration(): SnapshotGeneration

    suspend fun publishSnapshot(
        snapshot: DomainSnapshot,
        onlyIfCurrentGeneration: SnapshotGeneration
    ): SnapshotPublishResult
}

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

private fun invalid(reason: String): SnapshotBuildResult.Failure = SnapshotBuildResult.Failure(
    SnapshotSyncFailure.InvalidSnapshot(reason)
)

private fun returnFailure(failure: SnapshotSyncFailure): Nothing = throw SnapshotSyncAbort(failure)

private class SnapshotSyncAbort(val failure: SnapshotSyncFailure) : Throwable()

private const val MAX_STATUS_CONCURRENCY = 4
private const val CONFIRMED_A1_EXTERNAL_AMS_ID = 255
private const val CONFIRMED_A1_EXTERNAL_TRAY_ID = 0
private const val CONFIRMED_A1_EXTERNAL_LABEL = "External"
