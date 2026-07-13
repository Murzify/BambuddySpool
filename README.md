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

### Continuous integration

GitHub Actions runs the baseline quality gates on pushes, pull requests, and manual dispatches. The workflow uses JDK 17, immutable action revisions, read-only repository permissions, and Gradle caches that exclude credentials, signing material, and `.env.local`. Superseded runs are cancelled only for pull requests.

Run the corresponding checks locally with JDK 17:

- Wrapper, workflow, and repository policy: `./gradlew help && ./ci/verify-repository.sh`
- Shared common tests: `./gradlew :shared:testAndroidHostTest`
- Android builds and host tests: `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :shared:testAndroidHostTest`
- Android device-test compilation: `./gradlew :shared:assembleAndroidDeviceTest`
- iOS compilation and tests: `./gradlew :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test`
- iOS shell build: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build`
- Formatting, static analysis, Android Lint, and architecture tests: `./gradlew spotlessCheck detekt :androidApp:lintDebug :shared:testAndroidHostTest`
- Dependency inventories: `./gradlew :shared:dependencies :androidApp:dependencies`

Use `./gradlew spotlessApply` to apply the configured Kotlin and Gradle Kotlin DSL formatting rules.

Three gates are deliberately marked as limited in the workflow: device tests are compiled but not executed on the API 23 and modern-device matrix; the Compose UI test harness is not yet available; and dependency inventories plus the repository scan are not an authoritative license or CVE audit. These limitations block claims of full release coverage instead of being reported as completed checks.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
