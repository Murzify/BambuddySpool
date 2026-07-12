# Agent Documentation Changelog

## 2026-07-12

- Advanced `[INIT]-[002]` with a candidate pinned JDK 17, Gradle, AGP, Kotlin, Compose, Room, Decompose, Ktor, Metro, Serialization, and DataStore compatibility matrix; final iOS Simulator execution remains to be captured.
- Lowered the Android minimum SDK declaration from 26 to the required API 23 and aligned JVM bytecode with JDK 17.
- Added a cross-target dependency smoke test and verified Android debug, Android host tests, iOS device compilation, iOS Simulator tests, and the iOS shell build.
- Resolved `[INIT]-[001]` product and release parameters.
- Declared HONOR 50 as the physical Android/NFC acceptance device and recorded the API 36 emulator as non-physical test coverage.
- Retained Android application ID `com.murzify.bambuddyspool`.
- Confirmed GitHub repository `Murzify/BambuddySpool` and MPL-2.0 licensing.
- Scoped v1 topology support to the confirmed Bambu Lab A1 external slot (`255/0`) and recorded multi-slot evidence as a blocker only for multi-slot support claims.
