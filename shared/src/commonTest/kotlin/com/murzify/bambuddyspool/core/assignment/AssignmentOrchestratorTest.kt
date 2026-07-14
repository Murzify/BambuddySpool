package com.murzify.bambuddyspool.core.assignment

import com.murzify.bambuddyspool.core.domain.AssignedAndConfigured
import com.murzify.bambuddyspool.core.domain.AssignedConfigurationPending
import com.murzify.bambuddyspool.core.domain.AssignedInventoryOnly
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
import com.murzify.bambuddyspool.core.performance.AssignmentTimingStage
import com.murzify.bambuddyspool.core.performance.InMemoryAssignmentTiming
import com.murzify.bambuddyspool.core.platform.ClipboardService
import com.murzify.bambuddyspool.core.platform.HapticsService
import com.murzify.bambuddyspool.core.topology.KnownSlotTopologyResolver
import com.murzify.bambuddyspool.feature.assignment.AssignmentIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun postRetriesUseExactVirtualDelaysWithoutVerificationBeforeRetry() = runTest {
        val repository = FakeAssignmentRepository(
            postResults = listOf(
                failure(BambuddyNetworkError.Transport(TransportFailureReason.ConnectTimeout)),
                failure(BambuddyNetworkError.HttpServerError(503)),
                failure(BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable)),
                successAssignment()
            ),
            verificationResults = listOf(success(listOf(assignment())))
        )
        val orchestrator = orchestrator(repository)
        val commandIntent = intent()

        val operation = async { orchestrator.execute(commandIntent) }
        runCurrent()
        assertEquals(1, repository.posted.size)
        assertEquals(0, repository.verificationReads)
        testScheduler.advanceTimeBy(499)
        assertEquals(1, repository.posted.size)
        testScheduler.advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.posted.size)
        testScheduler.advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, repository.posted.size)
        testScheduler.advanceTimeBy(2_000)
        runCurrent()
        assertEquals(4, repository.posted.size)
        advanceUntilIdle()

        val result = operation.await()

        assertIs<AssignmentResult.Success>(result)
        assertEquals(1, repository.verificationReads)
        assertEquals(3_500, testScheduler.currentTime)
        repository.posted.forEach { posted ->
            assertSame(repository.posted.first(), posted)
            assertEquals(commandIntent.spoolId, posted.spoolId)
            assertEquals(commandIntent.slot, posted.slot)
        }
    }

    @Test
    fun clientContractAndTlsFailuresNeverRetryOrVerify() = runTest {
        listOf(
            BambuddyNetworkError.HttpClientError(409),
            BambuddyNetworkError.Contract(
                com.murzify.bambuddyspool.core.domain.IncompatibleApiResponse(
                    com.murzify.bambuddyspool.core.domain.IncompatibleApiReason.UnexpectedShape
                )
            ),
            BambuddyNetworkError.SecurityPolicy(
                com.murzify.bambuddyspool.core.network.SecurityPolicyFailureReason.TlsValidationFailed
            )
        ).forEach { error ->
            val repository = FakeAssignmentRepository(postResults = listOf(failure(error)))
            val result = orchestrator(repository).execute(intent())

            assertIs<AssignmentResult.Failure>(result)
            assertEquals(1, repository.posted.size)
            assertEquals(0, repository.verificationReads)
        }
    }

    @Test
    fun verificationPollsAtExactTimesAndNeverPostsAgain() = runTest {
        val repository = FakeAssignmentRepository(
            verificationResults = listOf(
                success(emptyList()),
                success(listOf(Assignment(spoolId(2), slot(1), configured = false, pendingConfiguration = false))),
                success(listOf(assignment(configured = false, pending = true)))
            )
        )

        val result = orchestrator(repository).execute(intent())

        val success = assertIs<AssignmentResult.Success>(result)
        assertIs<AssignedConfigurationPending>(success.outcome)
        assertEquals(1, repository.posted.size)
        assertEquals(3, repository.verificationReads)
        assertEquals(700, testScheduler.currentTime)
    }

    @Test
    fun duplicateSlotOrHttpOnlySuccessNeverPassVerification() = runTest {
        val duplicate = listOf(assignment(), assignment())
        val repository = FakeAssignmentRepository(verificationResults = listOf(success(duplicate)))

        val result = orchestrator(repository).execute(intent())

        val failure = assertIs<AssignmentResult.Failure>(result)
        assertIs<com.murzify.bambuddyspool.core.domain.VerificationMismatch>(failure.reason)
        assertEquals(1, repository.posted.size)
        assertEquals(1, repository.verificationReads)
    }

    @Test
    fun verificationReadFailuresUseThePollScheduleWithoutAnotherPost() = runTest {
        val repository = FakeAssignmentRepository(
            verificationResults = listOf(
                failure(BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable)),
                failure(BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable)),
                failure(BambuddyNetworkError.Transport(TransportFailureReason.NetworkUnavailable))
            )
        )

        assertIs<AssignmentResult.Failure>(orchestrator(repository).execute(intent()))

        assertEquals(1, repository.posted.size)
        assertEquals(3, repository.verificationReads)
        assertEquals(700, testScheduler.currentTime)
    }

    @Test
    fun verifiedOutcomesDistinguishConfiguredPendingAndInventoryOnly() = runTest {
        listOf(
            assignment(configured = true, pending = false) to AssignedAndConfigured::class,
            assignment(configured = false, pending = true) to AssignedConfigurationPending::class,
            assignment(configured = false, pending = false) to AssignedInventoryOnly::class
        ).forEach { (verified, expectedType) ->
            val repository = FakeAssignmentRepository(verificationResults = listOf(success(listOf(verified))))
            val result = orchestrator(repository).execute(intent())
            val outcome = assertIs<AssignmentResult.Success>(result).outcome
            assertEquals(expectedType, outcome::class)
        }
    }

    @Test
    fun userRetryIsANewPreflightAndPostCycle() = runTest {
        val repository = FakeAssignmentRepository(
            postResults = listOf(
                failure(BambuddyNetworkError.HttpClientError(409)),
                successAssignment()
            ),
            verificationResults = listOf(success(listOf(assignment())))
        )
        val orchestrator = orchestrator(repository)

        assertIs<AssignmentResult.Failure>(orchestrator.execute(intent()))
        assertIs<AssignmentResult.Success>(orchestrator.execute(intent()))

        assertEquals(2, repository.posted.size)
        // Each call re-runs fresh status/assignment validation rather than replaying a stored command.
        assertEquals(2, repository.preflightAssignmentReads)
        assertEquals(1, repository.verificationReads)
    }

    @Test
    fun hapticAndClipboardFailuresCannotInvalidateVerifiedSuccess() = runTest {
        val feedback = AssignmentSecondaryFeedback(
            haptics = object : HapticsService {
                override fun success() = error("synthetic haptic failure")
            },
            clipboard = object : ClipboardService {
                override fun copyRedacted(text: String): Boolean = error("synthetic clipboard failure")
            }
        )
        val repository = FakeAssignmentRepository(verificationResults = listOf(success(listOf(assignment()))))

        val result = orchestrator(repository, secondaryFeedback = feedback).execute(intent())

        assertIs<AssignmentResult.Success>(result)
        assertEquals(false, feedback.copyRedactedDiagnostic("safe diagnostic"))
    }

    @Test
    fun verifiedAssignmentPublishesOnlyEphemeralStageTiming() = runTest {
        val timing = InMemoryAssignmentTiming()
        val repository = FakeAssignmentRepository(verificationResults = listOf(success(listOf(assignment()))))

        assertIs<AssignmentResult.Success>(orchestrator(repository, timing = timing).execute(intent()))

        assertEquals(
            listOf(
                AssignmentTimingStage.ContextRefreshed,
                AssignmentTimingStage.PostCompleted,
                AssignmentTimingStage.Verified,
                AssignmentTimingStage.Published
            ),
            timing.samples().map { it.stage }
        )
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
        poster: InitialAssignmentPoster = InitialAssignmentPoster(repository::createAssignment),
        secondaryFeedback: AssignmentSecondaryFeedback? = null,
        timing: com.murzify.bambuddyspool.core.performance.AssignmentTiming =
            com.murzify.bambuddyspool.core.performance.NoOpAssignmentTiming
    ): DefaultAssignmentOrchestrator = DefaultAssignmentOrchestrator(
        repository = repository,
        topologyResolver = KnownSlotTopologyResolver(),
        freshnessGate = freshnessGate,
        applicationScope = this,
        poster = poster,
        secondaryFeedback = secondaryFeedback,
        timing = timing
    )
}

private class FakeAssignmentRepository(
    private val printers: List<Printer> = listOf(printer(1)),
    private val spool: Spool = Spool(spoolId(1), "PLA", null, null, null, 100),
    private val status: PrinterStatus = status(),
    private val assignments: List<Assignment> = emptyList(),
    private val postResults: List<BambuddyNetworkResult<Assignment>> = listOf(successAssignment()),
    private val verificationResults: List<BambuddyNetworkResult<List<Assignment>>> = emptyList()
) : BambuddyRepository {
    var totalReads: Int = 0
        private set
    val posted: MutableList<AssignmentCommand> = mutableListOf()
    var verificationReads: Int = 0
        private set
    var preflightAssignmentReads: Int = 0
        private set
    private var postIndex = 0
    private var verificationIndex = 0

    override suspend fun validateAuth(): BambuddyNetworkResult<Unit> = BambuddyNetworkResult.Success(Unit)
    override suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>> =
        BambuddyNetworkResult.Success(printers).also { totalReads++ }
    override suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus> =
        BambuddyNetworkResult.Success(status).also { totalReads++ }
    override suspend fun getSpools(includeArchived: Boolean): BambuddyNetworkResult<List<Spool>> =
        BambuddyNetworkResult.Success(listOf(spool))
    override suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool> =
        BambuddyNetworkResult.Success(spool).also { totalReads++ }
    override suspend fun getAssignments(printerId: PrinterId?): BambuddyNetworkResult<List<Assignment>> {
        totalReads++
        if (printerId == null) {
            preflightAssignmentReads++
            return success(assignments)
        }
        verificationReads++
        return verificationResults.getOrElse(verificationIndex++) { success(emptyList()) }
    }
    override suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment> {
        posted += command
        return postResults.getOrElse(postIndex++) { successAssignment(command) }
    }
}

private fun assignment(configured: Boolean = true, pending: Boolean = false): Assignment =
    Assignment(spoolId(1), slot(1), configured, pending)

private fun successAssignment(command: AssignmentCommand? = null): BambuddyNetworkResult<Assignment> = success(
    Assignment(
        command?.spoolId ?: spoolId(1),
        command?.slot ?: slot(1),
        configured = true,
        pendingConfiguration = false
    )
)

private fun <T> success(value: T): BambuddyNetworkResult<T> = BambuddyNetworkResult.Success(value)
private fun failure(error: BambuddyNetworkError): BambuddyNetworkResult<Nothing> = BambuddyNetworkResult.Failure(error)

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
