package com.murzify.bambuddyspool.feature.tagmutation

import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.TagMutationOutcome
import com.murzify.bambuddyspool.core.domain.TagMutationSuccess
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.nfc.CanonicalNfcPayloadCodec
import com.murzify.bambuddyspool.core.nfc.NfcReadClassification

/** A platform-neutral read observation retained only for the live tag-mutation workflow. */
data class TagMutationRead(val fingerprint: String, val classification: NfcReadClassification) {
    init {
        require(fingerprint.isNotBlank()) { "NFC fingerprints must not be blank." }
    }
}

sealed interface TagMutationOperation {
    data class Link(val spoolId: SpoolId, val canonicalUri: String) : TagMutationOperation
    data object Clear : TagMutationOperation
}

/** Platform boundary. Implementations must reject a fingerprint mismatch before a physical write. */
interface TagMutationWriter {
    suspend fun mutate(expectedFingerprint: String, operation: TagMutationOperation): TagMutationOutcome
}

sealed interface TagMutationState {
    data object Idle : TagMutationState
    data class LinkReady(val read: TagMutationRead, val spoolId: SpoolId) : TagMutationState
    data class OverwriteConfirmation(val read: TagMutationRead, val spoolId: SpoolId) : TagMutationState
    data class ClearConfirmation(val read: TagMutationRead) : TagMutationState
    data class AlreadyLinked(val read: TagMutationRead, val spoolId: SpoolId) : TagMutationState
    data class AwaitingReadBeforeRetry(val operation: TagMutationOperation, val expectedFingerprint: String) :
        TagMutationState
    data class RetryReady(val read: TagMutationRead, val operation: TagMutationOperation) : TagMutationState
    data class Succeeded(val operation: TagMutationOperation) : TagMutationState
    data class Failed(
        val reason: TagMutationFailure,
        val retry: TagMutationOperation? = null,
        val fingerprint: String? = null
    ) : TagMutationState
}

enum class TagMutationFailure {
    TagUnreadable,
    DifferentTagDetected,
    FreshValidationFailed,
    SpoolDeleted,
    PhysicalWriteUnverified
}

/**
 * Pure authorization policy for link, overwrite, clear, and physical retry. It deliberately has no saved state:
 * process recreation drops every confirmation and fingerprint-bound authorization.
 */
object TagMutationWorkflow {
    fun selectSpool(read: TagMutationRead, spoolId: SpoolId): TagMutationState = when (
        val value = read.classification
    ) {
        NfcReadClassification.Empty -> TagMutationState.LinkReady(read, spoolId)
        is NfcReadClassification.ValidSpoolPayload -> if (value.spoolId == spoolId) {
            TagMutationState.AlreadyLinked(read, spoolId)
        } else {
            TagMutationState.OverwriteConfirmation(read, spoolId)
        }
        is NfcReadClassification.UnknownPayload,
        is NfcReadClassification.MalformedNdef -> TagMutationState.OverwriteConfirmation(read, spoolId)
        is NfcReadClassification.UnsupportedTag,
        is NfcReadClassification.ReadFailure -> TagMutationState.Failed(TagMutationFailure.TagUnreadable)
    }

    fun requestClear(read: TagMutationRead): TagMutationState = when (read.classification) {
        is NfcReadClassification.UnsupportedTag,
        is NfcReadClassification.ReadFailure -> TagMutationState.Failed(TagMutationFailure.TagUnreadable)
        else -> TagMutationState.ClearConfirmation(read)
    }

    fun retryRead(state: TagMutationState.AwaitingReadBeforeRetry, read: TagMutationRead): TagMutationState = when {
        read.fingerprint != state.expectedFingerprint ->
            TagMutationState.Failed(TagMutationFailure.DifferentTagDetected)
        read.matches(state.operation) -> TagMutationState.Succeeded(state.operation)
        else -> TagMutationState.RetryReady(read, state.operation)
    }

    fun retryAfter(
        outcome: TagMutationOutcome,
        operation: TagMutationOperation,
        fingerprint: String
    ): TagMutationState = when (outcome) {
        is TagMutationSuccess -> TagMutationState.Succeeded(operation)
        else -> TagMutationState.Failed(
            reason = TagMutationFailure.PhysicalWriteUnverified,
            retry = operation,
            fingerprint = fingerprint
        )
    }
}

/** Fresh online validation boundary. A cached projection is intentionally never accepted for a tag mutation. */
class FreshTagMutationValidator(private val repository: BambuddyRepository) {
    suspend fun validate(operation: TagMutationOperation): TagMutationValidation = when (
        val auth = repository.validateAuth()
    ) {
        is BambuddyNetworkResult.Failure -> TagMutationValidation.OfflineOrInvalid
        is BambuddyNetworkResult.Success -> when (operation) {
            TagMutationOperation.Clear -> TagMutationValidation.Valid(null)
            is TagMutationOperation.Link -> when (val spool = repository.getSpool(operation.spoolId)) {
                is BambuddyNetworkResult.Success -> TagMutationValidation.Valid(spool.value)
                is BambuddyNetworkResult.Failure -> if (
                    spool.error is BambuddyNetworkError.HttpClientError && spool.error.statusCode == NOT_FOUND
                ) {
                    TagMutationValidation.Deleted
                } else {
                    TagMutationValidation.OfflineOrInvalid
                }
            }
        }
    }
}

sealed interface TagMutationValidation {
    data class Valid(val spool: Spool?) : TagMutationValidation
    data object OfflineOrInvalid : TagMutationValidation
    data object Deleted : TagMutationValidation
}

/**
 * Executes only an explicitly confirmed operation. It performs GET-only fresh validation; no Bambuddy mutation is
 * available from this feature. A physical failure never invokes the writer again automatically.
 */
class TagMutationWorkflowController(
    private val validator: FreshTagMutationValidator,
    private val writer: TagMutationWriter
) {
    suspend fun confirm(state: TagMutationState): TagMutationState {
        val operationAndRead = when (state) {
            is TagMutationState.LinkReady -> operation(state.spoolId) to state.read
            is TagMutationState.OverwriteConfirmation -> operation(state.spoolId) to state.read
            is TagMutationState.ClearConfirmation -> TagMutationOperation.Clear to state.read
            is TagMutationState.RetryReady -> state.operation to state.read
            else -> return state
        }
        val (operation, read) = operationAndRead
        return when (validator.validate(operation)) {
            is TagMutationValidation.Valid -> TagMutationWorkflow.retryAfter(
                outcome = writer.mutate(read.fingerprint, operation),
                operation = operation,
                fingerprint = read.fingerprint
            )
            TagMutationValidation.OfflineOrInvalid -> TagMutationState.Failed(TagMutationFailure.FreshValidationFailed)
            TagMutationValidation.Deleted -> TagMutationState.Failed(TagMutationFailure.SpoolDeleted)
        }
    }

    fun beginRetry(state: TagMutationState.Failed): TagMutationState = state.retry?.let { operation ->
        TagMutationState.AwaitingReadBeforeRetry(operation, requireNotNull(state.fingerprint))
    } ?: state

    private fun operation(spoolId: SpoolId): TagMutationOperation.Link = TagMutationOperation.Link(
        spoolId = spoolId,
        canonicalUri = CanonicalNfcPayloadCodec.encode(spoolId)
    )
}

private const val NOT_FOUND = 404

private fun TagMutationRead.matches(operation: TagMutationOperation): Boolean = when (operation) {
    TagMutationOperation.Clear -> classification == NfcReadClassification.Empty
    is TagMutationOperation.Link -> (classification as? NfcReadClassification.ValidSpoolPayload)
        ?.canonicalUri == operation.canonicalUri
}
