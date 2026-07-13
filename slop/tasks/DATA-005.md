# DATA-005 Completion Report

## Outcome

Room KMP persistence contracts now exist under
`shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/database`.

The implementation defines:

- Room entities for `printers`, `printer_slots`, `spools`, `assignments`, and `sync_metadata`;
- an FTS4 search table, `spools_fts`, tied to `spools` for indexed spool search over display name, manufacturer, material, and color name;
- a version 1 `BambuddyDatabase` declaration;
- DAO surfaces for upsert, generation-based cleanup, URL-change cache clearing, printer/slot views, assignment lookup, default indexed spool browsing/search, and flexible paged spool list observation;
- persistence projection types that map primitive database rows to domain models without depending on network DTOs;
- explicit baseline schema SQL under `slop/database/schema-v1.sql`.

The task intentionally does not implement the full synchronizer, Ktor repositories, DataStore settings, UI, assignment orchestration, or topology resolution.

## Schema Boundary

`printer_slots` uses the composite primary key `(printer_id, ams_id, tray_id)`.
`assignments` has a unique index over the same SlotKey columns, so the local cache cannot represent two spools in one slot. A duplicated server slot assignment will fail the later snapshot transaction instead of publishing ambiguous state.

Snapshot cleanup is generation-based. Later synchronization can upsert the complete snapshot with a new generation and delete rows from older generations inside one transaction.

## Schema Export Limitation

Room runtime was already present from the toolchain spike, but the repository does not yet configure Room compiler/KSP schema export. To avoid broad toolchain changes in this task, the baseline migration/schema is documented as the explicit tracked artifact `slop/database/schema-v1.sql` plus the matching `BambuddyDatabaseSchema` constants.

## Definition of Done

- [x] Room entities exist for the required persistence tables.
- [x] SlotKey uniqueness is enforced by `printer_slots` composite primary key and assignment unique index.
- [x] Indexed spool filter/sort columns, explicit default-list index usage, and FTS search table are part of the version 1 schema.
- [x] DAO methods return persistence/domain projections and never transport DTOs.
- [x] DAO cleanup methods support generation deletes, assignment replacement, slot-topology replacement, and URL-change cache clearing.
- [x] Baseline schema version 1 is documented/exported in a tracked artifact.
- [x] Focused common tests cover schema constants, SlotKey constraints, search query shape, cleanup indexes, and projection mapping.
- [x] iOS targets compile.

## Verification

Verification was run after implementation:

```text
./gradlew spotlessCheck detekt :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
git diff --check
```

Results are recorded in the final task response.

The Gradle matrix completed successfully. `git diff --check` is run as the final whitespace check after documentation updates.

An additional local SQLite query-plan check used the exported schema with 25,000 synthetic spool rows. The default browsing query used the `index_spools_default_filter_sort` covering index for filtering, and the search query scanned the `spools_fts` virtual table before primary-key spool lookup. SQLite reported a temporary B-tree for the exact recently-used/name/ID ordering, which is expected for the null-last sort expression and does not require loading a full snapshot into presentation state.
