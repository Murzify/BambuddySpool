package com.murzify.bambuddyspool.core.sync

import com.murzify.bambuddyspool.core.database.AssignmentEntity
import com.murzify.bambuddyspool.core.database.BambuddyDatabase
import com.murzify.bambuddyspool.core.database.PrinterEntity
import com.murzify.bambuddyspool.core.database.PrinterSlotEntity
import com.murzify.bambuddyspool.core.database.SYNC_METADATA_SNAPSHOT_KEY
import com.murzify.bambuddyspool.core.database.SnapshotTransactionResult
import com.murzify.bambuddyspool.core.database.SpoolEntity
import com.murzify.bambuddyspool.core.database.SyncMetadataEntity
import com.murzify.bambuddyspool.core.database.toPersistedSlotKind
import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterSlot
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool

class RoomSnapshotStore(private val database: BambuddyDatabase) : SnapshotStore {
    override suspend fun currentGeneration(): SnapshotGeneration = database.syncMetadata()
        .currentSyncMetadata()
        ?.snapshotGeneration
        ?.let { SnapshotGeneration.from(it) }
        ?: INITIAL_GENERATION

    override suspend fun publishSnapshot(
        snapshot: DomainSnapshot,
        onlyIfCurrentGeneration: SnapshotGeneration
    ): SnapshotPublishResult {
        val nextGenerationValue = onlyIfCurrentGeneration.value + 1
        val nextGeneration = SnapshotGeneration.from(nextGenerationValue)
        val result = if (nextGeneration == null) {
            SnapshotPublishResult.Rejected("Snapshot generation overflow")
        } else {
            publishSnapshotWithGeneration(
                snapshot = snapshot,
                onlyIfCurrentGeneration = onlyIfCurrentGeneration,
                generation = nextGeneration.value
            )
        }
        return result
    }

    private suspend fun publishSnapshotWithGeneration(
        snapshot: DomainSnapshot,
        onlyIfCurrentGeneration: SnapshotGeneration,
        generation: Long
    ): SnapshotPublishResult {
        val printerEntities = snapshot.printers.map { it.toEntity(generation, snapshot.updatedAtEpochMillis) }
        val slotEntities = snapshot.slots.mapIndexed { index, slot ->
            slot.toEntity(
                generation = generation,
                updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
                displayOrder = index
            )
        }
        val spoolEntities = snapshot.spools.map { it.toEntity(generation, snapshot.updatedAtEpochMillis) }
        val assignmentEntities = snapshot.assignments.map { assignment ->
            assignment.toEntity(generation, snapshot.updatedAtEpochMillis)
                ?: return SnapshotPublishResult.Rejected("Assignment ID overflow")
        }

        val transactionResult = database.snapshotTransactions().publishSnapshot(
            expectedGeneration = onlyIfCurrentGeneration.value,
            nextMetadata = SyncMetadataEntity(
                metadataKey = SYNC_METADATA_SNAPSHOT_KEY,
                lastSuccessfulSyncAtEpochMillis = snapshot.updatedAtEpochMillis,
                snapshotGeneration = generation,
                schemaVersion = ROOM_SNAPSHOT_SCHEMA_VERSION,
                updatedAtEpochMillis = snapshot.updatedAtEpochMillis
            ),
            printers = printerEntities,
            printerIdsToReplace = snapshot.printers.map { it.id.value },
            slots = slotEntities,
            spools = spoolEntities,
            assignments = assignmentEntities
        )
        return when (transactionResult) {
            SnapshotTransactionResult.Published -> SnapshotPublishResult.Published
            SnapshotTransactionResult.StaleGeneration -> SnapshotPublishResult.StaleGeneration
        }
    }
}

private fun Printer.toEntity(generation: Long, updatedAtEpochMillis: Long): PrinterEntity = PrinterEntity(
    printerId = id.value,
    name = name,
    isActive = true,
    snapshotGeneration = generation,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun PrinterSlot.toEntity(generation: Long, updatedAtEpochMillis: Long, displayOrder: Int): PrinterSlotEntity =
    PrinterSlotEntity(
        printerId = key.printerId.value,
        amsId = key.amsId,
        trayId = key.trayId,
        kind = kind.toPersistedSlotKind().storageValue,
        label = label,
        displayOrder = displayOrder,
        snapshotGeneration = generation,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

private fun Spool.toEntity(generation: Long, updatedAtEpochMillis: Long): SpoolEntity = SpoolEntity(
    spoolId = id.value,
    displayName = name,
    normalizedDisplayName = name.normalizedSearchValue().orEmpty(),
    manufacturer = manufacturer,
    normalizedManufacturer = manufacturer.normalizedSearchValue(),
    material = material,
    normalizedMaterial = material.normalizedSearchValue(),
    colorName = colorName,
    normalizedColorName = colorName.normalizedSearchValue(),
    remainingGrams = remainingGrams,
    isActive = true,
    archivedAtEpochMillis = null,
    lastUsedAtEpochMillis = null,
    snapshotGeneration = generation,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun Assignment.toEntity(generation: Long, updatedAtEpochMillis: Long): AssignmentEntity? {
    val assignmentId = stableAssignmentId(slot.printerId.value, slot.amsId, slot.trayId) ?: return null
    return AssignmentEntity(
        assignmentId = assignmentId,
        spoolId = spoolId.value,
        printerId = slot.printerId.value,
        amsId = slot.amsId,
        trayId = slot.trayId,
        configured = configured,
        pendingConfiguration = pendingConfiguration,
        createdAtEpochMillis = null,
        snapshotGeneration = generation,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}

private fun stableAssignmentId(printerId: Long, amsId: Int, trayId: Int): Long? {
    val slotCoordinate = amsId.toLong() * SLOT_COORDINATE_FACTOR + trayId
    if (printerId > (Long.MAX_VALUE - slotCoordinate) / PRINTER_ASSIGNMENT_FACTOR) {
        return null
    }
    return printerId * PRINTER_ASSIGNMENT_FACTOR + slotCoordinate
}

private fun String?.normalizedSearchValue(): String? = this
    ?.trim()
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

private val INITIAL_GENERATION: SnapshotGeneration = SnapshotGeneration.from(0L)
    ?: error("Initial snapshot generation must be valid")

private const val SLOT_COORDINATE_FACTOR = 1_000L
private const val PRINTER_ASSIGNMENT_FACTOR = 1_000_000L
private const val ROOM_SNAPSHOT_SCHEMA_VERSION = 1
