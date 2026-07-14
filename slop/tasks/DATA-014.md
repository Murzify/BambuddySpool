# DATA-014 Completion Report

## Summary

Polished the implemented data and infrastructure layer without adding product behavior or a new abstraction.
The Ktor repository now propagates `CancellationException` instead of classifying cancellation as a typed network
failure. Public contracts describe repository cancellation, HTTP-client ownership, synchronization scope and atomic
publication, and connection-replacement ordering. `RoomSnapshotStore` is internal because only the common data layer
adapts Room to the public `SnapshotStore` boundary.

## Changed Files

- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/network/BambuddyRepository.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/network/BambuddyHttpClientFactory.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/settings/ConnectionReplacementService.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/sync/RoomSnapshotStore.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/sync/SnapshotSynchronization.kt`
- `shared/src/commonTest/kotlin/com/murzify/bambuddyspool/core/network/KtorBambuddyRepositoryTest.kt`
- `slop/BACKLOG.md`
- `slop/CHANGELOG.md`
- `slop/tasks/DATA-014.md`

## Implementation Details

- `KtorBambuddyRepository.request` rethrows `CancellationException`; only non-cancellation failures become
  `BambuddyNetworkError`. A MockEngine test proves cancellation is not returned as a typed failure.
- `AtomicSnapshotSynchronizer` documents that its injected application scope owns the shared in-flight refresh and
  that cancellation remains cooperative. It already does not catch `CancellationException`.
- `SnapshotStore` and `RoomSnapshotStore` document the complete, generation-guarded Room publication boundary.
  `SnapshotTransactionDao.publishSnapshot` remains the single Room transaction that either completes the full write
  set or leaves the cached snapshot unchanged.
- `createBambuddyHttpClient` documents client closure ownership, disabled redirects, and platform-owned dispatching.
  No hard-coded dispatcher was introduced; network engines, Room, and NFC platform implementations retain I/O
  dispatch responsibility, while `AppDispatchers` remains platform-owned.
- `ConnectionReplacementService` documents validation-before-write ordering and its cancellation behavior.
- The existing compatibility workarounds remain deliberate and unchanged: the API 23-safe SQL rebuild migration is
  recorded in `slop/database/migration-1-to-2.sql`; disabled automatic redirects await policy-approved handling;
  and pinned KMP/AGP/Compose/Room/Metro versions remain recorded in `slop/decisions/toolchain.md` and TECHSPEC
  accepted risk 11.

## Documentation Alignment

- Schema version 2 and the `1 -> 2` migration match `BambuddyDatabase` and
  `BambuddyDatabaseMigrations` (`slop/database/schema-v2.sql`, `slop/database/migration-1-to-2.sql`).
- The sanitized eight-operation contract remains the input boundary used by mapping tests
  (`slop/contract/README.md`).
- Synchronization implementation remains aligned with TECHSPEC 8.3-8.5: complete data is validated before one
  generation-guarded Room transaction, concurrent triggers join, foreground refresh debounces, and printer status
  fan-out is bounded to four.

## Verification

```text
./gradlew spotlessApply :shared:testAndroidHostTest
# PASS (99 tests)

./gradlew spotlessCheck detekt :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
# PASS

./ci/verify-repository.sh
# PASS

git diff --check
# PASS
```

No private configuration was read and no Bambuddy request was made.

## Definition of Done

- [x] Common and Room behavior pass through the Android host/common suite.
- [x] Network behavior, including cancellation propagation, has deterministic coverage.
- [x] Formatting and static checks pass.
- [x] Architecture and repository policy checks pass.
- [x] iOS device compilation and Simulator tests pass.
- [x] Public API is minimal: the Room adapter is internal and public contracts state ownership.
- [x] Cancellation is preserved at the Ktor request boundary and in synchronization behavior.
- [x] Schema, contract, and synchronization documentation matches the inspected code.

## Risks and Limitations

- `CancellationException` is explicitly preserved before a response is mapped. Post-POST application-scoped mutation
  lifetime remains owned by the future assignment orchestrator (`[CODE]-[005]`) under TECHSPEC 10.11; this task does
  not introduce that workflow.
- Platform-specific database/NFC dispatcher implementation and platform HTTP/TLS enforcement remain owned by the
  existing platform/security work, not this common-code polish task.
- No new compatibility workaround or ADR was introduced.

## Added Backlog Tasks

None.

## ADR Links

- ADR-003 — Server-authoritative atomic snapshot (`slop/TECHSPEC.md` section 21)
- ADR-007 — No resumable mutations (`slop/TECHSPEC.md` section 21)
- ADR-009 — Host-scoped TLS verification override (`slop/TECHSPEC.md` section 21)
- ADR-010 — Minimal Gradle modularization (`slop/TECHSPEC.md` section 21)
