package com.murzify.bambuddyspool.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Register [all] on every Room database builder that can open an existing snapshot. */
object BambuddyDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(startVersion = 1, endVersion = 2) {
        override fun migrate(connection: SQLiteConnection) {
            V1_TO_V2_STATEMENTS.forEach { statement ->
                connection.prepare(statement).use { it.step() }
            }
        }
    }

    val all: Array<Migration> = arrayOf(MIGRATION_1_2)

    internal val V1_TO_V2_STATEMENTS: List<String> = listOf(
        CREATE_PRINTERS_V2,
        COPY_PRINTERS_V1_TO_V2,
        CREATE_SPOOLS_V2,
        COPY_SPOOLS_V1_TO_V2,
        CREATE_PRINTER_SLOTS_V2,
        COPY_PRINTER_SLOTS_V1_TO_V2,
        CREATE_ASSIGNMENTS_V2,
        COPY_ASSIGNMENTS_V1_TO_V2,
        CREATE_SYNC_METADATA_V2,
        COPY_SYNC_METADATA_V1_TO_V2,
        "DROP TABLE assignments",
        "DROP TABLE printer_slots",
        "DROP TABLE spools_fts",
        "DROP TABLE spools",
        "DROP TABLE printers",
        "DROP TABLE sync_metadata",
        "ALTER TABLE printers_v2 RENAME TO printers",
        "ALTER TABLE spools_v2 RENAME TO spools",
        "ALTER TABLE printer_slots_v2 RENAME TO printer_slots",
        "ALTER TABLE assignments_v2 RENAME TO assignments",
        "ALTER TABLE sync_metadata_v2 RENAME TO sync_metadata",
        CREATE_SPOOLS_FTS,
        REBUILD_SPOOLS_FTS,
        CREATE_INDEX_PRINTERS_SNAPSHOT_GENERATION,
        CREATE_INDEX_PRINTERS_NAME,
        CREATE_INDEX_PRINTER_SLOTS_PRINTER_ID,
        CREATE_INDEX_PRINTER_SLOTS_SNAPSHOT_GENERATION,
        CREATE_INDEX_SPOOLS_DEFAULT_FILTER_SORT,
        CREATE_INDEX_SPOOLS_SNAPSHOT_GENERATION,
        CREATE_INDEX_ASSIGNMENTS_SLOT_KEY_UNIQUE,
        CREATE_INDEX_ASSIGNMENTS_SPOOL_ID,
        CREATE_INDEX_ASSIGNMENTS_SNAPSHOT_GENERATION
    )
}

private const val CREATE_PRINTERS_V2: String = """
CREATE TABLE printers_v2 (
    printer_id INTEGER NOT NULL PRIMARY KEY,
    name TEXT,
    snapshot_generation INTEGER NOT NULL
)
"""

internal const val COPY_PRINTERS_V1_TO_V2: String = """
INSERT INTO printers_v2 (printer_id, name, snapshot_generation)
SELECT printer_id, name, snapshot_generation FROM printers
"""

private const val CREATE_SPOOLS_V2: String = """
CREATE TABLE spools_v2 (
    spool_id INTEGER NOT NULL PRIMARY KEY,
    display_name TEXT,
    normalized_display_name TEXT NOT NULL,
    manufacturer TEXT,
    material TEXT,
    color_name TEXT,
    remaining_grams INTEGER,
    is_active INTEGER NOT NULL,
    archived_at_epoch_millis INTEGER,
    last_used_at_epoch_millis INTEGER,
    snapshot_generation INTEGER NOT NULL
)
"""

internal const val COPY_SPOOLS_V1_TO_V2: String = """
INSERT INTO spools_v2 (
    spool_id, display_name, normalized_display_name, manufacturer, material, color_name,
    remaining_grams, is_active, archived_at_epoch_millis, last_used_at_epoch_millis, snapshot_generation
)
SELECT
    spool_id, display_name, normalized_display_name, manufacturer, material, color_name,
    remaining_grams, is_active, archived_at_epoch_millis, last_used_at_epoch_millis, snapshot_generation
FROM spools
"""

private const val CREATE_PRINTER_SLOTS_V2: String = """
CREATE TABLE printer_slots_v2 (
    printer_id INTEGER NOT NULL,
    ams_id INTEGER NOT NULL,
    tray_id INTEGER NOT NULL,
    kind TEXT NOT NULL,
    label TEXT,
    display_order INTEGER NOT NULL,
    snapshot_generation INTEGER NOT NULL,
    PRIMARY KEY (printer_id, ams_id, tray_id),
    FOREIGN KEY (printer_id) REFERENCES printers_v2(printer_id) ON DELETE CASCADE
)
"""

private const val COPY_PRINTER_SLOTS_V1_TO_V2: String = """
INSERT INTO printer_slots_v2 (
    printer_id, ams_id, tray_id, kind, label, display_order, snapshot_generation
)
SELECT printer_id, ams_id, tray_id, kind, label, display_order, snapshot_generation FROM printer_slots
"""

private const val CREATE_ASSIGNMENTS_V2: String = """
CREATE TABLE assignments_v2 (
    assignment_id INTEGER NOT NULL PRIMARY KEY,
    spool_id INTEGER NOT NULL,
    printer_id INTEGER NOT NULL,
    ams_id INTEGER NOT NULL,
    tray_id INTEGER NOT NULL,
    configured INTEGER NOT NULL,
    pending_configuration INTEGER NOT NULL,
    snapshot_generation INTEGER NOT NULL,
    FOREIGN KEY (printer_id) REFERENCES printers_v2(printer_id) ON DELETE CASCADE,
    FOREIGN KEY (printer_id, ams_id, tray_id) REFERENCES printer_slots_v2(printer_id, ams_id, tray_id)
        ON DELETE CASCADE,
    FOREIGN KEY (spool_id) REFERENCES spools_v2(spool_id) ON DELETE CASCADE
)
"""

internal const val COPY_ASSIGNMENTS_V1_TO_V2: String = """
INSERT INTO assignments_v2 (
    assignment_id, spool_id, printer_id, ams_id, tray_id, configured, pending_configuration, snapshot_generation
)
SELECT assignment_id, spool_id, printer_id, ams_id, tray_id, configured, pending_configuration, snapshot_generation
FROM assignments
"""

private const val CREATE_SYNC_METADATA_V2: String = """
CREATE TABLE sync_metadata_v2 (
    metadata_key TEXT NOT NULL PRIMARY KEY,
    last_successful_sync_at_epoch_millis INTEGER,
    snapshot_generation INTEGER NOT NULL,
    schema_version INTEGER NOT NULL
)
"""

internal const val COPY_SYNC_METADATA_V1_TO_V2: String = """
INSERT INTO sync_metadata_v2 (
    metadata_key, last_successful_sync_at_epoch_millis, snapshot_generation, schema_version
)
SELECT metadata_key, last_successful_sync_at_epoch_millis, snapshot_generation, 2
FROM sync_metadata
"""

internal const val REBUILD_SPOOLS_FTS: String = """
INSERT INTO spools_fts (rowid, display_name, manufacturer, material, color_name)
SELECT spool_id, display_name, manufacturer, material, color_name FROM spools
"""
