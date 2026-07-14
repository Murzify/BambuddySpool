# CODE-002 Completion Report

## Summary

Implemented the shared root navigation shell and minimal Home screen. The root owns the four primary destinations,
responsive compact/wide navigation, safe navigation restoration, and the boundary for transient assignment and tag
mutation workflows.

## Changed Files

- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/App.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/bootstrap/ApplicationGraph.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/navigation/RootDestination.kt`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/app/root/RootComponent.kt`
- `shared/src/commonTest/kotlin/com/murzify/bambuddyspool/SharedCommonTest.kt`
- `shared/src/commonTest/kotlin/com/murzify/bambuddyspool/app/root/RootComponentTest.kt`
- `slop/BACKLOG.md`
- `slop/CHANGELOG.md`
- `slop/tasks/CODE-002.md`

## Implementation Details

- The shared root has Home, Spools, Printers, and Settings destinations. It uses a bottom navigation bar below
  600 dp and a navigation rail at wider widths.
- Each destination owns an independent Decompose child stack of safe list/detail routes. Decompose restores those
  histories through each destination context while the root StateKeeper restores the selected root destination.
- Home is deliberately minimal: NFC instruction, Bambuddy connection state, and NFC availability state. It supports
  not-configured, online, offline, stale, unavailable, and disabled presentation states without directly calling a
  repository from Compose.
- Assignment, confirmation, result/error, and tag mutation are modeled as transient root workflows. They are never
  persisted, so process recreation cannot replay a mutation or restore an authorization/session.
- Root navigation data is common serializable Kotlin only. No Android framework type enters navigation, Home state,
  or the shared root model.

## Verification

```text
./gradlew spotlessApply :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
git diff --check
```

Focused tests prove deterministic status reduction and restoration of independent Spools and Printers histories
after destination switching, while proving that a transient workflow is discarded.

No private `.env.local` file was read and no Bambuddy request was made.

## Definition of Done

- [x] Shared Android/iOS root navigation has the four required destinations and responsive compact/wide navigation.
- [x] Each root destination retains and restores its own safe Decompose child-stack history.
- [x] Credentials, authorizations, NFC sessions, transient workflows, and mutations are excluded from restoration.
- [x] Home stays minimal and renders connection/NFC offline or unavailable states.
- [x] Navigation models are common serializable Kotlin with no Android framework types.

## Follow-up Ownership

- `[CODE]-[003]` owns spool list/detail UI and its search/filter restoration values.
- `[CODE]-[004]` owns printer detail UI and manual-assignment entry.
- `[CODE]-[005]` through `[CODE]-[011]` own active assignment and NFC workflow behavior behind the transient root
  boundary established here.
