# INIT-009 Completion Report

## Outcome

The Stage 1 foundation has a smaller public surface and accurate operator documentation. Implementation-only Metro graphs, reducer effects, reducer logic, and navigation children are no longer exported. The platform shells, shared root, UDF primitives, scopes, and platform-service contracts now document their ownership and limits without introducing unused abstractions.

The README now describes the current mock-only product state, prerequisites, exact module topology, clean-checkout Android/iOS/test commands, CI limitations, security posture, and canonical architecture records. Resolved toolchain warnings were refreshed instead of being preserved as current failures. CI now installs the Microsoft JDK 17 distribution required by the checked-in Gradle daemon criteria.

Two build-configuration defects were found during the completion matrix and fixed:

- Compose Android resource wiring remains enabled because Android device-test resource tasks require a configured output directory even while the source resource set is empty.
- Spotless targets the two Kotlin source trees and the four Gradle Kotlin DSL scripts explicitly, so it cannot traverse concurrently generated `build` directories.

## Definition of Done

- [x] Kotlin and Gradle Kotlin DSL formatting checks pass.
- [x] Detekt, Android Lint, host architecture tests, and repository policy checks pass.
- [x] Android debug/release builds and Android device-test packaging pass.
- [x] iOS device compilation, iOS Simulator tests, and the unsigned Xcode shell build pass.
- [x] A repeated representative Gradle invocation reuses the configuration cache.
- [x] Source TODO/FIXME/HACK/XXX markers and suppressions were inspected and triaged.
- [x] README clean-checkout instructions match the verified Stage 1 topology and toolchain.
- [x] No new ADR was created because this task changed no architectural decision; `slop/TECHSPEC.md` remains the canonical embedded ADR record.

## Verification

```text
./gradlew spotlessCheck detekt :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease \
  :shared:testAndroidHostTest :shared:assembleAndroidDeviceTest \
  :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test

xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp -sdk iphonesimulator -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build

./gradlew help --configuration-cache
./gradlew help --configuration-cache

./ci/verify-repository.sh
git diff --check
```

The final matrix completed successfully, the Xcode command reported `BUILD SUCCEEDED`, and the second `help` invocation reported `Configuration cache entry reused`. Both GitHub Actions YAML files were parsed locally after the shared setup action changed.

## Warning and Suppression Triage

- Android Lint warnings remain visible while `warningsAsErrors=false`; errors still fail the build as required by TECHSPEC section 18.6. The build script records this policy next to the setting.
- Android device-test packaging cannot strip `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`, so AGP packages those third-party libraries unchanged. Release artifact and dependency review remain owned by `[SEC]-[006]` and `[SEC]-[007]`.
- Xcode's generic unsigned Simulator build may select the first matching destination, rerun the Kotlin framework script on every build, and skip AppIntents metadata when no AppIntents dependency exists. These messages are expected for the current shell.
- Gradle reports type-safe project accessors as incubating; this is an upstream status notice, not a failed quality gate.
- The only source suppressions cover Compose/Swift interop entry-point naming. Each suppression now includes its local rationale.
- No source TODO, FIXME, HACK, or XXX marker remains.

## Residual Risk

The shared graph still uses deterministic no-op services and must not be represented as production networking, persistence, NFC, or credential support. Hosted CI still compiles rather than executes Android device tests, has no Compose UI behavior harness, and is not an authoritative license or CVE gate. Physical-device acceptance, production signing, dependency verification, license classification, vulnerability gating, and final artifact inspection remain assigned to later security and release tasks.

The MPL-2.0 product decision is linked from the README; this task did not introduce a separate repository license-text artifact.
