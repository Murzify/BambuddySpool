# Task Report: INIT-003 Establish the shared/androidApp/iosApp Topology

**Status:** Complete
**Completed:** 2026-07-12
**Branch:** `task/init-003`

## Outcome

ADR-001 is implemented as the project foundation. `shared` is the KMP product/UI boundary, `androidApp` is a thin Android shell, and `iosApp` is a thin Swift/Xcode shell. Required common, Android host/device, and iOS source/test sets are present and verified.

## Topology

- `shared/commonMain`: shared Compose UI and platform-independent logic.
- `shared/androidMain`: narrow Android platform implementation.
- `shared/iosMain`: narrow iOS platform implementation and Compose view-controller factory.
- `shared/commonTest`, `androidHostTest`, `androidDeviceTest`, and `iosTest`: required test boundaries.
- `shared` targets `iosArm64` and `iosSimulatorArm64` and exports a static framework with an explicit bundle ID.
- `androidApp`: manifest, `BambuddyApplication`, launcher `MainActivity`, Android resources, build types, and packaging/R8 boundary only. It renders `App()` from `shared`.
- `iosApp`: SwiftUI wrapper and Xcode configuration only. It renders `MainViewController()` from `shared`.

## Definition of Done

- [x] Module and source-set topology matches TECHSPEC and ADR-001.
- [x] Android application and Android KMP library plugins remain in separate modules.
- [x] Both Android and iOS shells render the same shared Compose entry point.
- [x] Common Kotlin source contains no Android framework imports.
- [x] Android debug and release APKs build.
- [x] Both APKs declare minSdk 23; orientation is not locked in the manifest.
- [x] Android host tests pass and the Android device-test APK builds.
- [x] Android device-test smoke passes on the connected HONOR 50, Android 13 / API 33.
- [x] `iosArm64` compiles and `iosSimulatorArm64` tests pass.
- [x] Xcode Simulator shell builds and the former iOS 18.5/18.2 link warning is resolved.
- [x] README documents ownership, source sets, build types, API baseline, and verification commands.
- [x] No INIT-004 UDF/navigation/DI architecture was introduced.

## Verification

```text
JAVA_HOME=<Microsoft JDK 17> ./gradlew \
  :androidApp:assembleDebug \
  :androidApp:assembleRelease \
  :shared:testAndroidHostTest \
  :shared:assembleAndroidDeviceTest \
  :shared:compileKotlinIosArm64 \
  :shared:iosSimulatorArm64Test

JAVA_HOME=<Microsoft JDK 17> ./gradlew :shared:connectedAndroidDeviceTest

xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build
```

Results:

- Combined Gradle matrix: `BUILD SUCCESSFUL`.
- Physical Android smoke: one test passed on `NTH-NX9`, Android 13.
- Xcode: `BUILD SUCCEEDED`; no libicu deployment-target mismatch warning remains.
- Artifact inspection: debug and release minSdk are 23.
- Available emulator inventory contains only `Medium_Phone_API_36.0`; no API 23 emulator/device execution is claimed.

## ADR and backlog

No new ADR is required; this task implements accepted ADR-001 and preserves ADR-010. No backlog task was added.

## Risks and limitations

- API 23 compatibility is proven at build/artifact level, not by execution on an API 23 runtime because none is installed.
- The current shared screen is still generated bootstrap UI; removal/replacement is owned by `[INIT]-[006]` and product tasks.
- iOS is a compile/render shell with mock/future platform behavior, as required for v1.

## Push status

Not pushed.
