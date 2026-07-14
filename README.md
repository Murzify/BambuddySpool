# BambuddySpool

BambuddySpool is a Kotlin Multiplatform application with a shared Compose UI and Android product shell. The iOS target is intentionally limited to a launchable shared-UI mock shell: it supports root navigation but uses explicit fail-closed mocks for unsupported NFC, credential, clipboard, and network services. It does not represent a functional iOS Bambuddy client or perform mutations.

- Repository: [`Murzify/BambuddySpool`](https://github.com/Murzify/BambuddySpool)
- Android application ID: `com.murzify.bambuddyspool`
- License decision: [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/)

The v1 physical Android/NFC acceptance device is HONOR 50 (`NTH-NX9`) running Android 13 / API 33, build `NTH-N29 7.1.0.345(C10E1R5P1)`. The confirmed Bambu Lab A1 external slot mapping is `ams_id=255`, `tray_id=0`. Multi-external-slot support is not claimed without factual sanitized topology evidence; unknown topology must fail closed.

## Prerequisites

- Git.
- Microsoft OpenJDK 17. The Gradle daemon criteria require this vendor and major version.
- Android SDK 36 for Android builds; API 23 remains the minimum supported runtime.
- macOS with Xcode 26.2 and an iOS Simulator runtime for iOS framework, test, and shell checks.

No private server configuration, API token, signing key, or live Bambuddy instance is required for foundation builds and tests. Keep all local/private configuration outside Git.

## Project structure

- [`shared`](./shared) is the Kotlin Multiplatform library. It owns product Compose UI, shared application logic, navigation, and platform contracts.
  - `commonMain` is platform-independent and must not import Android, JVM, or Apple platform APIs.
  - `androidMain` and `iosMain` are narrow platform implementation boundaries.
  - `commonTest`, `androidHostTest`, `androidDeviceTest`, and `iosTest` are the established test boundaries.
  - `iosArm64` and `iosSimulatorArm64` produce the static `Shared` framework.
- [`androidApp`](./androidApp) is the thin Android shell: manifest, application, launcher activity, resources, and build variants.
- [`iosApp`](./iosApp) is the thin Swift/Xcode shell that embeds and renders the shared Compose controller.
- [`config`](./config) contains shared project metadata and static-analysis configuration.
- [`ci`](./ci) contains repository-policy and CI limitation helpers.
- [`slop`](./slop) contains agent-facing requirements, decisions, reviews, and task reports; it is not production source.

The Gradle topology is limited to `:androidApp` and `:shared`; `iosApp` remains an Xcode project rather than a Gradle module. Android has only `debug` and `release` build types and does not lock orientation.

## Clean-checkout verification

Clone the public repository, then run the common and Android checks with JDK 17 selected:

```shell
git clone https://github.com/Murzify/BambuddySpool.git
cd BambuddySpool
./gradlew help
./ci/verify-repository.sh
./gradlew spotlessCheck detekt :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease \
  :shared:testAndroidHostTest :shared:assembleAndroidDeviceTest
```

On macOS, also verify both shared iOS targets and the shell without signing:

```shell
./gradlew :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Use `./gradlew spotlessApply` to apply the configured Kotlin and Gradle Kotlin DSL formatting rules. A second identical Gradle invocation should report configuration-cache reuse.

## Running the shells

- Android: use the Android Studio run configuration or assemble/install `:androidApp:assembleDebug` on an API 23+ device or emulator.
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on an iOS Simulator. It renders the shared root and its navigation only; unsupported platform actions remain unavailable and cannot persist credentials or issue network mutations.

The iOS shell is a mock-only surface, not a production Bambuddy client. It switches between the four shared root destinations but does not contact a server, persist data, read/write NFC tags, or accept credentials.

## Test and CI boundaries

GitHub Actions runs on pushes, pull requests, and manual dispatches with JDK 17, immutable action revisions, read-only repository permissions, non-persistent checkout credentials, and read-only caches outside trusted pushes. Superseded runs are cancelled only for pull requests.

Current limitations are explicit rather than false-green:

- Android device tests compile into an APK but hosted CI does not execute the API 23/modern-device matrix.
- The Compose UI behavior harness is not implemented.
- Dependency inventories and repository scans are not authoritative license or CVE gates.
- Physical NFC acceptance requires the named device and is outside hosted CI.

These limitations block claims of full release coverage. Dependency locking, verification metadata, license classification, vulnerability gating, production signing, and exact-binary acceptance remain later security/release tasks.

## Security posture

- Android backup, device transfer, and cleartext traffic are denied by default.
- No private API contract, token, signing material, analytics, crash reporter, or persistent diagnostics belongs in the repository or CI cache.
- Production credential, redirect, HTTP-consent, TLS-override, and redaction controls are not claimed by the current mock graph.
- The foundation threat review and accepted owners are recorded in [`slop/security/bootstrap-review.md`](./slop/security/bootstrap-review.md).

## Architecture and decisions

The product and architecture contract is [`slop/TECHSPEC.md`](./slop/TECHSPEC.md). Its ADR section is the canonical record for module boundaries, offline mutation policy, retry/verification behavior, TLS compromise scope, and minimal modularization. Focused Stage 1 decisions are under [`slop/decisions`](./slop/decisions); duplicating embedded ADRs into parallel files is intentionally avoided.
