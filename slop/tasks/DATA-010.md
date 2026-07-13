# [DATA]-[010] Implement Cache and Availability Projections

Status: Complete

## Scope

Implemented the common read-side projection boundary for cached printer, printer-slot, spool-list, spool-search, and spool-detail views. Feature code can consume `core.projections` contracts without importing Room DAOs, entities, transport DTOs, or raw responses.

## Implementation Notes

- Added `CacheProjectionState` with `InitialLoading`, `Content`, `ContentRefreshing`, and `FatalErrorWithoutCache`.
- Added `CacheAvailability` with stale state, nonblocking refresh error, and typed mutation availability.
- Kept cached content visible when refresh fails, including offline/auth-style failures, while disabling mutations with explicit reasons.
- Added limit-offset `PageRequest` and `PagedResult` contracts for large spool lists.
- Added database-backed spool search through projection data-source calls; the contract debounces search input and uses `flatMapLatest` so superseded database flows are cancelled.
- Added a Room adapter over existing DAO projection flows plus narrow count flows used only to classify cache presence.
- Added `DefaultPrinterLifecycle` for default-printer settings transitions: exactly one printer auto-selects, multiple printers defer, and missing/deleted defaults are cleared or replaced only when the remaining printer set is unambiguous.

## Verification

- `./gradlew :shared:testAndroidHostTest`
- `./gradlew spotlessCheck detekt`
- `./gradlew :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test`

No live Bambuddy instance was contacted.
