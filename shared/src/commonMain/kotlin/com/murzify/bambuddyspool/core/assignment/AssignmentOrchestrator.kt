package com.murzify.bambuddyspool.core.assignment

import com.murzify.bambuddyspool.core.domain.AlreadyAssigned
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
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.topology.SlotMutationTargetResolution
import com.murzify.bambuddyspool.core.topology.SlotTopologyResolution
import com.murzify.bambuddyspool.core.topology.SlotTopologyResolver
import com.murzify.bambuddyspool.feature.assignment.AssignmentIntent
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

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
    data class ConfirmationRequired(val reason: AssignmentConfirmationReason) : AssignmentWorkflowFailure

    /** A POST response is never a success boundary; CODE-007 supplies exact verification. */
    data object VerificationRequired : AssignmentWorkflowFailure
}

/**
 * Isolates the irreversible transition. It owns exactly one initial POST and intentionally does not retry or verify:
 * those policies are added by CODE-007 without allowing a successful HTTP response to become product success.
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
    private val poster: InitialAssignmentPoster = InitialAssignmentPoster(repository::createAssignment)
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
            is AssignmentPreflight.AlreadyAssigned -> preflight.result
            is AssignmentPreflight.Blocked -> AssignmentResult.Failure(preflight.failure)
            is AssignmentPreflight.ConfirmationRequired -> AssignmentResult.Failure(
                AssignmentWorkflowFailure.ConfirmationRequired(preflight.reason)
            )
            is AssignmentPreflight.Ready -> {
                // The immutable command is captured once: retries must reuse it, never a later mutable UI/cache value.
                val command = preflight.command
                when (val post = applicationScope.async { poster.post(command) }.await()) {
                    is BambuddyNetworkResult.Failure -> return AssignmentResult.Failure(
                        AssignmentWorkflowFailure.Network(post.error)
                    )
                    is BambuddyNetworkResult.Success -> Unit
                }
                // Deliberately fail closed until exact GET verification is performed by the next stage.
                AssignmentResult.Failure(AssignmentWorkflowFailure.VerificationRequired)
            }
        }

    private fun <T> BambuddyNetworkResult<T>.preflightValue(): T? = (this as? BambuddyNetworkResult.Success)?.value

    private fun networkFailure(result: BambuddyNetworkResult<*>): DomainFailure = when (result) {
        is BambuddyNetworkResult.Failure -> AssignmentWorkflowFailure.Network(result.error)
        is BambuddyNetworkResult.Success -> error("A successful result cannot be converted into a failure.")
    }
}
