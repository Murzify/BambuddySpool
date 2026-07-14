package com.murzify.bambuddyspool.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PrinterEntity::class,
        PrinterSlotEntity::class,
        SpoolEntity::class,
        SpoolSearchEntity::class,
        AssignmentEntity::class,
        SyncMetadataEntity::class
    ],
    version = BAMBUDDY_DATABASE_VERSION,
    exportSchema = true
)
abstract class BambuddyDatabase : RoomDatabase() {
    abstract fun printers(): PrinterDao

    abstract fun printerSlots(): PrinterSlotDao

    abstract fun spools(): SpoolDao

    abstract fun syncMetadata(): SyncMetadataDao

    abstract fun snapshotTransactions(): SnapshotTransactionDao
}

const val BAMBUDDY_DATABASE_VERSION: Int = 2
