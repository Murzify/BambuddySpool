# INIT-005 Completion Report

## Outcome

The repository now has a baseline GitHub Actions workflow for pushes, pull requests, and manual dispatches. It contains the nine required jobs:

1. Wrapper and configuration validation.
2. Shared common tests.
3. Android debug and release builds.
4. Android host tests.
5. Android device-test compilation.
6. Compose UI test compilation readiness.
7. iOS framework compilation, Simulator tests, and shell build.
8. Spotless, Detekt, Android Lint, and architecture tests.
9. Dependency inventories and baseline repository security checks.

The workflow uses JDK 17, pinned immutable action revisions, read-only repository permissions, safe Gradle caching, and pull-request-only cancellation. Quality commands are blocking and no step uses `continue-on-error`.

Spotless and Detekt are configured at the repository level. The initial formatting baseline was applied, declaration filenames were aligned with Kotlin conventions, and narrow suppressions document unavoidable Compose, Apple entry-point, and Kotlin Multiplatform filename exceptions.

## Definition of Done

- [x] Workflow YAML parses successfully and local equivalents are documented in `README.md`.
- [x] JDK 17 and safe Gradle caches are configured.
- [x] Formatting, Detekt, Android Lint, architecture tests, and repository policy failures block CI.
- [x] Incomplete device, UI, and authoritative license/CVE coverage is visibly labelled instead of producing false-green claims.
- [x] Workflow checkout does not persist credentials, and repository policy checks reject tracked local configuration, signing material, and private keys.

## Verification

The following commands passed locally with Microsoft JDK 17:

```text
./gradlew spotlessCheck detekt :androidApp:lintDebug :shared:testAndroidHostTest

./gradlew :shared:testAndroidHostTest \
  --tests com.murzify.bambuddyspool.ArchitectureSkeletonTest \
  --tests com.murzify.bambuddyspool.SharedCommonTest \
  --tests com.murzify.bambuddyspool.ToolchainCompatibilityTest

./gradlew :androidApp:assembleDebug :androidApp:assembleRelease \
  :shared:assembleAndroidDeviceTest \
  :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test \
  :shared:dependencies :androidApp:dependencies

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build

./ci/verify-repository.sh

ruby -e 'require "yaml"; Dir[".github/**/*.{yml,yaml}"].each { |file| YAML.parse_file(file); puts "valid YAML: #{file}" }'

git diff --check
```

`actionlint` was not installed locally, so workflow validation used Ruby Psych plus the workflow's own Gradle and repository-policy equivalents.

## Explicit Limitations

- Android device tests are compiled, but CI does not yet execute the required API 23 and modern-device matrix.
- The Compose UI test harness is not yet present, so CI verifies compilation readiness only.
- Dependency inventories and the baseline repository scan are not an authoritative license or CVE audit.
- Comprehensive dependency locking and verification remain outside this baseline task and must not be inferred from these checks.

Each incomplete gate emits a visible workflow warning and summary. These limitations prevent full release-coverage claims until later tasks replace them with factual execution.
