package com.murzify.bambuddyspool.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "printers",
    indices = [
        Index(name = "index_printers_snapshot_generation", value = ["snapshot_generation"]),
        Index(name = "index_printers_name", value = ["name"])
    ]
)
data class PrinterEntity(
    @PrimaryKey
    @ColumnInfo(name = "printer_id")
    val printerId: Long,
    @ColumnInfo(name = "name")
    val name: String?,
    @ColumnInfo(name = "snapshot_generation")
    val snapshotGeneration: Long
)

@Suppress("LongParameterList")
@Entity(
    tableName = "printer_slots",
    primaryKeys = ["printer_id", "ams_id", "tray_id"],
    foreignKeys = [
        ForeignKey(
            entity = PrinterEntity::class,
            parentColumns = ["printer_id"],
            childColumns = ["printer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_printer_slots_printer_id", value = ["printer_id"]),
        Index(name = "index_printer_slots_snapshot_generation", value = ["snapshot_generation"])
    ]
)
data class PrinterSlotEntity(
    @ColumnInfo(name = "printer_id")
    val printerId: Long,
    @ColumnInfo(name = "ams_id")
    val amsId: Int,
    @ColumnInfo(name = "tray_id")
    val trayId: Int,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int,
    @ColumnInfo(name = "snapshot_generation")
    val snapshotGeneration: Long
)

@Suppress("LongParameterList")
@Entity(
    tableName = "spools",
    indices = [
        Index(
            name = "index_spools_default_filter_sort",
            value = [
                "is_active",
                "archived_at_epoch_millis",
                "remaining_grams",
                "last_used_at_epoch_millis",
                "normalized_display_name",
                "spool_id"
            ]
        ),
        Index(name = "index_spools_snapshot_generation", value = ["snapshot_generation"])
    ]
)
data class SpoolEntity(
    @PrimaryKey
    @ColumnInfo(name = "spool_id")
    val spoolId: Long,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    @ColumnInfo(name = "normalized_display_name")
    val normalizedDisplayName: String,
    @ColumnInfo(name = "manufacturer")
    val manufacturer: String?,
    @ColumnInfo(name = "material")
    val material: String?,
    @ColumnInfo(name = "color_name")
    val colorName: String?,
    @ColumnInfo(name = "remaining_grams")
    val remainingGrams: Int?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "archived_at_epoch_millis")
    val archivedAtEpochMillis: Long?,
    @ColumnInfo(name = "last_used_at_epoch_millis")
    val lastUsedAtEpochMillis: Long?,
    @ColumnInfo(name = "snapshot_generation")
    val snapshotGeneration: Long
)

@Entity(tableName = "spools_fts")
@Fts4(
    contentEntity = SpoolEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4]
)
data class SpoolSearchEntity(
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    @ColumnInfo(name = "manufacturer")
    val manufacturer: String?,
    @ColumnInfo(name = "material")
    val material: String?,
    @ColumnInfo(name = "color_name")
    val colorName: String?
)

@Suppress("LongParameterList")
@Entity(
    tableName = "assignments",
    foreignKeys = [
        ForeignKey(
            entity = PrinterEntity::class,
            parentColumns = ["printer_id"],
            childColumns = ["printer_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PrinterSlotEntity::class,
            parentColumns = ["printer_id", "ams_id", "tray_id"],
            childColumns = ["printer_id", "ams_id", "tray_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SpoolEntity::class,
            parentColumns = ["spool_id"],
            childColumns = ["spool_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_assignments_slot_key_unique", value = ["printer_id", "ams_id", "tray_id"], unique = true),
        Index(name = "index_assignments_spool_id", value = ["spool_id"]),
        Index(name = "index_assignments_snapshot_generation", value = ["snapshot_generation"])
    ]
)
data class AssignmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "assignment_id")
    val assignmentId: Long,
    @ColumnInfo(name = "spool_id")
    val spoolId: Long,
    @ColumnInfo(name = "printer_id")
    val printerId: Long,
    @ColumnInfo(name = "ams_id")
    val amsId: Int,
    @ColumnInfo(name = "tray_id")
    val trayId: Int,
    @ColumnInfo(name = "configured")
    val configured: Boolean,
    @ColumnInfo(name = "pending_configuration")
    val pendingConfiguration: Boolean,
    @ColumnInfo(name = "snapshot_generation")
    val snapshotGeneration: Long
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "metadata_key")
    val metadataKey: String,
    @ColumnInfo(name = "last_successful_sync_at_epoch_millis")
    val lastSuccessfulSyncAtEpochMillis: Long?,
    @ColumnInfo(name = "snapshot_generation")
    val snapshotGeneration: Long,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int
)

const val SYNC_METADATA_SNAPSHOT_KEY: String = "domain_snapshot"
