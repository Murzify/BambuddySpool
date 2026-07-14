package com.murzify.bambuddyspool.core.database

import com.murzify.bambuddyspool.core.domain.SlotKind
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("s.last_used_at_epoch_millis DESC"))
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("s.normalized_display_name COLLATE NOCASE ASC"))
        assertTrue(OBSERVE_DEFAULT_SPOOL_LIST_QUERY.contains("s.spool_id ASC"))
    }

    @Test
    fun schemaKeepsGenerationCleanupWithoutUnusedPersistenceArtifacts() {
        assertTrue(BambuddyDatabaseSchema.baselineSql.contains(CREATE_INDEX_SPOOLS_DEFAULT_FILTER_SORT))
        assertTrue(CREATE_INDEX_PRINTERS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
        assertTrue(CREATE_INDEX_PRINTER_SLOTS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
        assertTrue(CREATE_INDEX_SPOOLS_SNAPSHOT_GENERATION.contains("snapshot_generation"))
        assertTrue(CREATE_INDEX_ASSIGNMENTS_SNAPSHOT_GENERATION.contains("snapshot_generation"))

        val schema = BambuddyDatabaseSchema.baselineSql.joinToString(separator = "\n")
        assertTrue("normalized_manufacturer" !in schema)
        assertTrue("normalized_material" !in schema)
        assertTrue("normalized_color_name" !in schema)
        assertTrue("updated_at_epoch_millis" !in schema)
        assertTrue("created_at_epoch_millis" !in schema)
        assertTrue("access_code" !in schema)
        assertTrue("raw_response" !in schema)
        assertTrue("diagnostic" !in schema)
    }

    @Test
    fun persistedSlotKindsRemainExplicit() {
        assertEquals(PersistedSlotKind.External, SlotKind.External.toPersistedSlotKind())
    }
}
