# CODE-009 Completion Report

## Outcome

NFC scan admission is now coordinated by one common, ephemeral state machine. It permits only one active physical
workflow and, once a POST begins, retains no more than the newest later scan for subsequent processing.

## Delivered

- Added platform-neutral monotonic timestamps to accepted NFC observations. Android obtains them through
  `SystemClock.elapsedRealtime()` at the Android intent boundary; wall-clock time is not used.
- Added a pure common coordinator with opaque session IDs and explicit `BeforePost`, `AfterPost`, `Success`, and
  `Error` phases. It suppresses an accepted matching fingerprint inside the 1-second window.
- A scan before POST replaces the active workflow. A scan after POST cannot start a second mutation: it replaces the
  sole pending slot and begins only after the active workflow completes verification. Stale callbacks are ignored.
- Wired root scan input through the coordinator. Coordination state remains process-local and is absent from
  StateKeeper, navigation, and restored mutation state.

## Verification

```text
./gradlew spotlessApply :shared:testAndroidHostTest --tests '*NfcScanCoordinatorTest' \
  --tests '*RootComponentTest' :androidApp:compileDebugAndroidTestKotlin
```

The deterministic common race tests cover duplicate suppression, pre-POST supersession, stale callback rejection,
post-POST active-plus-latest capacity, no parallel start before completion, and immediate Success replacement.

## Definition of Done

- [x] One active NFC session and at most one latest pending scan are represented in common transient state.
- [x] Accepted duplicate fingerprints within one monotonic second are suppressed.
- [x] New scans replace pre-POST workflows; post-POST scans wait for verification and keep only the newest input.
- [x] A new scan immediately replaces a Success result with Processing.
- [x] Deterministic race tests prove supersession and prevent parallel assignment starts.
- [x] Android framework objects and restorable mutation/session state are absent from common coordination.
