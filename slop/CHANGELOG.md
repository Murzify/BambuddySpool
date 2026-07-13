# Agent Documentation Changelog

## 2026-07-13

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
