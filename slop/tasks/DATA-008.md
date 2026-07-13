# [DATA]-[008] Implement Atomic Snapshot Synchronization

Status: Completed on 2026-07-13.

Implemented scope:

- Added `AtomicSnapshotSynchronizer` for full snapshot refreshes from the existing `BambuddyRepository`.
- Fetches printers, every printer status, spools with `includeArchived=true`, and assignments before publishing anything.
- Joins concurrent full-sync triggers into one in-flight work item.
- Debounces foreground triggers by two seconds through coroutine virtual time.
- Bounds printer-status fan-out with a semaphore at four concurrent status requests.
- Validates the complete domain snapshot before publication, including duplicate IDs, exact status coverage, duplicate slot assignments, and assignment references to known printers, spools, and resolved slots.
- Added a narrow `SnapshotStore` transaction boundary plus `RoomSnapshotStore` for the Room integration point.
- `RoomSnapshotStore` publishes in a single Room transaction, replaces volatile slot/assignment state, deletes rows absent from the new complete generation, and advances sync metadata only after successful rebuild.
- Generation-guarded publication prevents an older refresh from overwriting state after a newer mutation generation.

Topology note:

- DATA-008 uses only a minimal local mapping for the confirmed A1 external slot (`ams_id=255`, `tray_id=0`) so synchronization can build persistence rows for current views.
- `[DATA]-[009]` still owns the full fail-closed `SlotTopologyResolver`, multi-slot interpretation, AMS read-only modeling, and label precedence.

Definition of Done:

- [x] Rollback and stale-cache preservation are covered by deterministic common tests.
- [x] Partial network or invalid contract-like responses do not publish a snapshot.
- [x] Concurrent trigger joining is covered without sleeps.
- [x] Foreground debounce is covered with virtual time.
- [x] Status concurrency is deterministically bounded to four.
- [x] Later server views replace earlier snapshots, including deletion of missing rows at the snapshot boundary.
- [x] Older sync generations cannot overwrite post-mutation state.
- [x] Cache rebuild remains scoped to Room domain data; settings and token contracts from `[DATA]-[006]` are untouched.
- [x] UI-facing read surfaces remain Room projections/DAO flows from `[DATA]-[005]`; sync publishes persistence rows and does not expose network DTOs.

Verification:

```text
./gradlew :shared:testAndroidHostTest
./gradlew spotlessCheck detekt :shared:testAndroidHostTest
```

Final full verification is recorded in the implementing agent response.
