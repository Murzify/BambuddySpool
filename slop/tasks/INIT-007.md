# INIT-007 Completion Report

## Outcome

Product name, base package, version code, version name, and iOS deployment target now have one source of truth in `config/project-metadata.xcconfig`. Gradle loads the file during settings evaluation, while Xcode includes it directly. Android application metadata, shared namespace, Kotlin framework bundle identifier, and iOS product metadata are derived from those values.

The JVM target and ktlint version are now stored in the Gradle version catalog. Both Android modules derive Kotlin bytecode settings from the catalog, and the Android application derives Java compatibility from the same value. Spotless no longer contains a direct ktlint version literal.

Common Detekt configuration is applied once from the root build. Modules retain only their intentionally different source boundaries. Dependency repositories are settings-owned, and project-level repository declarations fail the build.

No convention or build-logic module was added. The Gradle topology remains the root build plus `:androidApp` and `:shared`; `iosApp` remains the required Xcode shell rather than a Gradle module. The existing `config`, `ci`, and `slop` directories are configuration, automation, and agent-documentation boundaries, not build modules.

## Intentional Gradle DSL Boundaries

- Plugin-resolution repositories and dependency-resolution repositories are separate Gradle settings scopes. Their restricted Google filters remain explicit in both scopes because settings script helpers are not visible inside the early `pluginManagement` evaluation phase without adding build logic.
- Android application and Android KMP library plugins expose different DSLs, so each module must assign its catalog-owned SDK values to its own extension.
- Kotlin and Java compatibility are distinct compiler settings in the Android application, but both derive from the same catalog value.

## Definition of Done

- [x] Dependency, plugin, formatter, SDK, and JVM versions are centralized and non-dynamic.
- [x] Android application, shared library/framework, and iOS shell metadata derive from aligned sources.
- [x] A representative Gradle configuration-cache entry stores and reuses successfully.
- [x] The topology remains limited to `shared`, `androidApp`, and the `iosApp` shell, with no new Gradle modules.
- [x] Quality, Android debug/release, host/common, iOS framework/Simulator, and Xcode shell checks pass.

## Verification

The following commands passed locally with JDK 17:

```text
./gradlew help --configuration-cache
./gradlew help --configuration-cache

./gradlew spotlessCheck detekt :androidApp:lintDebug \
  :androidApp:assembleDebug :androidApp:assembleRelease \
  :shared:testAndroidHostTest :shared:compileKotlinIosArm64 \
  :shared:iosSimulatorArm64Test

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build

./ci/verify-repository.sh

git diff --check
```

The second `help` run reported `Reusing configuration cache` and `Configuration cache entry reused`. Xcode resolved the shared metadata to product `BambuddySpool`, bundle identifier `com.murzify.bambuddyspool.BambuddySpool`, version `1.0` / build `1`, and iOS deployment target `18.5`.

## Residual Risk

The shared metadata file intentionally uses the intersection of Java properties and Xcode configuration syntax. Future entries must remain simple scalar `KEY = VALUE` assignments so both consumers parse them identically.
