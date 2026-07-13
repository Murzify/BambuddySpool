-- BambuddySpool Room schema export
-- Version: 1
-- Scope: DATA-005 baseline schema artifact.
-- Note: this explicit artifact is maintained until Room compiler schema export is enabled.

CREATE TABLE IF NOT EXISTS printers (
    printer_id INTEGER NOT NULL PRIMARY KEY,
    name TEXT,
    is_active INTEGER NOT NULL,
    snapshot_generation INTEGER NOT NULL,
    updated_at_epoch_millis INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS printer_slots (
    printer_id INTEGER NOT NULL,
    ams_id INTEGER NOT NULL,
    tray_id INTEGER NOT NULL,
    kind TEXT NOT NULL,
    label TEXT,
    display_order INTEGER NOT NULL,
    snapshot_generation INTEGER NOT NULL,
    updated_at_epoch_millis INTEGER NOT NULL,
    PRIMARY KEY (printer_id, ams_id, tray_id),
    FOREIGN KEY (printer_id) REFERENCES printers(printer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS spools (
    spool_id INTEGER NOT NULL PRIMARY KEY,
    display_name TEXT,
    normalized_display_name TEXT NOT NULL,
    manufacturer TEXT,
    normalized_manufacturer TEXT,
    material TEXT,
    normalized_material TEXT,
    color_name TEXT,
    normalized_color_name TEXT,
    remaining_grams INTEGER,
    is_active INTEGER NOT NULL,
    archived_at_epoch_millis INTEGER,
    last_used_at_epoch_millis INTEGER,
    snapshot_generation INTEGER NOT NULL,
    updated_at_epoch_millis INTEGER NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS spools_fts USING fts4 (
    display_name,
    manufacturer,
    material,
    color_name,
    content='spools',
    tokenize=unicode61,
    prefix='2,3,4'
);

CREATE TABLE IF NOT EXISTS assignments (
    assignment_id INTEGER NOT NULL PRIMARY KEY,
    spool_id INTEGER NOT NULL,
    printer_id INTEGER NOT NULL,
    ams_id INTEGER NOT NULL,
    tray_id INTEGER NOT NULL,
    configured INTEGER NOT NULL,
    pending_configuration INTEGER NOT NULL,
    created_at_epoch_millis INTEGER,
    snapshot_generation INTEGER NOT NULL,
    updated_at_epoch_millis INTEGER NOT NULL,
    FOREIGN KEY (printer_id) REFERENCES printers(printer_id) ON DELETE CASCADE,
    FOREIGN KEY (printer_id, ams_id, tray_id) REFERENCES printer_slots(printer_id, ams_id, tray_id)
        ON DELETE CASCADE,
    FOREIGN KEY (spool_id) REFERENCES spools(spool_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sync_metadata (
    metadata_key TEXT NOT NULL PRIMARY KEY,
    last_successful_sync_at_epoch_millis INTEGER,
    snapshot_generation INTEGER NOT NULL,
    schema_version INTEGER NOT NULL,
    updated_at_epoch_millis INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS index_printers_snapshot_generation ON printers(snapshot_generation);
CREATE INDEX IF NOT EXISTS index_printers_name ON printers(name);
CREATE INDEX IF NOT EXISTS index_printer_slots_printer_id ON printer_slots(printer_id);
CREATE INDEX IF NOT EXISTS index_printer_slots_kind ON printer_slots(kind);
CREATE INDEX IF NOT EXISTS index_printer_slots_snapshot_generation ON printer_slots(snapshot_generation);
CREATE INDEX IF NOT EXISTS index_spools_default_filter_sort
    ON spools(
        is_active,
        archived_at_epoch_millis,
        remaining_grams,
        last_used_at_epoch_millis,
        normalized_display_name,
        spool_id
    );
CREATE INDEX IF NOT EXISTS index_spools_snapshot_generation ON spools(snapshot_generation);
CREATE INDEX IF NOT EXISTS index_spools_manufacturer ON spools(normalized_manufacturer);
CREATE INDEX IF NOT EXISTS index_spools_material ON spools(normalized_material);
CREATE INDEX IF NOT EXISTS index_spools_color_name ON spools(normalized_color_name);
CREATE UNIQUE INDEX IF NOT EXISTS index_assignments_slot_key_unique
    ON assignments(printer_id, ams_id, tray_id);
CREATE INDEX IF NOT EXISTS index_assignments_spool_id ON assignments(spool_id);
CREATE INDEX IF NOT EXISTS index_assignments_snapshot_generation ON assignments(snapshot_generation);
