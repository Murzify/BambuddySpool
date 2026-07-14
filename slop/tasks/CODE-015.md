# CODE-015 Completion Report

## Outcome

Removed the temporary Android production mock-platform bootstrap and obsolete development-only UI artifacts without
removing the required common test fakes or the iOS mock shell.

## Delivered

- Android now supplies its real `AndroidNfcService` directly to the shared root. It no longer creates a production
  graph from `mockPlatformServices()` or overrides one mocked capability.
- The root bootstrap now depends only on the NFC capability it actually consumes. The unused application graph and
  its unimplemented platform-service bindings were removed.
- Moved the deterministic aggregate platform fake from `commonMain` to `commonTest`. It remains available to
  architecture tests but cannot be linked into either production shell.
- Retained the dedicated iOS mock platform bindings and their Simulator tests; the iOS shell continues to expose
  unavailable platform capabilities fail-closed rather than synthesize credentials, inventory, assignments, or
  mutations.
- Removed the Android Compose Preview, its tooling dependencies, and empty feature-boundary marker files. No
  production assignment, NFC, data, or navigation implementation was duplicated or removed.

## Verification

```text
rg -n 'mockPlatformServices|platformServices =|createApplicationGraph|ApplicationGraph|uiTooling|FeatureBoundary' \
  --glob '*.kt' --glob '*.kts' --glob '*.toml' .
./gradlew spotlessApply :shared:testAndroidHostTest \
  --tests 'com.murzify.bambuddyspool.ArchitectureSkeletonTest' \
  :androidApp:assembleDebug :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test --console=plain
git diff --check
```

The audit found `mockPlatformServices` only in `commonTest`; the retained `iosMockPlatformServices` is iOS-only.
The Gradle verification completed successfully with Android assembly, Android host coverage, iOS device compilation,
and iOS Simulator tests.

## Definition of Done

- [x] Android production no longer contains a temporary mock platform graph or Preview/debug UI tooling.
- [x] Common test fakes remain test-only, and the explicit iOS mock shell remains intact.
- [x] Empty feature markers and unused Android tooling dependencies are removed.
- [x] Assignment and NFC paths remain singular; no production inventory, assignment, or mutation fake data exists.
- [x] Android and iOS builds plus targeted feature/architecture tests pass.
