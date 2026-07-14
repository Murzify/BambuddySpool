# CODE-001 Completion Report

## Summary

Implemented the shared one-page connection form used by first-run Setup and Settings. It accepts a Bambuddy base
URL and API token, can validate reachability/authentication without saving, and saves through the existing atomic
connection replacement service only after successful validation and required confirmations.

## Changed Files

- `shared/build.gradle.kts`
- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/settings/ConnectionReplacementService.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/connection/ConnectionFormComponent.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/connection/ConnectionFormReducer.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/connection/ConnectionFormScreen.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/feature/setup/SetupFeatureBoundary.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/feature/settings/SettingsFeatureBoundary.kt`
- `shared/src/commonTest/kotlin/com/murzify/bambuddyspool/core/settings/ConnectionReplacementServiceTest.kt`
- `shared/src/commonTest/kotlin/com/murzify/bambuddyspool/app/connection/ConnectionFormReducerTest.kt`
- `slop/BACKLOG.md`
- `slop/CHANGELOG.md`
- `slop/tasks/CODE-001.md`

## Implementation Details

- `ConnectionFormComponent` is the single shared controller used by Setup and Settings entry points. It delegates
  Test and Save actions to `ConnectionReplacementService`.
- The API token never enters reducer state, navigation state, persistence, resources, or error text. The Composable
  retains it with `remember` only, then passes it directly to the `SecretValue` request boundary for an immediate
  operation.
- Connection testing parses the permitted canonical HTTP/HTTPS URL forms and checks reachability/authentication
  only; it never changes active settings, token, cache, defaults, consents, or sync state.
- Replacement now follows TECHSPEC 8.7 ordering: parse, validate reachability/authentication, show a required
  instance-change warning when applicable, persist the replacement, reset cache/default/security scope as defined
  by the existing service, and request initial synchronization. A failed validation leaves active state unchanged.
- Saving an unchanged canonical URL uses the token-replacement path instead: it validates the replacement token and
  changes no cache, default printer, acknowledgement, or synchronization state.
- The screen is scrollable, uses standard labelled text-field/button/dialog semantics, suppresses token rendering,
  and sources every displayed string from Compose resources.

## Verification

```text
./gradlew spotlessApply
./gradlew spotlessCheck detekt :androidApp:lintDebug :shared:testAndroidHostTest \
  :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
./ci/verify-repository.sh
git diff --check
```

Focused Android-host tests cover the connection service and pure reducer paths, including validation-only testing,
validation failure preservation, validation-before-warning ordering, sequential instance/HTTP confirmations, cache
clearing, and initial-sync request.

No private `.env.local` file was read and no Bambuddy request was made.

## Definition of Done

- [x] Setup and Settings use the same `ConnectionFormComponent` and `ConnectionReplacementService` boundary.
- [x] The token is absent from reducer, saved, and navigation state.
- [x] Validation-only Test and failed Save paths leave active state unchanged.
- [x] Confirmed instance replacement uses the established default/cache/HTTP/TLS reset rules and requests initial sync.
- [x] The shared accessible UI is reducer-tested and uses resource-backed user-facing strings.

## Risks and Limitations

- Root navigation and first-run routing are intentionally owned by `[CODE]-[002]`; this task supplies the shared
  Setup and Settings form entry points without changing that future navigation scope.
- Platform-backed secure storage, settings persistence, and network/TLS policy adapters remain the responsibilities
  established in the preceding data/security backlog. This task does not introduce any new persistence or network
  implementation.

## Added Backlog Tasks

None.

## ADR Links

No ADR was required. This task implements the existing ADR-003 server-authoritative snapshot and the connection
replacement requirements in TECHSPEC sections 8.7, 8.8, 13.1-13.5, and 14.8-14.9 without changing an architecture
invariant, security decision, persistence model, or public API contract.
