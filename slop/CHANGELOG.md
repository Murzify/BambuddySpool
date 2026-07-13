# Agent Documentation Changelog

## 2026-07-13

- Completed `[DATA]-[001]` with a machine-readable manifest for the eight mandatory Bambuddy 0.2.4.7 operations and synthetic minimal-valid, representative-valid, and incompatible fixtures for every endpoint form.
- Recorded request parameters, authentication, used wire fields with types/nullability, response limits, JSON policy, provenance, and explicit unknown limits without retaining the private OpenAPI export or live data.
- Added host contract tests and repository policy checks for endpoint/category completeness, JSON validity, additive fields, synthetic provenance, incompatible evidence, and exclusion of private connection, credential, device, and tag material.
- Completed `[INIT]-[009]` by narrowing foundation implementation visibility, documenting the remaining public application and platform contracts, and replacing the template README with accurate clean-checkout, architecture, security, and CI guidance.
- Aligned CI with the repository's Microsoft JDK 17 daemon requirement, constrained Spotless to production source roots and Gradle scripts, and retained the Compose Android resource wiring required by device-test packaging.
- Triaged Stage 1 warnings and suppressions, refreshed resolved toolchain notes, and verified the complete Android, iOS, Xcode, quality, architecture, repository-policy, and configuration-cache matrix.
- Completed `[INIT]-[008]` with an adversarial foundation review covering the tracked tree, reachable history, Android manifests, CI, caches, dependencies, logging, signing, and private configuration boundaries.
- Closed the High backup/migration finding with explicit legacy, cloud, and device-transfer exclusions; made cleartext deny-by-default; restricted CI cache writes to trusted pushes; and expanded strong secret/signing signatures.
- Recorded threat-to-control evidence and assigned remaining implementation and release debt to the corresponding security backlog tasks without claiming unimplemented product controls.
- Completed `[INIT]-[007]` with shared Gradle/Xcode product metadata, catalog-owned JVM and ktlint versions, package-derived Android/shared/framework identifiers, and root-level Detekt defaults.
- Enforced settings-owned dependency repositories, retained the two required Gradle repository scopes, and confirmed configuration-cache storage and reuse without adding a build-logic module.
- Verified aligned Android debug/release, iOS framework/Simulator, and Xcode shell metadata while preserving the `shared`, `androidApp`, and `iosApp` topology.
- Completed `[INIT]-[006]` by removing generated greeting/platform samples, arithmetic placeholder tests, Compose template artwork, launcher icons, preview assets, stale resource settings, and unused dependency aliases.
- Replaced the common placeholder assertion with a root-navigation invariant, retained the dependency toolchain smoke coverage and required Ktor engines, and kept the Android device test as a supported-device/library-load smoke boundary.
- Expanded `.gitignore` coverage for local configuration, build output, IDE metadata, secrets, signing material, and Xcode user state while preserving required Gradle wrapper and iOS project files.
- Completed `[INIT]-[005]` with nine blocking GitHub Actions jobs covering wrapper/config validation, common tests, Android builds and host tests, iOS compilation/tests, formatting, Detekt, Android Lint, architecture checks, and baseline dependency/security inspection.
- Added JDK 17 setup, immutable action revisions, pull-request-only cancellation, safe Gradle caching, and repository checks that reject tracked local credentials, signing files, private keys, dynamic dependency versions, and mutable third-party action references.
- Marked device execution, Compose UI harness coverage, and authoritative license/CVE auditing as explicit CI limitations so they cannot be mistaken for completed release gates.
- Completed `[INIT]-[004]` with the prescribed `app`, `core`, and `feature` package boundaries, a Decompose root, an immutable UDF contract, and Metro application/component graphs.
- Added narrow secure storage, NFC, settings, clipboard, haptics, dispatchers, and networking interfaces with explicit compile-time mock bindings for both platform shells.
- Added Android host architecture gates for platform imports, domain framework imports, cross-feature imports, Android shell domain imports, and large `expect` services.

## 2026-07-12

- Completed `[INIT]-[003]` with the ADR-001 `shared` / `androidApp` / `iosApp` topology, thin platform shells, Android host/device test boundaries, and aligned iOS deployment targets.
- Verified Android debug/release and device-test APKs, physical device smoke on HONOR 50, both iOS targets, iOS Simulator tests, and the Xcode shell build.
- Completed `[INIT]-[002]` with a pinned JDK 17, Gradle, AGP, Kotlin, Compose, Room, Decompose, Ktor, Metro, Serialization, and DataStore compatibility matrix.
- Lowered the Android minimum SDK declaration from 26 to the required API 23 and aligned JVM bytecode with JDK 17.
- Added a cross-target dependency smoke test and verified Android debug, Android host tests, iOS device compilation, iOS Simulator tests, and the iOS shell build.
- Resolved `[INIT]-[001]` product and release parameters.
- Declared HONOR 50 as the physical Android/NFC acceptance device and recorded the API 36 emulator as non-physical test coverage.
- Retained Android application ID `com.murzify.bambuddyspool`.
- Confirmed GitHub repository `Murzify/BambuddySpool` and MPL-2.0 licensing.
- Scoped v1 topology support to the confirmed Bambu Lab A1 external slot (`255/0`) and recorded multi-slot evidence as a blocker only for multi-slot support claims.
