package com.murzify.bambuddyspool.core.database

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKind
import com.murzify.bambuddyspool.core.domain.SpoolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseSchemaContractTest {

    @Test
    fun schemaVersionOneContainsRequiredTables() {
        assertEquals(1, BambuddyDatabaseSchema.VERSION)
        assertEquals(
            listOf("printers", "printer_slots", "spools", "spools_fts", "assignments", "sync_metadata"),
            BambuddyDatabaseSchema.tables
        )
    }

    @Test
    fun slotKeyConstraintsPreventAmbiguousSlots() {
        assertTrue(CREATE_PRINTER_SLOTS.contains("PRIMARY KEY (printer_id, ams_id, tray_id)"))
        assertTrue(
            CREATE_INDEX_ASSIGNMENTS_SLOT_KEY_UNIQUE.contains(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_assignments_slot_key_unique"
            )
        )
        assertTrue(CREATE_INDEX_ASSIGNMENTS_SLOT_KEY_UNIQUE.contains("assignments(printer_id, ams_id, tray_id)"))
    }

    @Test
    fun spoolSearchUsesFtsAndStableDefaultSort() {
        assertTrue(CREATE_SPOOLS_FTS.contains("USING fts4"))
        assertTrue(CREATE_SPOOLS_FTS.contains("tokenize=unicode61"))
        assertTrue(OBSERVE_SPOOL_LIST_QUERY.contains("spools_fts MATCH :ftsQuery"))
        assertTrue(OBSERVE_SPOOL_LIST_QUERY.contains("s.is_active = 1"))
        assertTrue(OBSERVE_SPOOL_LIST_QUERY.contains("s.archived_at_epoch_millis IS NULL"))
        assertTrue(OBSERVE_SPOOL_LIST_QUERY.contains("s.remaining_grams > 0"))
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("INDEXED BY index_spools_default_filter_sort"))
        assertTrue(SEARCH_DEFAULT_SPOOL_LIST_QUERY.contains("FROM spools_fts"))
        assertTrue(SEARCH_DEFAULT_SPOOL_LIST_QUERY.contains("spools_fts MATCH :ftsQuery"))
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("s.last_used_at_epoch_millis DESC"))
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("s.normalized_display_name COLLATE NOCASE ASC"))
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("s.spool_id ASC"))
    }

    @Test
    fun cleanupSurfacesAreGenerationBasedAndCacheClearable() {
        assertTrue(BambuddyDatabaseSchema.baselineSql.contains(CREATE_INDEX_SPOOLS_DEFAULT_FILTER_SORT))
        assertTrue(CREATE_INDEX_PRINTERS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
        assertTrue(CREATE_INDEX_PRINTER_SLOTS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
        assertTrue(CREATE_INDEX_SPOOLS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
        assertTrue(CREATE_INDEX_ASSIGNMENTS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
    }

    @Test
    fun projectionsMapOnlyPersistencePrimitivesToDomain() {
        val projection = AssignmentProjection(
            assignmentId = 7,
            spoolId = 3,
            printerId = 1,
            amsId = 255,
            trayId = 0,
            configured = true,
            pendingConfiguration = false
        )

        val assignment = projection.toDomain()

        assertEquals(assertNotNull(SpoolId.from(3)), assignment.spoolId)
        assertEquals(assertNotNull(PrinterId.from(1)), assignment.slot.printerId)
        assertEquals(255, assignment.slot.amsId)
        assertEquals(0, assignment.slot.trayId)
        assertEquals(true, assignment.configured)
        assertEquals(false, assignment.pendingConfiguration)
        assertEquals(PersistedSlotKind.External, SlotKind.External.toPersistedSlotKind())
    }
}
