This is a Kotlin Multiplatform project targeting Android, iOS.

Project coordinates: [`Murzify/BambuddySpool`](https://github.com/Murzify/BambuddySpool)
Android application ID: `com.murzify.bambuddyspool`
License: Mozilla Public License 2.0 (`MPL-2.0`)

The v1 physical Android/NFC acceptance device is HONOR 50 (`NTH-NX9`) running Android 13 / API 33, build `NTH-N29 7.1.0.345(C10E1R5P1)`. The confirmed Bambu Lab A1 external slot mapping is `ams_id=255`, `tray_id=0`. Multi-external-slot printer support is not claimed without factual sanitized topology evidence; unknown topology fails closed.

## Project structure

- [`shared`](./shared) is the Kotlin Multiplatform library. It owns all product Compose UI and shared business logic.
  - `commonMain` contains platform-independent UI and logic and must not import Android framework types.
  - `androidMain` and `iosMain` contain narrow platform adapters and entry-point factories.
  - `commonTest`, `androidHostTest`, `androidDeviceTest`, and `iosTest` provide the required shared test boundaries.
  - `iosArm64` and `iosSimulatorArm64` produce the static `Shared` framework.
- [`androidApp`](./androidApp) is a thin Android application shell. It owns only the manifest, `Application`, launcher `Activity`, Android resources, build types, signing/R8 wiring, and shared-root rendering.
- [`iosApp`](./iosApp) is a thin Swift/Xcode shell that embeds and renders the shared Compose framework. Product screens must not be implemented in Swift.

The Android application uses only `debug` and `release` build types, supports API 23+, and does not lock orientation.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Android release smoke build: `./gradlew :androidApp:assembleRelease`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Android device-test APK: `./gradlew :shared:assembleAndroidDeviceTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
