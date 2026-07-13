package com.murzify.bambuddyspool.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Upsert
    suspend fun upsertPrinters(printers: List<PrinterEntity>)

    @Query(OBSERVE_PRINTER_LIST_QUERY)
    fun observePrinters(): Flow<List<PrinterListProjection>>

    @Query("DELETE FROM printers WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deletePrintersBeforeGeneration(snapshotGeneration: Long)
}

@Dao
interface PrinterSlotDao {
    @Upsert
    suspend fun upsertPrinterSlots(slots: List<PrinterSlotEntity>)

    @Query("DELETE FROM printer_slots WHERE printer_id = :printerId")
    suspend fun deleteSlotsForPrinter(printerId: Long)

    @Query("DELETE FROM printer_slots WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deleteSlotsBeforeGeneration(snapshotGeneration: Long)

    @Query(OBSERVE_PRINTER_SLOTS_QUERY)
    fun observePrinterSlots(printerId: Long): Flow<List<PrinterSlotAssignmentProjection>>
}

@Dao
interface SpoolDao {
    @Upsert
    suspend fun upsertSpools(spools: List<SpoolEntity>)

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

    @Query(SEARCH_DEFAULT_SPOOL_LIST_QUERY)
    fun searchDefaultSpools(ftsQuery: String, limit: Int, offset: Int): Flow<List<SpoolListProjection>>

    @Query(OBSERVE_SPOOL_QUERY)
    fun observeSpool(spoolId: Long): Flow<List<SpoolListProjection>>

    @Query("DELETE FROM spools WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deleteSpoolsBeforeGeneration(snapshotGeneration: Long)
}

@Dao
interface AssignmentDao {
    @Upsert
    suspend fun upsertAssignments(assignments: List<AssignmentEntity>)

    @Query("DELETE FROM assignments")
    suspend fun deleteAllAssignments()

    @Query("DELETE FROM assignments WHERE printer_id = :printerId")
    suspend fun deleteAssignmentsForPrinter(printerId: Long)

    @Query("DELETE FROM assignments WHERE snapshot_generation < :snapshotGeneration")
    suspend fun deleteAssignmentsBeforeGeneration(snapshotGeneration: Long)

    @Query(OBSERVE_ASSIGNMENTS_FOR_PRINTER_QUERY)
    fun observeAssignmentsForPrinter(printerId: Long): Flow<List<AssignmentProjection>>

    @Query("SELECT * FROM assignments WHERE spool_id = :spoolId ORDER BY printer_id ASC, ams_id ASC, tray_id ASC")
    fun observeAssignmentsForSpool(spoolId: Long): Flow<List<AssignmentEntity>>

    @Query(FIND_EXACT_ASSIGNMENT_QUERY)
    suspend fun findExactAssignment(printerId: Long, amsId: Int, trayId: Int, spoolId: Long): List<AssignmentProjection>
}

@Dao
interface SyncMetadataDao {
    @Upsert
    suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

    @Query(OBSERVE_SYNC_METADATA_QUERY)
    fun observeSyncMetadata(): Flow<SyncMetadataProjection?>

    @Query(MARK_SUCCESSFUL_SYNC_QUERY)
    suspend fun markSuccessfulSync(syncedAt: Long, updatedAt: Long)
}

@Dao
interface CacheMaintenanceDao {
    @Query("DELETE FROM assignments")
    suspend fun clearAssignments()

    @Query("DELETE FROM printer_slots")
    suspend fun clearPrinterSlots()

    @Query("DELETE FROM spools")
    suspend fun clearSpools()

    @Query("DELETE FROM printers")
    suspend fun clearPrinters()

    @Query("DELETE FROM sync_metadata")
    suspend fun clearSyncMetadata()

    @Transaction
    suspend fun clearDomainSnapshot() {
        clearAssignments()
        clearPrinterSlots()
        clearSpools()
        clearPrinters()
        clearSyncMetadata()
    }
}
