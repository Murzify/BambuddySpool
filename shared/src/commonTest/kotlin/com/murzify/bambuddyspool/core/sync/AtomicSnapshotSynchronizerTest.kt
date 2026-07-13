package com.murzify.bambuddyspool.core.sync

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.UnsupportedTopologyReason
import com.murzify.bambuddyspool.core.domain.VirtualTray
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.network.TransportFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AtomicSnapshotSynchronizerTest {
    @Test
    fun partialResponsesNeverPublishAndPreservePreviousSnapshot() = runTest {
        val repository = FakeRepository(
            statusResults = mutableMapOf(
                printerId(1).value to BambuddyNetworkResult.Failure(
                    BambuddyNetworkError.Transport(TransportFailureReason.Unknown)
                )
            )
        )
        val store = FakeSnapshotStore(existingSnapshot = completeSnapshot(spoolValue = 99))
        val synchronizer = synchronizer(repository = repository, store = store)

        val result = synchronizer.sync(SnapshotSyncTrigger.Manual)

        assertIs<SnapshotSyncResult.Failure>(result)
        assertEquals(0, store.publishAttempts)
        assertEquals(99, store.currentSnapshot?.spools?.single()?.id?.value)
    }

    @Test
    fun invalidSnapshotRollsBackAndKeepsPreviousCache() = runTest {
        val repository = FakeRepository(
            assignmentsResult = BambuddyNetworkResult.Success(
                listOf(assignment(spoolId = spoolId(404)))
            )
        )
        val store = FakeSnapshotStore(existingSnapshot = completeSnapshot(spoolValue = 99))
        val synchronizer = synchronizer(repository = repository, store = store)

        val result = synchronizer.sync(SnapshotSyncTrigger.Manual)

        assertIs<SnapshotSyncResult.Failure>(result)
        assertEquals(0, store.publishAttempts)
        assertEquals(99, store.currentSnapshot?.spools?.single()?.id?.value)
    }

    @Test
    fun unsupportedTopologyFailsClosedAndDoesNotPublish() = runTest {
        val repository = FakeRepository(
            statusResults = mutableMapOf(
                printerId(1).value to BambuddyNetworkResult.Success(
                    status(printerValue = 1, virtualTray = VirtualTray(id = 42, label = "Unknown"))
                )
            )
        )
        val store = FakeSnapshotStore(existingSnapshot = completeSnapshot(spoolValue = 99))
        val synchronizer = synchronizer(repository = repository, store = store)

        val result = synchronizer.sync(SnapshotSyncTrigger.Manual)

        val failure = assertIs<SnapshotSyncResult.Failure>(result)
        val topologyFailure = assertIs<SnapshotSyncFailure.UnsupportedTopology>(failure.reason)
        assertEquals(UnsupportedTopologyReason.UnknownExternalSlotMapping, topologyFailure.failure.reason)
        assertEquals(0, store.publishAttempts)
        assertEquals(99, store.currentSnapshot?.spools?.single()?.id?.value)
    }

    @Test
    fun concurrentTriggersJoinOneInFlightSync() = runTest {
        val repository = FakeRepository()
        val store = FakeSnapshotStore()
        val synchronizer = synchronizer(repository = repository, store = store)

        val first = async { synchronizer.sync(SnapshotSyncTrigger.Manual) }
        val second = async { synchronizer.sync(SnapshotSyncTrigger.Manual) }

        assertEquals(SnapshotSyncResult.Success, first.await())
        assertEquals(SnapshotSyncResult.Success, second.await())
        assertEquals(1, repository.printersCalls)
        assertEquals(1, repository.spoolsCalls)
        assertEquals(1, repository.assignmentsCalls)
        assertEquals(1, store.publishAttempts)
    }

    @Test
    fun foregroundTriggersDebounceWithVirtualTime() = runTest {
        val repository = FakeRepository()
        val store = FakeSnapshotStore()
        val synchronizer = synchronizer(repository = repository, store = store)

        val sync = async { synchronizer.sync(SnapshotSyncTrigger.Foreground) }
        runCurrent()

        assertEquals(0, repository.printersCalls)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(0, repository.printersCalls)

        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(SnapshotSyncResult.Success, sync.await())
        assertEquals(1, repository.printersCalls)
    }

    @Test
    fun statusFetchConcurrencyIsBoundedToFour() = runTest {
        val printers = (1L..5L).map { printer(it) }
        val repository = FakeRepository(
            printersResult = BambuddyNetworkResult.Success(printers),
            controlledStatuses = true
        )
        val store = FakeSnapshotStore()
        val synchronizer = synchronizer(repository = repository, store = store)

        val sync = async { synchronizer.sync(SnapshotSyncTrigger.Manual) }
        runCurrent()

        assertEquals(4, repository.maxActiveStatuses)
        assertEquals(setOf(1L, 2L, 3L, 4L), repository.waitingStatusIds())

        repository.completeStatus(1L)
        runCurrent()
        assertEquals(4, repository.maxActiveStatuses)
        assertTrue(5L in repository.waitingStatusIds())

        listOf(2L, 3L, 4L, 5L).forEach(repository::completeStatus)
        runCurrent()

        assertEquals(SnapshotSyncResult.Success, sync.await())
        assertEquals(4, repository.maxActiveStatuses)
    }

    @Test
    fun newerSnapshotDeletesRowsMissingFromLaterServerView() = runTest {
        val repository = FakeRepository()
        val store = FakeSnapshotStore(existingSnapshot = completeSnapshot(spoolValue = 2))
        val synchronizer = synchronizer(repository = repository, store = store)

        assertEquals(SnapshotSyncResult.Success, synchronizer.sync(SnapshotSyncTrigger.Manual))

        assertEquals(listOf(1L), store.currentSnapshot?.spools?.map { it.id.value })
        assertEquals(2, store.currentGeneration().value)
    }

    @Test
    fun olderSyncGenerationCannotOverwritePostMutationState() = runTest {
        val repository = FakeRepository(controlledStatuses = true)
        val store = FakeSnapshotStore()
        val synchronizer = synchronizer(repository = repository, store = store)

        val sync = async { synchronizer.sync(SnapshotSyncTrigger.Manual) }
        runCurrent()
        store.advanceGenerationForMutation()
        repository.completeStatus(1L)
        runCurrent()

        val result = sync.await()

        assertEquals(SnapshotSyncResult.Failure(SnapshotSyncFailure.StaleGeneration), result)
        assertEquals(1, store.publishAttempts)
        assertEquals(0, store.publishedSnapshots.size)
    }

    @Test
    fun archiveInclusiveSpoolFetchIsRequired() = runTest {
        val repository = FakeRepository()
        val synchronizer = synchronizer(repository = repository, store = FakeSnapshotStore())

        assertEquals(SnapshotSyncResult.Success, synchronizer.sync(SnapshotSyncTrigger.Manual))

        assertEquals(listOf(true), repository.includeArchivedRequests)
    }

    private fun TestScope.synchronizer(
        repository: FakeRepository,
        store: FakeSnapshotStore
    ): AtomicSnapshotSynchronizer = AtomicSnapshotSynchronizer(
        repository = repository,
        store = store,
        clock = SyncClock { 123L },
        scope = this,
        foregroundDebounce = 2.seconds
    )
}

private class FakeSnapshotStore(existingSnapshot: DomainSnapshot? = null) : SnapshotStore {
    var currentSnapshot: DomainSnapshot? = existingSnapshot
        private set
    var publishAttempts: Int = 0
        private set
    val publishedSnapshots: MutableList<DomainSnapshot> = mutableListOf()
    private var generation: SnapshotGeneration = SnapshotGeneration.from(if (existingSnapshot == null) 0L else 1L)
        ?: error("Test generation must be valid")

    override suspend fun currentGeneration(): SnapshotGeneration = generation

    override suspend fun publishSnapshot(
        snapshot: DomainSnapshot,
        onlyIfCurrentGeneration: SnapshotGeneration
    ): SnapshotPublishResult {
        publishAttempts += 1
        if (generation != onlyIfCurrentGeneration) {
            return SnapshotPublishResult.StaleGeneration
        }
        generation = SnapshotGeneration.from(generation.value + 1) ?: fail("Test generation overflow")
        currentSnapshot = snapshot
        publishedSnapshots += snapshot
        return SnapshotPublishResult.Published
    }

    fun advanceGenerationForMutation() {
        generation = SnapshotGeneration.from(generation.value + 1) ?: fail("Test generation overflow")
    }
}

private class FakeRepository(
    private val printersResult: BambuddyNetworkResult<List<Printer>> = BambuddyNetworkResult.Success(
        listOf(printer(1))
    ),
    private val spoolsResult: BambuddyNetworkResult<List<Spool>> = BambuddyNetworkResult.Success(listOf(spool(1))),
    private val assignmentsResult: BambuddyNetworkResult<List<Assignment>> = BambuddyNetworkResult.Success(
        listOf(assignment())
    ),
    private val statusResults: MutableMap<Long, BambuddyNetworkResult<PrinterStatus>> = mutableMapOf(),
    private val controlledStatuses: Boolean = false
) : BambuddyRepository {
    var printersCalls = 0
        private set
    var spoolsCalls = 0
        private set
    var assignmentsCalls = 0
        private set
    var maxActiveStatuses = 0
        private set
    val includeArchivedRequests: MutableList<Boolean> = mutableListOf()
    private val waitingStatuses: MutableMap<Long, CompletableDeferred<Unit>> = mutableMapOf()
    private var activeStatuses: Int = 0

    override suspend fun validateAuth(): BambuddyNetworkResult<Unit> = BambuddyNetworkResult.Success(Unit)

    override suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>> {
        printersCalls += 1
        return printersResult
    }

    override suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus> {
        if (controlledStatuses) {
            activeStatuses += 1
            maxActiveStatuses = maxOf(maxActiveStatuses, activeStatuses)
            val gate = CompletableDeferred<Unit>()
            waitingStatuses[printerId.value] = gate
            gate.await()
            activeStatuses -= 1
        }
        return statusResults[printerId.value] ?: BambuddyNetworkResult.Success(status(printerId.value))
    }

    override suspend fun getSpools(includeArchived: Boolean): BambuddyNetworkResult<List<Spool>> {
        spoolsCalls += 1
        includeArchivedRequests += includeArchived
        return spoolsResult
    }

    override suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool> =
        BambuddyNetworkResult.Success(spool(spoolId.value))

    override suspend fun getAssignments(printerId: PrinterId?): BambuddyNetworkResult<List<Assignment>> {
        assignmentsCalls += 1
        return assignmentsResult
    }

    override suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment> =
        BambuddyNetworkResult.Success(assignment(spoolId = command.spoolId, slot = command.slot))

    fun waitingStatusIds(): Set<Long> = waitingStatuses.filterValues { !it.isCompleted }.keys

    fun completeStatus(printerId: Long) {
        waitingStatuses[printerId]?.complete(Unit)
    }
}

private fun completeSnapshot(spoolValue: Long): DomainSnapshot = DomainSnapshot(
    printers = listOf(printer(1)),
    slots = emptyList(),
    spools = listOf(spool(spoolValue)),
    assignments = emptyList(),
    updatedAtEpochMillis = 1L
)

private fun printer(value: Long): Printer = Printer(id = printerId(value), name = "Printer $value")

private fun status(
    printerValue: Long,
    virtualTray: VirtualTray = VirtualTray(id = 255, label = "External")
): PrinterStatus = PrinterStatus(
    printer = printer(printerValue),
    connected = true,
    virtualTrays = listOf(virtualTray)
)

private fun spool(value: Long): Spool = Spool(
    id = spoolId(value),
    name = "Spool $value",
    manufacturer = null,
    material = "PLA",
    colorName = null,
    remainingGrams = 100
)

private fun assignment(spoolId: SpoolId = spoolId(1), slot: SlotKey = slotKey()): Assignment = Assignment(
    spoolId = spoolId,
    slot = slot,
    configured = true,
    pendingConfiguration = false
)

private fun slotKey(printerValue: Long = 1L): SlotKey = SlotKey.from(
    printerId = printerId(printerValue),
    amsId = 255,
    trayId = 0
) ?: error("Test slot key must be valid")

private fun printerId(value: Long): PrinterId = PrinterId.from(value) ?: error("Test printer ID must be valid")

private fun spoolId(value: Long): SpoolId = SpoolId.from(value) ?: error("Test spool ID must be valid")
