package com.murzify.bambuddyspool.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SnapshotTransactionDaoTest {

    @Test
    fun staleGenerationDoesNotWriteAnyPartOfSnapshot() = runTest {
        val dao = RecordingSnapshotTransactionDao(currentGeneration = 2L)

        val result = dao.publishSnapshot(publication(expectedGeneration = 1L, nextGeneration = 2L))

        assertEquals(SnapshotTransactionResult.StaleGeneration, result)
        assertEquals(emptyList(), dao.operations)
    }

    @Test
    fun matchingGenerationPublishesOneCompleteOrderedWriteSet() = runTest {
        val dao = RecordingSnapshotTransactionDao(currentGeneration = 1L)

        val result = dao.publishSnapshot(publication(expectedGeneration = 1L, nextGeneration = 2L))

        assertEquals(SnapshotTransactionResult.Published, result)
        assertEquals(
            listOf(
                "printers",
                "delete-slots:1",
                "slots",
                "spools",
                "delete-assignments",
                "assignments",
                "delete-assignments-before:2",
                "delete-slots-before:2",
                "delete-spools-before:2",
                "delete-printers-before:2",
                "metadata:2"
            ),
            dao.operations
        )
    }

    private fun publication(expectedGeneration: Long, nextGeneration: Long): SnapshotPublication = SnapshotPublication(
        expectedGeneration = expectedGeneration,
        nextMetadata = SyncMetadataEntity(
            metadataKey = SYNC_METADATA_SNAPSHOT_KEY,
            lastSuccessfulSyncAtEpochMillis = 5L,
            snapshotGeneration = nextGeneration,
            schemaVersion = 2
        ),
        printers = listOf(PrinterEntity(printerId = 1L, name = "Printer", snapshotGeneration = nextGeneration)),
        printerIdsToReplace = listOf(1L),
        slots = listOf(
            PrinterSlotEntity(
                printerId = 1L,
                amsId = 255,
                trayId = 0,
                kind = PersistedSlotKind.External.storageValue,
                label = "External",
                displayOrder = 0,
                snapshotGeneration = nextGeneration
            )
        ),
        spools = listOf(
            SpoolEntity(
                spoolId = 1L,
                displayName = "Spool",
                normalizedDisplayName = "spool",
                manufacturer = null,
                material = "PLA",
                colorName = null,
                remainingGrams = 100,
                isActive = true,
                archivedAtEpochMillis = null,
                lastUsedAtEpochMillis = null,
                snapshotGeneration = nextGeneration
            )
        ),
        assignments = listOf(
            AssignmentEntity(
                assignmentId = 1L,
                spoolId = 1L,
                printerId = 1L,
                amsId = 255,
                trayId = 0,
                configured = true,
                pendingConfiguration = false,
                snapshotGeneration = nextGeneration
            )
        )
    )
}

private class RecordingSnapshotTransactionDao(currentGeneration: Long) : SnapshotTransactionDao {
    private val metadata = SyncMetadataEntity(
        metadataKey = SYNC_METADATA_SNAPSHOT_KEY,
        lastSuccessfulSyncAtEpochMillis = null,
        snapshotGeneration = currentGeneration,
        schemaVersion = 2
    )

    val operations = mutableListOf<String>()

    override suspend fun currentSyncMetadata(): SyncMetadataEntity = metadata

    override suspend fun upsertPrinters(printers: List<PrinterEntity>) {
        operations += "printers"
    }

    override suspend fun deleteSlotsForPrinter(printerId: Long) {
        operations += "delete-slots:$printerId"
    }

    override suspend fun upsertPrinterSlots(slots: List<PrinterSlotEntity>) {
        operations += "slots"
    }

    override suspend fun upsertSpools(spools: List<SpoolEntity>) {
        operations += "spools"
    }

    override suspend fun deleteAllAssignments() {
        operations += "delete-assignments"
    }

    override suspend fun upsertAssignments(assignments: List<AssignmentEntity>) {
        operations += "assignments"
    }

    override suspend fun deleteAssignmentsBeforeGeneration(snapshotGeneration: Long) {
        operations += "delete-assignments-before:$snapshotGeneration"
    }

    override suspend fun deleteSlotsBeforeGeneration(snapshotGeneration: Long) {
        operations += "delete-slots-before:$snapshotGeneration"
    }

    override suspend fun deleteSpoolsBeforeGeneration(snapshotGeneration: Long) {
        operations += "delete-spools-before:$snapshotGeneration"
    }

    override suspend fun deletePrintersBeforeGeneration(snapshotGeneration: Long) {
        operations += "delete-printers-before:$snapshotGeneration"
    }

    override suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity) {
        operations += "metadata:${metadata.snapshotGeneration}"
    }
}
