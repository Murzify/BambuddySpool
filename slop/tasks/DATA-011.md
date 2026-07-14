# [DATA]-[011] Remove Unused Models and Persistence Artifacts

Status: Complete

## Scope

Removed data-layer artifacts that were not read by product behavior or required by the active snapshot/projection contracts.

## Implementation Notes

- Removed unused Room write/maintenance DAO surfaces, assignment-specific read projections, and a duplicate default-search query. Snapshot publication remains the sole write boundary.
- Removed unused printer activity state, per-row persistence timestamps, assignment creation timestamps, normalized spool copies, and indexes that did not support active queries. The v1 schema export remains tracked and matches the reduced baseline.
- Retained spool archived, active, and last-used fields because PRD filtering and sorting require them, even though their fuller transport-to-domain propagation remains later work.
- Reduced DTO and synthetic fixture fields to values used for authentication response shape, printer identity, and assignment verification/configuration. No raw responses, diagnostics, access codes, or unrelated Bambuddy data are persisted.
- Added schema-contract assertions that the baseline does not contain removed normalized/timestamp fields or prohibited sensitive persistence categories.

## Definition of Done

- [x] Proven-unused DAO methods, projections, queries, DTO fields, fixture fields, persistence fields, and indexes are removed.
- [x] The tracked version 1 schema export is retained and reflects the active persistence model.
- [x] Required spool filtering/search/sorting, assignment slot uniqueness, snapshot generation cleanup, and sync metadata remain covered.
- [x] The schema does not store raw responses, diagnostics, or access codes.
- [x] Contract/database tests and iOS compilation pass.

## Verification

```text
./gradlew :shared:testAndroidHostTest
./gradlew spotlessCheck detekt :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
./ci/verify-repository.sh
git diff --check
```

No live Bambuddy instance was contacted.
