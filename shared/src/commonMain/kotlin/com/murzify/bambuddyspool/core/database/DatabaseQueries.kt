package com.murzify.bambuddyspool.core.database

internal const val OBSERVE_PRINTER_LIST_QUERY: String = """
SELECT
    p.printer_id AS printerId,
    p.name AS name,
    COUNT(CASE WHEN ps.kind = 'external' THEN 1 END) AS externalSlotCount,
    COUNT(a.assignment_id) AS assignedSlotCount
FROM printers AS p
LEFT JOIN printer_slots AS ps ON ps.printer_id = p.printer_id
LEFT JOIN assignments AS a
    ON a.printer_id = ps.printer_id
    AND a.ams_id = ps.ams_id
    AND a.tray_id = ps.tray_id
WHERE p.is_active = 1
GROUP BY p.printer_id
ORDER BY p.name COLLATE NOCASE ASC, p.printer_id ASC
"""

internal const val OBSERVE_PRINTER_SLOTS_QUERY: String = """
SELECT
    ps.printer_id AS printerId,
    p.name AS printerName,
    ps.ams_id AS amsId,
    ps.tray_id AS trayId,
    ps.kind AS kind,
    ps.label AS label,
    a.spool_id AS assignedSpoolId,
    s.display_name AS assignedSpoolName,
    s.material AS assignedMaterial,
    s.color_name AS assignedColorName
FROM printer_slots AS ps
JOIN printers AS p ON p.printer_id = ps.printer_id
LEFT JOIN assignments AS a
    ON a.printer_id = ps.printer_id
    AND a.ams_id = ps.ams_id
    AND a.tray_id = ps.tray_id
LEFT JOIN spools AS s ON s.spool_id = a.spool_id
WHERE ps.printer_id = :printerId
ORDER BY
    CASE ps.kind WHEN 'external' THEN 0 ELSE 1 END ASC,
    ps.display_order ASC,
    ps.ams_id ASC,
    ps.tray_id ASC
"""

internal const val OBSERVE_SPOOL_LIST_QUERY: String = """
SELECT
    s.spool_id AS spoolId,
    s.display_name AS displayName,
    s.manufacturer AS manufacturer,
    s.material AS material,
    s.color_name AS colorName,
    s.remaining_grams AS remainingGrams,
    s.archived_at_epoch_millis AS archivedAtEpochMillis,
    s.last_used_at_epoch_millis AS lastUsedAtEpochMillis,
    a.printer_id AS assignedPrinterId,
    p.name AS assignedPrinterName,
    a.ams_id AS assignedAmsId,
    a.tray_id AS assignedTrayId
FROM spools AS s
LEFT JOIN assignments AS a ON a.spool_id = s.spool_id
LEFT JOIN printers AS p ON p.printer_id = a.printer_id
WHERE (:includeInactive = 1 OR s.is_active = 1)
    AND (:includeArchived = 1 OR s.archived_at_epoch_millis IS NULL)
    AND (:includeEmpty = 1 OR s.remaining_grams > 0)
    AND (:ftsQuery IS NULL OR s.spool_id IN (
        SELECT rowid FROM spools_fts WHERE spools_fts MATCH :ftsQuery
    ))
ORDER BY
    CASE WHEN s.last_used_at_epoch_millis IS NULL THEN 1 ELSE 0 END ASC,
    s.last_used_at_epoch_millis DESC,
    s.normalized_display_name COLLATE NOCASE ASC,
    s.spool_id ASC
LIMIT :limit OFFSET :offset
"""

internal const val OBSERVE_DEFAULT_SPOOL_LIST_QUERY: String = """
SELECT
    s.spool_id AS spoolId,
    s.display_name AS displayName,
    s.manufacturer AS manufacturer,
    s.material AS material,
    s.color_name AS colorName,
    s.remaining_grams AS remainingGrams,
    s.archived_at_epoch_millis AS archivedAtEpochMillis,
    s.last_used_at_epoch_millis AS lastUsedAtEpochMillis,
    a.printer_id AS assignedPrinterId,
    p.name AS assignedPrinterName,
    a.ams_id AS assignedAmsId,
    a.tray_id AS assignedTrayId
FROM spools AS s INDEXED BY index_spools_default_filter_sort
LEFT JOIN assignments AS a ON a.spool_id = s.spool_id
LEFT JOIN printers AS p ON p.printer_id = a.printer_id
WHERE s.is_active = 1
    AND s.archived_at_epoch_millis IS NULL
    AND s.remaining_grams > 0
ORDER BY
    CASE WHEN s.last_used_at_epoch_millis IS NULL THEN 1 ELSE 0 END ASC,
    s.last_used_at_epoch_millis DESC,
    s.normalized_display_name COLLATE NOCASE ASC,
    s.spool_id ASC
LIMIT :limit OFFSET :offset
"""

internal const val SEARCH_DEFAULT_SPOOL_LIST_QUERY: String = """
SELECT
    s.spool_id AS spoolId,
    s.display_name AS displayName,
    s.manufacturer AS manufacturer,
    s.material AS material,
    s.color_name AS colorName,
    s.remaining_grams AS remainingGrams,
    s.archived_at_epoch_millis AS archivedAtEpochMillis,
    s.last_used_at_epoch_millis AS lastUsedAtEpochMillis,
    a.printer_id AS assignedPrinterId,
    p.name AS assignedPrinterName,
    a.ams_id AS assignedAmsId,
    a.tray_id AS assignedTrayId
FROM spools_fts
JOIN spools AS s ON s.spool_id = spools_fts.rowid
LEFT JOIN assignments AS a ON a.spool_id = s.spool_id
LEFT JOIN printers AS p ON p.printer_id = a.printer_id
WHERE spools_fts MATCH :ftsQuery
    AND s.is_active = 1
    AND s.archived_at_epoch_millis IS NULL
    AND s.remaining_grams > 0
ORDER BY
    CASE WHEN s.last_used_at_epoch_millis IS NULL THEN 1 ELSE 0 END ASC,
    s.last_used_at_epoch_millis DESC,
    s.normalized_display_name COLLATE NOCASE ASC,
    s.spool_id ASC
LIMIT :limit OFFSET :offset
"""

internal const val OBSERVE_SPOOL_QUERY: String = """
SELECT
    s.spool_id AS spoolId,
    s.display_name AS displayName,
    s.manufacturer AS manufacturer,
    s.material AS material,
    s.color_name AS colorName,
    s.remaining_grams AS remainingGrams,
    s.archived_at_epoch_millis AS archivedAtEpochMillis,
    s.last_used_at_epoch_millis AS lastUsedAtEpochMillis,
    a.printer_id AS assignedPrinterId,
    p.name AS assignedPrinterName,
    a.ams_id AS assignedAmsId,
    a.tray_id AS assignedTrayId
FROM spools AS s
LEFT JOIN assignments AS a ON a.spool_id = s.spool_id
LEFT JOIN printers AS p ON p.printer_id = a.printer_id
WHERE s.spool_id = :spoolId
"""

internal const val OBSERVE_ASSIGNMENTS_FOR_PRINTER_QUERY: String = """
SELECT
    assignment_id AS assignmentId,
    spool_id AS spoolId,
    printer_id AS printerId,
    ams_id AS amsId,
    tray_id AS trayId,
    configured AS configured,
    pending_configuration AS pendingConfiguration
FROM assignments
WHERE printer_id = :printerId
ORDER BY ams_id ASC, tray_id ASC, assignment_id ASC
"""

internal const val FIND_EXACT_ASSIGNMENT_QUERY: String = """
SELECT
    assignment_id AS assignmentId,
    spool_id AS spoolId,
    printer_id AS printerId,
    ams_id AS amsId,
    tray_id AS trayId,
    configured AS configured,
    pending_configuration AS pendingConfiguration
FROM assignments
WHERE printer_id = :printerId
    AND ams_id = :amsId
    AND tray_id = :trayId
    AND spool_id = :spoolId
LIMIT 2
"""

internal const val OBSERVE_SYNC_METADATA_QUERY: String = """
SELECT
    last_successful_sync_at_epoch_millis AS lastSuccessfulSyncAtEpochMillis,
    snapshot_generation AS snapshotGeneration
FROM sync_metadata
WHERE metadata_key = 'domain_snapshot'
"""

internal const val MARK_SUCCESSFUL_SYNC_QUERY: String = """
UPDATE sync_metadata
SET
    last_successful_sync_at_epoch_millis = :syncedAt,
    snapshot_generation = snapshot_generation + 1,
    updated_at_epoch_millis = :updatedAt
WHERE metadata_key = 'domain_snapshot'
"""
