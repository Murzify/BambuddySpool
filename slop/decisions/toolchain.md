# Toolchain Compatibility Decision

**Status:** Candidate; final iOS Simulator execution pending
**Decision date:** 2026-07-12
**Scope:** `[INIT]-[002]`

This file records the compatibility spike required by `slop/TECHSPEC.md`. The backlog names `docs/decisions/toolchain.md`; repository-level `AGENTS.md` requires agent-facing decisions under `slop/`, so this location takes precedence.

## Pinned matrix

| Component | Version | Rationale and proof |
|---|---:|---|
| JDK / Gradle daemon | Microsoft OpenJDK 17 | TECHSPEC baseline. `gradle/gradle-daemon-jvm.properties` requires Java 17 from Microsoft; both launcher and daemon criteria were verified as Java 17. |
| Gradle wrapper | 9.1.0 | Meets the 9.1+ baseline. Distribution URL and SHA-256 are pinned in the wrapper properties. |
| Android Gradle Plugin | 9.0.1 | Required AGP 9.0.x line. The application plugin remains in `androidApp`; the Android KMP library plugin remains in `shared`. |
| Kotlin / KGP / Compose compiler / Serialization plugin | 2.3.20 | One Kotlin version controls all Kotlin compiler plugins. Compose 1.11 requires at least Kotlin 2.3.10 for native targets, and Metro 0.11.4 explicitly supports Kotlin 2.3.20. Kotlin 2.4.0 cannot be used with a JDK-17-compatible Metro plugin. |
| Compose Multiplatform | 1.11.1 | Existing stable UI baseline; common Compose compiles into both Android and iOS shells. |
| Material 3 for Compose Multiplatform | 1.11.0-alpha07 | Existing explicitly pinned artifact compatible with the Compose 1.11 project. It is retained pending product UI work rather than silently substituted during this spike. |
| Room KMP | 2.8.4 | Current stable Room release observed in Google Maven metadata; common annotations/runtime compile for Android and both iOS targets. Code generation and schema setup belong to `[DATA]-[005]`. |
| Decompose | 3.5.0 | Stable release selected instead of the available 3.6 alpha. Common `ComponentContext` compiles on Android and iOS. |
| Ktor | 3.5.1 | Stable release; common client plus OkHttp Android and Darwin iOS engines resolve and compile. |
| Metro | 0.11.4 | Latest release whose published Gradle plugin targets JVM 11 and therefore runs on the required JDK 17. Metro 0.12+ targets JVM 21 and is incompatible with the baseline. An injected common test class proves compiler-plugin processing. Graph design belongs to `[INIT]-[004]`. |
| Kotlinx Serialization JSON | 1.11.0 | Stable runtime paired with the Kotlin serialization compiler plugin; common round-trip smoke test executes on Android/JVM and iOS Simulator. |
| DataStore Preferences | 1.2.0 | Stable KMP release selected instead of the available 1.3 alpha; common preference API compiles and executes in smoke tests. |
| Android SDK | compile/target 36, minimum 23 | API 23 is the product baseline. The assembled debug APK declares minSdk 23. |
| Xcode | 26.2 | Installed toolchain used to build the iOS Simulator shell. |

All direct versions are exact. Dynamic versions, version ranges, `latest.*`, and snapshots are prohibited.

## Compatibility evidence

The matrix commands were exercised on 2026-07-12. Android assembly, Android host tests, and both iOS compilations completed for the final Kotlin 2.3.20 / Metro 0.11.4 / JDK 17 candidate. The final run was interrupted after the iOS Simulator test binary linked but before Gradle recorded test execution; therefore this document is not yet an accepted final decision.

```text
JAVA_HOME=<Microsoft JDK 17> ./gradlew \
  :androidApp:assembleDebug \
  :shared:testAndroidHostTest \
  :shared:compileKotlinIosArm64 \
  :shared:iosSimulatorArm64Test

xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build
```

Results:

- Android debug APK assembled through the thin `androidApp` shell and shared Compose UI.
- Android host/common dependency smoke tests passed.
- `iosArm64` shared code compiled.
- `iosSimulatorArm64` shared tests linked for the final matrix; execution remains to be captured. An earlier Kotlin 2.4 / Metro 1.3.1 run executed successfully but used the incompatible Java 21 daemon criteria and is not final evidence.
- The Swift iOS shell linked the shared Compose framework and built successfully for the earlier matrix; it must be repeated once after final Simulator test execution.
- Android artifact inspection reported minSdk `23`.
- Configuration cache was stored and reused by the main verification matrix.

`ToolchainCompatibilityTest` provides a deliberately small proof of common Room, DataStore, Decompose, Ktor, Serialization, and Metro symbols. It does not implement production architecture or persistence. `[INIT]-[002]` remains unchecked until the final candidate executes the iOS Simulator tests and rebuilds the shell.

## Reproducibility controls

- The wrapper version, download URL, and checksum are committed.
- Every direct plugin and library version is centralized and exact in `gradle/libs.versions.toml`.
- Kotlin compiler plugins share the single Kotlin version.
- Gradle daemon criteria require JDK 17; local and CI environments must provision a matching Microsoft JDK rather than rely on the shell default.
- Google Maven, Maven Central, and Gradle Plugin Portal are the only configured repositories.
- Dependency verification and locking are intentionally deferred until graph stabilization as prescribed by TECHSPEC section 18.7 and `[SEC]-[005]`.

## Workarounds and observations

### Gradle daemon JDK

Setting `JAVA_HOME` alone did not override the repository's prior Java 21 daemon criteria. The criteria file is now pinned to Microsoft Java 17. The current build has no toolchain-download resolver, so JDK 17 must be preinstalled. Running `updateDaemonJvm` without such a resolver fails and must not be used as a verification command until provisioning is added.

Metro 0.12.0 through 1.3.1 publish their Gradle plugins for JVM 21, so they cannot configure a JDK 17 build. Metro 0.11.4 is the newest published version with a JVM 11 plugin target and is the deliberate compatibility pin. It supports Kotlin 2.3.20; the initially present Kotlin 2.4.0 is not supported by this Metro line, so Kotlin is pinned to 2.3.20.

### AGP D8 metadata warning

AGP 9.0.1's bundled D8 may emit verbose warnings while parsing newer Kotlin metadata during debug dexing. The matrix uses Kotlin 2.3.20, which is accepted by Metro and Compose. Any remaining D8 warning must remain visible and be re-evaluated during `[INIT]-[005]` quality gates and before release/minification; replacing AGP's bundled D8 ad hoc is not accepted in this spike.

### Kotlin/Native first run

The first native build downloads the Kotlin/Native compiler dependencies into the user's Kotlin/Native cache. Subsequent builds reuse them. CI must allow this documented toolchain bootstrap or cache it without caching credentials.

### Framework bundle identifier warning

Kotlin/Native warns that it derives the shared framework bundle identifier from the framework name. This does not block compilation or shell linking. A final explicit framework identity belongs with topology/release configuration, not this compatibility spike.

## ADR assessment

No new ADR is required. The spike implements the already accepted AGP 9 module separation (ADR-001), preserves the minimal module layout (ADR-010), and changes no non-negotiable product invariant.
