package com.murzify.bambuddyspool.core.assignment

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.AssignmentResult
import com.murzify.bambuddyspool.core.domain.AssignmentSource
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.VirtualTray
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.network.TransportFailureReason
import com.murzify.bambuddyspool.core.topology.KnownSlotTopologyResolver
import com.murzify.bambuddyspool.feature.assignment.AssignmentIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentOrchestratorTest {
    @Test
    fun staleOrOfflineGateNeverReadsOrPosts() = runTest {
        val repository = FakeAssignmentRepository()
        val orchestrator = orchestrator(
            repository = repository,
            freshnessGate = AssignmentFreshnessGate {
                AssignmentFreshness.Blocked(com.murzify.bambuddyspool.core.domain.StaleOrOfflineReason.Offline)
            }
        )

        val result = orchestrator.execute(intent())

        assertIs<AssignmentResult.Failure>(result)
        assertEquals(0, repository.totalReads)
        assertEquals(0, repository.posted.size)
    }

    @Test
    fun changedSlotAndUnsupportedTopologyNeverPost() = runTest {
        val repository = FakeAssignmentRepository(
            status = status(virtualTrayId = 12)
        )
        val result = orchestrator(repository).execute(intent())

        assertIs<AssignmentResult.Failure>(result)
        assertEquals(0, repository.posted.size)
    }

    @Test
    fun moveAndMultiPrinterNeedOneTypedConfirmationWithoutPost() = runTest {
        val otherPrinter = printer(2)
        val repository = FakeAssignmentRepository(
            printers = listOf(printer(1), otherPrinter),
            assignments = listOf(Assignment(spoolId(1), slot(2), configured = true, pendingConfiguration = false))
        )

        val preflight = orchestrator(repository).preflight(intent())

        val required = assertIs<AssignmentPreflight.ConfirmationRequired>(preflight)
        assertEquals(AssignmentConfirmationReason.MultiplePrinters, required.reason)
        assertEquals(0, repository.posted.size)
    }

    @Test
    fun exactFreshAssignmentIsIdempotentWithoutPost() = runTest {
        val repository = FakeAssignmentRepository(
            assignments = listOf(Assignment(spoolId(1), slot(1), configured = true, pendingConfiguration = false))
        )

        val result = orchestrator(repository).execute(intent())

        assertIs<AssignmentResult.Success>(result)
        assertEquals(0, repository.posted.size)
    }

    @Test
    fun successfulHttpPostIsStillVerificationRequiredAndCommandIsImmutable() = runTest {
        val repository = FakeAssignmentRepository()
        val orchestrator = orchestrator(repository)
        val commandIntent = intent()

        val result = orchestrator.execute(commandIntent)

        val failure = assertIs<AssignmentResult.Failure>(result)
        assertEquals(AssignmentWorkflowFailure.VerificationRequired, failure.reason)
        assertEquals(1, repository.posted.size)
        assertEquals(commandIntent.spoolId, repository.posted.single().spoolId)
        assertEquals(commandIntent.slot, repository.posted.single().slot)
    }

    @Test
    fun callerCancellationAfterPostSchedulingDoesNotCancelApplicationScopedPost() = runTest {
        val postStarted = CompletableDeferred<Unit>()
        val allowPostCompletion = CompletableDeferred<Unit>()
        val repository = FakeAssignmentRepository()
        val orchestrator = orchestrator(
            repository = repository,
            poster = InitialAssignmentPoster { command ->
                postStarted.complete(Unit)
                allowPostCompletion.await()
                repository.createAssignment(command)
            }
        )

        val operation = async { orchestrator.execute(intent()) }
        postStarted.await()
        operation.cancelAndJoin()
        allowPostCompletion.complete(Unit)
        runCurrent()

        assertEquals(1, repository.posted.size)
    }

    private fun TestScope.orchestrator(
        repository: FakeAssignmentRepository,
        freshnessGate: AssignmentFreshnessGate = AssignmentFreshnessGate { AssignmentFreshness.Fresh },
        poster: InitialAssignmentPoster = InitialAssignmentPoster(repository::createAssignment)
    ): DefaultAssignmentOrchestrator = DefaultAssignmentOrchestrator(
        repository = repository,
        topologyResolver = KnownSlotTopologyResolver(),
        freshnessGate = freshnessGate,
        applicationScope = this,
        poster = poster
    )
}

private class FakeAssignmentRepository(
    private val printers: List<Printer> = listOf(printer(1)),
    private val spool: Spool = Spool(spoolId(1), "PLA", null, null, null, 100),
    private val status: PrinterStatus = status(),
    private val assignments: List<Assignment> = emptyList()
) : BambuddyRepository {
    var totalReads: Int = 0
        private set
    val posted: MutableList<AssignmentCommand> = mutableListOf()

    override suspend fun validateAuth(): BambuddyNetworkResult<Unit> = BambuddyNetworkResult.Success(Unit)
    override suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>> =
        BambuddyNetworkResult.Success(printers).also { totalReads++ }
    override suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus> =
        BambuddyNetworkResult.Success(status).also { totalReads++ }
    override suspend fun getSpools(includeArchived: Boolean): BambuddyNetworkResult<List<Spool>> =
        BambuddyNetworkResult.Success(listOf(spool))
    override suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool> =
        BambuddyNetworkResult.Success(spool).also { totalReads++ }
    override suspend fun getAssignments(printerId: PrinterId?): BambuddyNetworkResult<List<Assignment>> =
        BambuddyNetworkResult.Success(assignments).also { totalReads++ }
    override suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment> {
        posted += command
        return BambuddyNetworkResult.Success(Assignment(command.spoolId, command.slot, true, false))
    }
}

private fun intent(): AssignmentIntent = AssignmentIntent(
    spoolId = spoolId(1),
    slot = slot(1),
    source = AssignmentSource.Manual,
    expectedSnapshotGeneration = SnapshotGeneration.from(4) ?: error("valid generation")
)

private fun printer(value: Long): Printer = Printer(printerId(value), "P$value")
private fun printerId(value: Long): PrinterId = PrinterId.from(value) ?: error("valid printer")
private fun spoolId(value: Long): SpoolId = SpoolId.from(value) ?: error("valid spool")
private fun slot(printerValue: Long): SlotKey = SlotKey(printerId(printerValue), 255, 0)
private fun status(virtualTrayId: Int = 255): PrinterStatus = PrinterStatus(
    printer = printer(1),
    connected = true,
    virtualTrays = listOf(VirtualTray(virtualTrayId, "External"))
)
