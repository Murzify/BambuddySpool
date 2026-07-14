package com.murzify.bambuddyspool.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query(OBSERVE_PRINTER_LIST_QUERY)
    fun observePrinters(): Flow<List<PrinterListProjection>>

    @Query("SELECT COUNT(*) FROM printers")
    fun observePrinterCount(): Flow<Int>
}

@Dao
interface PrinterSlotDao {
    @Query(OBSERVE_PRINTER_SLOTS_QUERY)
    fun observePrinterSlots(printerId: Long): Flow<List<PrinterSlotAssignmentProjection>>
}

@Dao
interface SpoolDao {
    @Query(OBSERVE_SPOOL_LIST_QUERY)
    fun observeSpools(
        ftsQuery: String?,
        includeInactive: Boolean,
        includeArchived: Boolean,
        includeEmpty: Boolean,
        limit: Int,
        offset: Int
    ): Flow<List<SpoolListProjection>>

    @Query(OBSERVE_DEFAULT_SPOOL_LIST_QUERY)
    fun observeDefaultSpools(limit: Int, offset: Int): Flow<List<SpoolListProjection>>

    @Query(OBSERVE_SPOOL_QUERY)
    fun observeSpool(spoolId: Long): Flow<List<SpoolListProjection>>

    @Query("SELECT COUNT(*) FROM spools")
    fun observeSpoolCount(): Flow<Int>
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE metadata_key = 'domain_snapshot'")
    suspend fun currentSyncMetadata(): SyncMetadataEntity?

    @Query(OBSERVE_SYNC_METADATA_QUERY)
    fun observeSyncMetadata(): Flow<SyncMetadataProjection?>
}

@Dao
@Suppress("TooManyFunctions")
interface SnapshotTransactionDao {
    @Query("SELECT * FROM sync_metadata WHERE metadata_key = 'domain_snapshot'")
    suspend fun currentSyncMetadata(): SyncMetadataEntity?

    @Upsert
    suspend fun upsertPrinters(printers: List<PrinterEntity>)

    @Query("DELETE FROM printer_slots WHERE printer_id = :printerId")
    suspend fun deleteSlotsForPrinter(printerId: Long)

    @Upsert
    suspend fun upsertPrinterSlots(slots: List<PrinterSlotEntity>)

    @Upsert
    suspend fun upsertSpools(spools: List<SpoolEntity>)

    @Query("DELETE FROM assignments")
    suspend fun deleteAllAssignments()

    @Upsert
    suspend fun upsertAssignments(assignments: List<AssignmentEntity>)

    @Query("DELETE FROM assignments WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deleteAssignmentsBeforeGeneration(snapshotGeneration: Long)

    @Query("DELETE FROM printer_slots WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deleteSlotsBeforeGeneration(snapshotGeneration: Long)

    @Query("DELETE FROM spools WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deleteSpoolsBeforeGeneration(snapshotGeneration: Long)

    @Query("DELETE FROM printers WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deletePrintersBeforeGeneration(snapshotGeneration: Long)

    @Upsert
    suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

    @Transaction
    suspend fun publishSnapshot(
        expectedGeneration: Long,
        nextMetadata: SyncMetadataEntity,
        printers: List<PrinterEntity>,
        printerIdsToReplace: List<Long>,
        slots: List<PrinterSlotEntity>,
        spools: List<SpoolEntity>,
        assignments: List<AssignmentEntity>
    ): SnapshotTransactionResult {
        val current = currentSyncMetadata()?.snapshotGeneration ?: 0L
        if (current != expectedGeneration) {
            return SnapshotTransactionResult.StaleGeneration
        }
        val generation = nextMetadata.snapshotGeneration
        upsertPrinters(printers)
        printerIdsToReplace.forEach { deleteSlotsForPrinter(it) }
        upsertPrinterSlots(slots)
        upsertSpools(spools)
        deleteAllAssignments()
        upsertAssignments(assignments)
        deleteAssignmentsBeforeGeneration(generation)
        deleteSlotsBeforeGeneration(generation)
        deleteSpoolsBeforeGeneration(generation)
        deletePrintersBeforeGeneration(generation)
        upsertSyncMetadata(nextMetadata)
        return SnapshotTransactionResult.Published
    }
}

enum class SnapshotTransactionResult {
    Published,
    StaleGeneration
}
