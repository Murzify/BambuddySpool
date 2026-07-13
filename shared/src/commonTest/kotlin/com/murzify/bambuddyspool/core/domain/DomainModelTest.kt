package com.murzify.bambuddyspool.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainModelTest {

    @Test
    fun idsRequirePositiveDatabaseValues() {
        assertEquals(1L, assertNotNull(PrinterId.from(1L)).value)
        assertEquals(9L, assertNotNull(SpoolId.from(9L)).value)

        assertNull(PrinterId.from(0L))
        assertNull(PrinterId.from(-1L))
        assertNull(SpoolId.from(0L))
        assertNull(SpoolId.from(-1L))
    }

    @Test
    fun slotKeyRequiresBoundedCoordinates() {
        val printerId = printerId()

        assertNotNull(SlotKey.from(printerId = printerId, amsId = 0, trayId = 0))
        assertNotNull(SlotKey.from(printerId = printerId, amsId = 255, trayId = 255))

        assertNull(SlotKey.from(printerId = printerId, amsId = -1, trayId = 0))
        assertNull(SlotKey.from(printerId = printerId, amsId = 0, trayId = -1))
        assertNull(SlotKey.from(printerId = printerId, amsId = 256, trayId = 0))
        assertNull(SlotKey.from(printerId = printerId, amsId = 0, trayId = 256))
    }

    @Test
    fun slotKeyEqualityUsesExactPrinterAmsAndTray() {
        val slot = slotKey(amsId = 255, trayId = 0)

        assertEquals(slot, slotKey(amsId = 255, trayId = 0))
        assertNotEquals(slot, slotKey(amsId = 254, trayId = 0))
        assertNotEquals(slot, slotKey(amsId = 255, trayId = 1))
        assertNotEquals(slot, slotKey(printerId = assertNotNull(PrinterId.from(2L)), amsId = 255, trayId = 0))
    }

    @Test
    fun copiedModelsCannotBypassCoordinateOrRemainingValidation() {
        val slot = slotKey(amsId = 255, trayId = 0)
        assertFailsWith<IllegalArgumentException> {
            slot.copy(trayId = -1)
        }

        assertFailsWith<IllegalArgumentException> {
            Spool(
                id = spoolId(),
                name = null,
                manufacturer = null,
                material = null,
                colorName = null,
                remainingGrams = -1
            )
        }
    }

    @Test
    fun assignmentCommandRequiresNonNegativeSnapshotGeneration() {
        assertNull(SnapshotGeneration.from(-1L))

        val generation = assertNotNull(SnapshotGeneration.from(0L))
        val command = AssignmentCommand.from(
            spoolId = spoolId(),
            slot = slotKey(),
            source = AssignmentSource.NfcScan,
            expectedSnapshotGeneration = generation
        )

        assertEquals(generation, command.expectedSnapshotGeneration)
        assertEquals(AssignmentSource.NfcScan, command.source)
    }

    @Test
    fun assignmentResultsExposeRequiredSuccessTaxonomy() {
        val successes = listOf(
            AssignedAndConfigured(spoolId(), slotKey()),
            AssignedConfigurationPending(spoolId(), slotKey()),
            AssignedInventoryOnly(spoolId(), slotKey()),
            AlreadyAssigned(spoolId(), slotKey())
        )

        assertTrue(successes.all { it.spoolId == spoolId() })
        assertTrue(successes.all { it.slot == slotKey() })
        assertIs<AssignmentResult.Success>(AssignmentResult.Success(successes.first()))
    }

    @Test
    fun failuresAreTypedWithoutUiStringsOrRawExceptions() {
        val failures: List<DomainFailure> = listOf(
            IncompatibleApiResponse(IncompatibleApiReason.MissingRequiredField),
            StaleOrOfflineState(StaleOrOfflineReason.Offline),
            UnsupportedTopology(UnsupportedTopologyReason.UnknownExternalSlotMapping),
            VerificationMismatch(
                expectedSpoolId = spoolId(),
                expectedSlot = slotKey(),
                reason = VerificationMismatchReason.MissingAssignment
            )
        )

        assertEquals(4, failures.size)
        assertTrue(failures.all { it !is Throwable })
    }

    @Test
    fun tagMutationOutcomesDistinguishTerminalStates() {
        val outcomes: List<TagMutationOutcome> = listOf(
            TagMutationSuccess(spoolId = spoolId()),
            TagMutationNotApplied(TagMutationNotAppliedReason.TagRemovedBeforeWrite),
            TagMutationAppliedButUnverified(TagMutationAppliedButUnverifiedReason.VerificationInterrupted),
            TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.TagRemovedAfterWriteStarted)
        )

        assertIs<TagMutationSuccess>(outcomes[0])
        assertIs<TagMutationNotApplied>(outcomes[1])
        assertIs<TagMutationAppliedButUnverified>(outcomes[2])
        assertIs<TagMutationOutcomeUnknown>(outcomes[3])
    }

    private fun printerId(): PrinterId = assertNotNull(PrinterId.from(1L))

    private fun spoolId(): SpoolId = assertNotNull(SpoolId.from(3L))

    private fun slotKey(printerId: PrinterId = printerId(), amsId: Int = 255, trayId: Int = 0): SlotKey =
        assertNotNull(SlotKey.from(printerId = printerId, amsId = amsId, trayId = trayId))
}
