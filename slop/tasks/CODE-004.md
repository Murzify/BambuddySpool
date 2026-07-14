# CODE-004 Completion Report

## Summary

Implemented the shared Printers browser and manual assignment entry. The feature reads only through the existing
cache projection boundary and creates an in-memory `AssignmentIntent` for the later common orchestrator; it does not
perform a network mutation or create a persistent/deferred command.

## Delivered

- Added a restorable shared printer browser and details controller backed by `CacheProjectionRepository` printer and
  slot flows. Only safe selected-printer navigation restores.
- Rendered external slots as selectable only when the cache grants fresh mutation availability and an active manual
  spool selection exists. AMS slots remain visible but read-only, with no assignment action.
- Added the common, non-serializable `AssignmentIntent` boundary. Manual entry creates it with source `Manual`, the
  selected external `SlotKey`, and the generation supplied by the fresh projection. The root keeps this state
  transient, so it cannot be restored or replayed.
- Kept unsupported, stale, refresh-in-progress, failed, and offline cache states fail-closed through the existing
  `MutationAvailability` guard. No topology inference or Android framework type was introduced.
- Fixed the compact-width Spools filter layout regression with wrapping controls and 48 dp minimum targets.

## Verification

```text
./gradlew spotlessCheck :shared:testAndroidHostTest :shared:compileAndroidDeviceTest detekt
git diff --check
```

The host reducer/restoration tests and compiled Android device Compose tests cover printer selection, external manual
entry, AMS read-only presentation, stale disabling, and the compact-width filter regression. No private configuration
was read and no Bambuddy request was made.

## Definition of Done

- [x] Printer and slot screens consume Room projection contracts only.
- [x] External, AMS, empty/loading, refreshing, fatal, and unavailable mutation states have explicit rendering.
- [x] AMS has no mutation path; stale and offline mutation availability disables external assignment.
- [x] Manual assignment creates the shared transient `AssignmentIntent` boundary reserved for NFC as well.
- [x] The intent, authorization, and pending manual spool selection are absent from restored state.
- [x] Shared reducer/restoration and Android device Compose semantic coverage compile and host tests pass.
- [x] Compact filter controls wrap at narrow widths with 48 dp targets and regression coverage.
