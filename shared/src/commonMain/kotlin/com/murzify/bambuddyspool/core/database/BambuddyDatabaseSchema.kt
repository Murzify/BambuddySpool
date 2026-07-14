package com.murzify.bambuddyspool.core.database

object BambuddyDatabaseSchema {
    const val VERSION: Int = BAMBUDDY_DATABASE_VERSION

    val tables: List<String> = listOf(
        "printers",
        "printer_slots",
        "spools",
        "spools_fts",
        "assignments",
        "sync_metadata"
    )

    val baselineSql: List<String> = listOf(
        CREATE_PRINTERS,
        CREATE_PRINTER_SLOTS,
        CREATE_SPOOLS,
        CREATE_SPOOLS_FTS,
        CREATE_ASSIGNMENTS,
        CREATE_SYNC_METADATA,
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

const val CREATE_PRINTERS: String = """
CREATE TABLE IF NOT EXISTS printers (
    printer_id INTEGER NOT NULL PRIMARY KEY,
    name TEXT,
    snapshot_generation INTEGER NOT NULL
)
"""

const val CREATE_PRINTER_SLOTS: String = """
CREATE TABLE IF NOT EXISTS printer_slots (
    printer_id INTEGER NOT NULL,
    ams_id INTEGER NOT NULL,
    tray_id INTEGER NOT NULL,
    kind TEXT NOT NULL,
    label TEXT,
    display_order INTEGER NOT NULL,
    snapshot_generation INTEGER NOT NULL,
    PRIMARY KEY (printer_id, ams_id, tray_id),
    FOREIGN KEY (printer_id) REFERENCES printers(printer_id) ON DELETE CASCADE
)
"""

const val CREATE_SPOOLS: String = """
CREATE TABLE IF NOT EXISTS spools (
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

const val CREATE_SPOOLS_FTS: String = """
CREATE VIRTUAL TABLE IF NOT EXISTS spools_fts USING fts4 (
    display_name,
    manufacturer,
    material,
    color_name,
    content='spools',
    tokenize=unicode61,
    prefix='2,3,4'
)
"""

const val CREATE_ASSIGNMENTS: String = """
CREATE TABLE IF NOT EXISTS assignments (
    assignment_id INTEGER NOT NULL PRIMARY KEY,
    spool_id INTEGER NOT NULL,
    printer_id INTEGER NOT NULL,
    ams_id INTEGER NOT NULL,
    tray_id INTEGER NOT NULL,
    configured INTEGER NOT NULL,
    pending_configuration INTEGER NOT NULL,
    snapshot_generation INTEGER NOT NULL,
    FOREIGN KEY (printer_id) REFERENCES printers(printer_id) ON DELETE CASCADE,
    FOREIGN KEY (printer_id, ams_id, tray_id) REFERENCES printer_slots(printer_id, ams_id, tray_id)
        ON DELETE CASCADE,
    FOREIGN KEY (spool_id) REFERENCES spools(spool_id) ON DELETE CASCADE
)
"""

const val CREATE_SYNC_METADATA: String = """
CREATE TABLE IF NOT EXISTS sync_metadata (
    metadata_key TEXT NOT NULL PRIMARY KEY,
    last_successful_sync_at_epoch_millis INTEGER,
    snapshot_generation INTEGER NOT NULL,
    schema_version INTEGER NOT NULL
)
"""

const val CREATE_INDEX_PRINTERS_SNAPSHOT_GENERATION: String =
    "CREATE INDEX IF NOT EXISTS index_printers_snapshot_generation ON printers(snapshot_generation)"

const val CREATE_INDEX_PRINTERS_NAME: String = "CREATE INDEX IF NOT EXISTS index_printers_name ON printers(name)"

const val CREATE_INDEX_PRINTER_SLOTS_PRINTER_ID: String =
    "CREATE INDEX IF NOT EXISTS index_printer_slots_printer_id ON printer_slots(printer_id)"

const val CREATE_INDEX_PRINTER_SLOTS_SNAPSHOT_GENERATION: String =
    "CREATE INDEX IF NOT EXISTS index_printer_slots_snapshot_generation ON printer_slots(snapshot_generation)"

const val CREATE_INDEX_SPOOLS_DEFAULT_FILTER_SORT: String =
    "CREATE INDEX IF NOT EXISTS index_spools_default_filter_sort ON spools(is_active, archived_at_epoch_millis, " +
        "remaining_grams, last_used_at_epoch_millis, normalized_display_name, spool_id)"

const val CREATE_INDEX_SPOOLS_SNAPSHOT_GENERATION: String =
    "CREATE INDEX IF NOT EXISTS index_spools_snapshot_generation ON spools(snapshot_generation)"

const val CREATE_INDEX_ASSIGNMENTS_SLOT_KEY_UNIQUE: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS index_assignments_slot_key_unique ON assignments(printer_id, ams_id, tray_id)"

const val CREATE_INDEX_ASSIGNMENTS_SPOOL_ID: String =
    "CREATE INDEX IF NOT EXISTS index_assignments_spool_id ON assignments(spool_id)"

const val CREATE_INDEX_ASSIGNMENTS_SNAPSHOT_GENERATION: String =
    "CREATE INDEX IF NOT EXISTS index_assignments_snapshot_generation ON assignments(snapshot_generation)"
