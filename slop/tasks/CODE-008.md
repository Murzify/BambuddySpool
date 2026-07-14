# CODE-008 Completion Report

## Outcome

Android now receives only canonical Bambuddy spool NDEF URI launches through the existing `singleTop` launcher
Activity. A recognized scan enters shared Processing before Compose renders, while all Android framework NFC objects
remain contained in the Android shell.

## Delivered

- Declared NFC as an optional device feature. The manifest has one NDEF-discovery filter for
  `bambuddy-spool://spool/<id>` and no broad `VIEW`, MIME, or technology-discovery fallback.
- Set the launcher Activity to `singleTop`; initial intent and `onNewIntent` both pass through one thin adapter.
  New accepted scans replace the root's transient operation input instead of adding an Activity to the back stack.
- Implemented strict URI admission: only the canonical common-code URI parse result is accepted. Different schemes,
  malformed paths, leading-zero forms, non-NDEF actions, and missing tag fingerprints are ignored.
- Added an Android NFC availability adapter that distinguishes unavailable hardware from disabled NFC. The shared
  root maps that capability without importing Android types, and manual/viewing surfaces remain usable in both cases.
- Added a transient shared NFC observation hand-off. It contains only the canonical URI and a non-empty physical-tag
  fingerprint; it is cleared on dismissal/cancellation/manual entry and deliberately absent from StateKeeper.

## Verification

```text
./gradlew spotlessApply :shared:testAndroidHostTest :androidApp:compileDebugAndroidTestKotlin :androidApp:assembleDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest --console=plain
./gradlew spotlessCheck detekt :androidApp:lintDebug :shared:testAndroidHostTest \
  :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
./ci/verify-repository.sh
git diff --check
```

All commands passed. The Android device test covers the canonical adapter and manifest launch mode on API 36. The
debug APK was installed and inspected on that emulator: Home, unavailable-NFC messaging, and all four navigation
destinations remained present and usable. The production minSdk stays API 23; this adapter uses only APIs available
from that level. No private configuration was read and no Bambuddy request was made.

## Definition of Done

- [x] Cold NDEF launch input is parsed before shared graph construction and initially renders Processing.
- [x] Active scans reach `onNewIntent` on the `singleTop` launcher; no scan-specific Activity is declared.
- [x] Unrelated, malformed, and noncanonical URIs are ignored.
- [x] Android `Intent`, `Tag`, and NFC adapter types do not enter shared code.
- [x] Missing/disabled NFC are explicit capability states and do not disable manual/viewing flows.
- [x] Android device, host, formatting, static analysis, lint, iOS compilation/tests, and repository policy checks pass.

## Follow-up Ownership

- `[CODE]-[009]` owns NFC session serialization, duplicate suppression, scan supersession, and routing this transient
  observation into assignment orchestration.
- `[CODE]-[010]` owns physical NDEF read/write/read-back primitives beyond the NDEF URI launch entry adapter.
