# CODE-007 Completion Report

## Outcome

The shared assignment orchestrator now treats only an exact, unique assignment read as success. It retries only the
approved transient POST failures and keeps the command immutable for every attempt. A successful POST response alone
is still fail-closed.

## Delivered

- Added exactly four maximum POST attempts: immediate, then 500 ms, 1 s, and 2 s. Only HTTP 5xx, connect timeout,
  request timeout, and temporary network failures are retried; HTTP 4xx, contract, security/TLS, invalid and other
  failures stop immediately.
- Added read-only exact verification through `GET assignments?printer_id=<target>` immediately, after 200 ms, and
  after 500 ms. It requires one target `SlotKey` assignment with the requested spool, rejects duplicate target-slot
  state, and never returns to POST after verification begins.
- Converted verified assignment state into `AssignedAndConfigured`, `AssignedConfigurationPending`, or
  `AssignedInventoryOnly`. The pending variant remains a verified success for the shared presentation layer.
- Added a narrow best-effort secondary-feedback boundary: haptic and user-triggered redacted clipboard failures are
  contained and cannot turn an already verified assignment into an error.
- Retained the CODE-005 preflight boundary for user Retry: calling `execute` again begins a fresh validation cycle;
  no command, authorization, retry counter, or mutation is persisted or replayed.

## Verification

```text
./gradlew spotlessApply
./gradlew spotlessCheck detekt :androidApp:lintDebug :shared:testAndroidHostTest \
  :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
./ci/verify-repository.sh
git diff --check
```

All commands passed. Deterministic virtual-time tests prove POST counts/delays, no GET before a retry, no retry for
forbidden errors, exact verification polling, duplicate-slot rejection, HTTP-success fail-closed behavior, verified
configuration variants, verification-read isolation, and a fresh user Retry cycle. No private configuration was read
and no Bambuddy request was made.

## Definition of Done

- [x] Retry count, payload identity, delays, and forbidden-error behavior are covered with virtual time.
- [x] Verification requires the unique exact printer/AMS/tray/spool state and never resends a POST.
- [x] HTTP-only success and duplicate slot state cannot produce an assignment success.
- [x] A new user Retry executes fresh preflight rather than replaying prior operation state.
- [x] Configured, pending-configuration, and inventory-only verified outcomes remain typed for common presentation.
- [x] Haptic and clipboard secondary-effect failures are isolated from verified assignment success.

## Follow-up Ownership

- `[CODE]-[009]` owns serializing NFC workflows and their root-level transition into the shared orchestration result.
- `[CODE]-[012]` owns the full success/error presentation and platform feedback accessibility surface.
