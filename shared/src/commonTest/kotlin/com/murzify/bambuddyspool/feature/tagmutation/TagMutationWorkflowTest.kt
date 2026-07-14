package com.murzify.bambuddyspool.feature.tagmutation

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.TagMutationAppliedButUnverified
import com.murzify.bambuddyspool.core.domain.TagMutationAppliedButUnverifiedReason
import com.murzify.bambuddyspool.core.domain.TagMutationOutcome
import com.murzify.bambuddyspool.core.domain.TagMutationSuccess
import com.murzify.bambuddyspool.core.network.BambuddyNetworkError
import com.murzify.bambuddyspool.core.network.BambuddyNetworkResult
import com.murzify.bambuddyspool.core.network.BambuddyRepository
import com.murzify.bambuddyspool.core.nfc.CommonNdefMessage
import com.murzify.bambuddyspool.core.nfc.CommonNdefRecord
import com.murzify.bambuddyspool.core.nfc.NfcReadClassification
import com.murzify.bambuddyspool.core.nfc.NfcReadClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class TagMutationWorkflowTest {
    @Test
    fun emptyLinksButSamePayloadNeverRewritesAndOtherPayloadRequiresOverwriteConfirmation() {
        val empty = read(NfcReadClassification.Empty)
        val spool = spoolId(7)
        assertIs<TagMutationState.LinkReady>(TagMutationWorkflow.selectSpool(empty, spool))

        val same = read(valid(7))
        assertIs<TagMutationState.AlreadyLinked>(TagMutationWorkflow.selectSpool(same, spool))

        val other = read(valid(8))
        assertIs<TagMutationState.OverwriteConfirmation>(TagMutationWorkflow.selectSpool(other, spool))
    }

    @Test
    fun unknownPayloadRequiresOverwriteAndUnreadableTagCannotBeCleared() {
        val unknown = read(
            NfcReadClassification.UnknownPayload(
                com.murzify.bambuddyspool.core.nfc.UnknownNfcPayloadReason.UnsupportedRecordType
            )
        )
        assertIs<TagMutationState.OverwriteConfirmation>(TagMutationWorkflow.selectSpool(unknown, spoolId(7)))

        val unsupported = read(
            NfcReadClassification.UnsupportedTag(
                com.murzify.bambuddyspool.core.nfc.UnsupportedTagReason.NdefUnavailable
            )
        )
        assertIs<TagMutationState.Failed>(TagMutationWorkflow.requestClear(unsupported))
    }

    @Test
    fun confirmedLinkFreshValidatesThenUsesOnlyPhysicalWriterAndNoBambuddyPost() = runTest {
        val repository = FakeRepository()
        val writer = FakeWriter(TagMutationSuccess(spoolId(7)))
        val controller = TagMutationWorkflowController(FreshTagMutationValidator(repository), writer)
        val result = controller.confirm(TagMutationWorkflow.selectSpool(read(NfcReadClassification.Empty), spoolId(7)))

        assertIs<TagMutationState.Succeeded>(result)
        assertEquals(1, repository.authReads)
        assertEquals(1, repository.spoolReads)
        assertEquals(0, repository.posts)
        assertEquals(1, writer.calls)
    }

    @Test
    fun deletedSpoolAndOfflineClearFailBeforePhysicalWrite() = runTest {
        val deletedRepository = FakeRepository(
            spoolResult = BambuddyNetworkResult.Failure(BambuddyNetworkError.HttpClientError(404))
        )
        val deletedWriter = FakeWriter(TagMutationSuccess(spoolId(7)))
        val deleted = TagMutationWorkflowController(FreshTagMutationValidator(deletedRepository), deletedWriter)
            .confirm(TagMutationWorkflow.selectSpool(read(NfcReadClassification.Empty), spoolId(7)))
        assertEquals(TagMutationFailure.SpoolDeleted, assertIs<TagMutationState.Failed>(deleted).reason)
        assertEquals(0, deletedWriter.calls)

        val offlineRepository = FakeRepository(
            authResult = BambuddyNetworkResult.Failure(BambuddyNetworkError.MissingCredential)
        )
        val offlineWriter = FakeWriter(TagMutationSuccess(null))
        val offline = TagMutationWorkflowController(FreshTagMutationValidator(offlineRepository), offlineWriter)
            .confirm(TagMutationWorkflow.requestClear(read(valid(7))))
        assertEquals(TagMutationFailure.FreshValidationFailed, assertIs<TagMutationState.Failed>(offline).reason)
        assertEquals(0, offlineWriter.calls)
    }

    @Test
    fun retryRequiresSameTagRereadAndNeverWritesAutomatically() = runTest {
        val writer = FakeWriter(
            TagMutationAppliedButUnverified(TagMutationAppliedButUnverifiedReason.RereadFailed)
        )
        val controller = TagMutationWorkflowController(FreshTagMutationValidator(FakeRepository()), writer)
        val failed = assertIs<TagMutationState.Failed>(
            controller.confirm(
                TagMutationWorkflow.selectSpool(read(NfcReadClassification.Empty), spoolId(7))
            )
        )
        val awaiting = assertIs<TagMutationState.AwaitingReadBeforeRetry>(
            controller.beginRetry(failed)
        )
        val wrong = TagMutationWorkflow.retryRead(awaiting, read(NfcReadClassification.Empty, "other"))
        assertEquals(TagMutationFailure.DifferentTagDetected, assertIs<TagMutationState.Failed>(wrong).reason)
        assertEquals(1, writer.calls)

        val ready = TagMutationWorkflow.retryRead(awaiting, read(NfcReadClassification.Empty))
        assertIs<TagMutationState.RetryReady>(ready)
        assertEquals(1, writer.calls)
    }

    private fun read(classification: NfcReadClassification, fingerprint: String = "tag-1") =
        TagMutationRead(fingerprint, classification)

    private fun valid(id: Long): NfcReadClassification = NfcReadClassifier.classify(
        CommonNdefMessage(listOf(CommonNdefRecord.Uri("bambuddy-spool://spool/$id")))
    )
}

private class FakeWriter(private val outcome: TagMutationOutcome) : TagMutationWriter {
    var calls = 0
    override suspend fun mutate(expectedFingerprint: String, operation: TagMutationOperation): TagMutationOutcome {
        calls++
        return outcome
    }
}

private class FakeRepository(
    private val authResult: BambuddyNetworkResult<Unit> = BambuddyNetworkResult.Success(Unit),
    private val spoolResult: BambuddyNetworkResult<Spool> = BambuddyNetworkResult.Success(
        Spool(spoolId(7), "PLA", null, null, null, 100)
    )
) : BambuddyRepository {
    var authReads = 0
    var spoolReads = 0
    var posts = 0
    override suspend fun validateAuth(): BambuddyNetworkResult<Unit> = authResult.also { authReads++ }
    override suspend fun getPrinters(): BambuddyNetworkResult<List<Printer>> = error("Not used")
    override suspend fun getPrinterStatus(printerId: PrinterId): BambuddyNetworkResult<PrinterStatus> =
        error("Not used")
    override suspend fun getSpools(includeArchived: Boolean): BambuddyNetworkResult<List<Spool>> = error("Not used")
    override suspend fun getSpool(spoolId: SpoolId): BambuddyNetworkResult<Spool> = spoolResult.also { spoolReads++ }
    override suspend fun getAssignments(printerId: PrinterId?): BambuddyNetworkResult<List<Assignment>> =
        error("Not used")
    override suspend fun createAssignment(command: AssignmentCommand): BambuddyNetworkResult<Assignment> {
        posts++
        error("Tag mutation must never create a Bambuddy assignment")
    }
}

private fun spoolId(value: Long): SpoolId = requireNotNull(SpoolId.from(value))
