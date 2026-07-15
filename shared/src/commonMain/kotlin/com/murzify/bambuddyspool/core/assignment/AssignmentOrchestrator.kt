package com.murzify.bambuddyspool.core.assignment

import com.murzify.bambuddyspool.core.domain.AlreadyAssigned
import com.murzify.bambuddyspool.core.domain.AssignedAndConfigured
import com.murzify.bambuddyspool.core.domain.AssignedConfigurationPending
import com.murzify.bambuddyspool.core.domain.AssignedInventoryOnly
import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.AssignmentResult
import com.murzify.bambuddyspool.core.domain.DomainFailure
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.StaleOrOfflineReason
import com.murzify.bambuddyspool.core.domain.StaleOrOfflineState
import com.murzify.bambuddyspool.core.domain.UnsupportedTopologyReason
import com.murzify.bambuddyspool.core.domain.VerificationMismatch
import com.murzify.bambuddyspool.core.domain.VerificationMismatchReason
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.performance.AssignmentTiming
import com.murzify.bambuddyspool.core.performance.AssignmentTimingStage
import com.murzify.bambuddyspool.core.performance.NoOpAssignmentTiming
import com.murzify.bambuddyspool.core.topology.SlotMutationTargetResolution
import com.murzify.bambuddyspool.core.topology.SlotTopologyResolution
import com.murzify.bambuddyspool.core.topology.SlotTopologyResolver
import com.murzify.bambuddyspool.feature.assignment.AssignmentIntent
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * Common assignment boundary for both NFC and manual entry. It deliberately owns no saved state, queue, or replay
 * mechanism. A caller cancellation is cooperative while preflighting; once a POST has been scheduled it belongs to
 * [applicationScope], so navigation cannot cancel an ambiguous server mutation before its later verification stage.
 */
interface AssignmentOrchestrator {
    suspend fun preflight(intent: AssignmentIntent): AssignmentPreflight

    suspend fun execute(intent: AssignmentIntent): AssignmentResult
}

/** Freshness authority below presentation code. An unavailable or mismatching generation is mutation-denying. */
fun interface AssignmentFreshnessGate {
    suspend fun validate(expectedGeneration: SnapshotGeneration): AssignmentFreshness
}

sealed interface AssignmentFreshness {
    data object Fresh : AssignmentFreshness
    data class Blocked(val reason: StaleOrOfflineReason) : AssignmentFreshness
}

/** A fully refreshed, immutable server view used only for one decision. */
data class AssignmentContext(
    val spool: Spool,
    val printers: List<Printer>,
    val targetPrinter: Printer,
    val targetStatus: PrinterStatus,
    val assignments: List<Assignment>,
    val targetSlot: SlotKey
)

sealed interface AssignmentPreflight {
    data class Ready(val command: AssignmentCommand, val context: AssignmentContext) : AssignmentPreflight
    data class AlreadyAssigned(val result: AssignmentResult.Success) : AssignmentPreflight
    data class ConfirmationRequired(val context: AssignmentContext, val reason: AssignmentConfirmationReason) :
        AssignmentPreflight

    data class Blocked(val failure: DomainFailure) : AssignmentPreflight
}

sealed interface AssignmentConfirmationReason {
    data object MultiplePrinters : AssignmentConfirmationReason
    data object MultipleExternalSlots : AssignmentConfirmationReason
    data class MoveRequired(val currentSlots: List<SlotKey>) : AssignmentConfirmationReason
}

/** Typed fail-closed outcomes not represented by the existing server domain model. */
sealed interface AssignmentWorkflowFailure : DomainFailure {
    data class Network(val error: BambuddyNetworkError) : AssignmentWorkflowFailure
    data object TargetPrinterMissing : AssignmentWorkflowFailure
    data object TargetPrinterOffline : AssignmentWorkflowFailure
    data object SlotChanged : AssignmentWorkflowFailure
    data object InconsistentAssignments : AssignmentWorkflowFailure

    /** Another application-scoped assignment already owns the sole mutation boundary. */
    data object MutationInProgress : AssignmentWorkflowFailure
    data class ConfirmationRequired(val reason: AssignmentConfirmationReason) : AssignmentWorkflowFailure
}

/**
 * Isolates the irreversible transition so retries reuse the exact immutable command.
 */
fun interface InitialAssignmentPoster {
    suspend fun post(command: AssignmentCommand): BambuddyNetworkResult<Assignment>
}

@Inject
class DefaultAssignmentOrchestrator(
    private val repository: BambuddyRepository,
    private val topologyResolver: SlotTopologyResolver,
    private val freshnessGate: AssignmentFreshnessGate,
    private val applicationScope: CoroutineScope,
    private val poster: InitialAssignmentPoster = InitialAssignmentPoster(repository::createAssignment),
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val secondaryFeedback: AssignmentSecondaryFeedback? = null,
    /** Process-local engineering hook; it is not a telemetry or diagnostic sink. */
    private val timing: AssignmentTiming = NoOpAssignmentTiming,
    /** Process-local global mutation ownership; it is intentionally neither saved nor replayed. */
    private val mutationMutex: Mutex = Mutex()
) : AssignmentOrchestrator {
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override suspend fun preflight(intent: AssignmentIntent): AssignmentPreflight {
        when (val freshness = freshnessGate.validate(intent.expectedSnapshotGeneration)) {
            AssignmentFreshness.Fresh -> Unit
            is AssignmentFreshness.Blocked -> return AssignmentPreflight.Blocked(StaleOrOfflineState(freshness.reason))
        }

        val spoolResult = repository.getSpool(intent.spoolId)
        val spool = spoolResult.preflightValue()
            ?: return AssignmentPreflight.Blocked(networkFailure(spoolResult))
        val printersResult = repository.getPrinters()
        val printers = printersResult.preflightValue()
            ?: return AssignmentPreflight.Blocked(networkFailure(printersResult))
        val targetPrinter = printers.firstOrNull { it.id == intent.slot.printerId }
            ?: return AssignmentPreflight.Blocked(AssignmentWorkflowFailure.TargetPrinterMissing)
        val targetStatusResult = repository.getPrinterStatus(targetPrinter.id)
        val targetStatus = targetStatusResult.preflightValue()
            ?: return AssignmentPreflight.Blocked(networkFailure(targetStatusResult))
        if (!targetStatus.connected) {
            return AssignmentPreflight.Blocked(AssignmentWorkflowFailure.TargetPrinterOffline)
        }
        val assignmentsResult = repository.getAssignments()
        val assignments = assignmentsResult.preflightValue()
            ?: return AssignmentPreflight.Blocked(networkFailure(assignmentsResult))
        if (assignments.map { it.slot }.toSet().size != assignments.size) {
            return AssignmentPreflight.Blocked(AssignmentWorkflowFailure.InconsistentAssignments)
        }

        val targetResolution = topologyResolver.resolve(targetPrinter, targetStatus, assignments)
        val resolvedSlots = (targetResolution as? SlotTopologyResolution.Supported)?.slots
            ?: return AssignmentPreflight.Blocked((targetResolution as SlotTopologyResolution.Unsupported).failure)
        when (val target = topologyResolver.validateMutationTarget(targetResolution, intent.slot)) {
            is SlotMutationTargetResolution.Blocked -> return AssignmentPreflight.Blocked(
                if (target.failure.reason == UnsupportedTopologyReason.UnknownExternalSlotMapping) {
                    AssignmentWorkflowFailure.SlotChanged
                } else {
                    target.failure
                }
            )
            is SlotMutationTargetResolution.Valid -> Unit
        }

        val context = AssignmentContext(
            spool = spool,
            printers = printers,
            targetPrinter = targetPrinter,
            targetStatus = targetStatus,
            assignments = assignments,
            targetSlot = intent.slot
        )
        val alreadyAssigned = assignments.any { it.spoolId == intent.spoolId && it.slot == intent.slot }
        if (alreadyAssigned) {
            return AssignmentPreflight.AlreadyAssigned(
                AssignmentResult.Success(AlreadyAssigned(intent.spoolId, intent.slot))
            )
        }

        val confirmationReason = when {
            printers.size != 1 -> AssignmentConfirmationReason.MultiplePrinters
            resolvedSlots.count { it.kind == SlotKind.External } != 1 ->
                AssignmentConfirmationReason.MultipleExternalSlots
            else -> assignments.filter { it.spoolId == intent.spoolId }.map { it.slot }
                .filter { it != intent.slot }
                .takeIf { it.isNotEmpty() }
                ?.let(AssignmentConfirmationReason::MoveRequired)
        }
        if (confirmationReason != null) {
            return AssignmentPreflight.ConfirmationRequired(context, confirmationReason)
        }

        return AssignmentPreflight.Ready(
            command = AssignmentCommand.from(
                spoolId = intent.spoolId,
                slot = intent.slot,
                source = intent.source,
                expectedSnapshotGeneration = intent.expectedSnapshotGeneration
            ),
            context = context
        )
    }

    override suspend fun execute(intent: AssignmentIntent): AssignmentResult =
        when (val preflight = preflight(intent)) {
            is AssignmentPreflight.AlreadyAssigned -> preflight.result.also {
                timing.mark(AssignmentTimingStage.ContextRefreshed)
                timing.mark(AssignmentTimingStage.Verified)
                timing.mark(AssignmentTimingStage.Published)
            }
            is AssignmentPreflight.Blocked -> AssignmentResult.Failure(preflight.failure)
            is AssignmentPreflight.ConfirmationRequired -> AssignmentResult.Failure(
                AssignmentWorkflowFailure.ConfirmationRequired(preflight.reason)
            )
            is AssignmentPreflight.Ready -> {
                timing.mark(AssignmentTimingStage.ContextRefreshed)
                val command = preflight.command
                if (!mutationMutex.tryLock()) {
                    AssignmentResult.Failure(AssignmentWorkflowFailure.MutationInProgress)
                } else {
                    // The application-scoped operation must finish ambiguity resolution after its first POST is scheduled.
                    applicationScope.async {
                        try {
                            postThenVerify(command)
                        } finally {
                            mutationMutex.unlock()
                        }
                    }.await().also {
                        timing.mark(AssignmentTimingStage.Published)
                    }
                }
            }
        }

    @Suppress("ReturnCount")
    private suspend fun postThenVerify(command: AssignmentCommand): AssignmentResult {
        for (retryDelay in POST_RETRY_DELAYS_MILLIS) {
            when (val post = poster.post(command)) {
                is BambuddyNetworkResult.Success -> {
                    timing.mark(AssignmentTimingStage.PostCompleted)
                    return verify(command)
                }
                is BambuddyNetworkResult.Failure -> if (!post.error.isRetryablePostFailure()) {
                    return AssignmentResult.Failure(AssignmentWorkflowFailure.Network(post.error))
                }
            }
            wait(retryDelay)
        }

        return when (val finalPost = poster.post(command)) {
            is BambuddyNetworkResult.Success -> {
                timing.mark(AssignmentTimingStage.PostCompleted)
                verify(command)
            }
            is BambuddyNetworkResult.Failure -> AssignmentResult.Failure(
                AssignmentWorkflowFailure.Network(finalPost.error)
            )
        }
    }

    /** Verification is read-only and never transitions back to POST, including after a GET failure or mismatch. */
    @Suppress("ReturnCount")
    private suspend fun verify(command: AssignmentCommand): AssignmentResult {
        var lastReadFailure: BambuddyNetworkError? = null
        for (pollDelay in VERIFY_POLL_DELAYS_MILLIS) {
            if (pollDelay > 0) wait(pollDelay)
            when (val assignmentsResult = repository.getAssignments(command.slot.printerId)) {
                is BambuddyNetworkResult.Failure -> lastReadFailure = assignmentsResult.error
                is BambuddyNetworkResult.Success -> exactVerification(command, assignmentsResult.value)?.let { result ->
                    (result as? AssignmentResult.Success)?.let {
                        timing.mark(AssignmentTimingStage.Verified)
                        secondaryFeedback?.reportVerifiedSuccess(it.outcome)
                    }
                    return result
                }
            }
        }
        lastReadFailure?.let { return AssignmentResult.Failure(AssignmentWorkflowFailure.Network(it)) }
        return AssignmentResult.Failure(
            VerificationMismatch(
                expectedSpoolId = command.spoolId,
                expectedSlot = command.slot,
                reason = VerificationMismatchReason.MissingAssignment
            )
        )
    }

    @Suppress("ReturnCount")
    private fun exactVerification(command: AssignmentCommand, assignments: List<Assignment>): AssignmentResult? {
        val targetAssignments = assignments.filter { it.slot == command.slot }
        if (targetAssignments.size > 1) {
            return AssignmentResult.Failure(
                VerificationMismatch(command.spoolId, command.slot, VerificationMismatchReason.DuplicateSlotAssignments)
            )
        }
        val target = targetAssignments.singleOrNull() ?: return null
        if (target.spoolId != command.spoolId) return null
        val outcome = when {
            target.pendingConfiguration -> AssignedConfigurationPending(command.spoolId, command.slot)
            target.configured -> AssignedAndConfigured(command.spoolId, command.slot)
            else -> AssignedInventoryOnly(command.spoolId, command.slot)
        }
        return AssignmentResult.Success(outcome)
    }

    private fun BambuddyNetworkError.isRetryablePostFailure(): Boolean = when (this) {
        is BambuddyNetworkError.HttpServerError -> statusCode in 500..599
        is BambuddyNetworkError.Transport -> reason in RETRYABLE_TRANSPORT_FAILURES
        else -> false
    }

    private fun <T> BambuddyNetworkResult<T>.preflightValue(): T? = (this as? BambuddyNetworkResult.Success)?.value

    private fun networkFailure(result: BambuddyNetworkResult<*>): DomainFailure = when (result) {
        is BambuddyNetworkResult.Failure -> AssignmentWorkflowFailure.Network(result.error)
        is BambuddyNetworkResult.Success -> error("A successful result cannot be converted into a failure.")
    }
}

private val POST_RETRY_DELAYS_MILLIS = listOf(500L, 1_000L, 2_000L)
private val VERIFY_POLL_DELAYS_MILLIS = listOf(0L, 200L, 500L)
private val RETRYABLE_TRANSPORT_FAILURES = setOf(
    com.murzify.bambuddyspool.core.network.TransportFailureReason.ConnectTimeout,
    com.murzify.bambuddyspool.core.network.TransportFailureReason.RequestTimeout,
    com.murzify.bambuddyspool.core.network.TransportFailureReason.NetworkUnavailable
)
