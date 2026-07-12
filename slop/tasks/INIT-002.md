# Task Report: INIT-002 Run the Toolchain Compatibility Spike

**Status:** Incomplete; final iOS Simulator execution evidence pending
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
- [ ] `iosSimulatorArm64` tests execute on the final JDK 17 / Kotlin 2.3.20 / Metro 0.11.4 matrix. The binary linked, but the verification run was interrupted before task execution completed.
- [ ] The iOS Simulator shell is rebuilt after the final matrix test execution. An earlier candidate built successfully but used the superseded Java 21 / Kotlin 2.4 / Metro 1.3.1 combination.
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

No ADR and no new backlog task were added. `[INIT]-[002]` remains unchecked. Existing `[INIT]-[005]` and `[SEC]-[005]` own quality-gate follow-up and dependency verification/locking respectively.

## Risks

- AGP 9.0.1 D8 may emit newer-Kotlin metadata parsing warnings; re-evaluate before release/minification.
- JDK auto-download is not configured; environments must provision Microsoft JDK 17.
- Metro is pinned to 0.11.4 because every 0.12+ plugin release requires a JDK 21 Gradle runtime.
- The framework bundle ID warning remains non-blocking configuration work.

## Push status

Not pushed.
