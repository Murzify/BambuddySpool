# Task Report: INIT-002 Run the Toolchain Compatibility Spike

**Status:** Complete
**Completed:** 2026-07-12
**Branch:** `task/init-002`

## Outcome

The required ecosystem is pinned and proven across Android, `iosArm64`, and `iosSimulatorArm64` using JDK 17. The shared Compose entry point remains wired into both platform shells. API 23 is now the actual Android minimum rather than documentation-only intent.

## Definition of Done

- [x] `slop/decisions/toolchain.md` records exact versions, rationale, evidence, and workarounds.
- [x] Gradle wrapper 9.1.0 has an exact URL and SHA-256; the version catalog contains no dynamic versions.
- [x] Gradle launcher/daemon criteria and JVM bytecode target JDK 17.
- [x] AGP application and Android KMP library plugins remain in separate modules.
- [x] Android debug APK builds and declares minSdk 23.
- [x] Android host/common smoke tests execute.
- [x] `iosArm64` compiles.
- [x] `iosSimulatorArm64` tests complete successfully on the final JDK 17 / Kotlin 2.3.20 / Metro 0.11.4 matrix.
- [x] The iOS Simulator shell builds and links the final shared Compose framework.
- [x] Room, Decompose, Ktor, Metro, Serialization, and DataStore resolve and compile through a focused common smoke test.
- [x] Dependency-resolution controls and known warnings are documented.
- [x] No private instance access, secret, or private OpenAPI artifact was used.

## Verification commands

```text
JAVA_HOME=<Microsoft JDK 17> ./gradlew --version
JAVA_HOME=<Microsoft JDK 17> ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
apkanalyzer manifest min-sdk androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## ADR and backlog

No ADR and no new backlog task were added. Existing `[INIT]-[003]`, `[INIT]-[005]`, and `[SEC]-[005]` own deployment-target alignment, quality-gate follow-up, and dependency verification/locking respectively.

## Risks

- AGP 9.0.1 D8 may emit newer-Kotlin metadata parsing warnings; re-evaluate before release/minification.
- JDK auto-download is not configured; environments must provision Microsoft JDK 17.
- Metro is pinned to 0.11.4 because every 0.12+ plugin release requires a JDK 21 Gradle runtime.
- The framework bundle ID warning remains non-blocking configuration work.
- The successful Xcode shell build reports a shared libicu object built for Simulator 18.5 while the app targets 18.2; align deployment targets in `[INIT]-[003]`.

## Push status

Not pushed.
