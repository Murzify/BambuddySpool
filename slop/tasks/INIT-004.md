# Task Report: INIT-004 Establish Packages, UDF, Navigation, and Metro DI

**Status:** Complete

**Completed:** 2026-07-13

**Branch:** `task/init-004`

## Outcome

The shared application now has the prescribed architecture skeleton. `app` owns bootstrap, root, and navigation; `core` owns reusable application and platform contracts; and each prescribed `feature` package has an explicit boundary. Android and iOS construct retained mock application/component graphs and render the same shared root UI.

## Architecture

- `app/bootstrap` provides Metro `ApplicationGraph` and component graph factories.
- `app/root` provides the Decompose root component, root state, intents, effects, and pure reducer.
- `app/navigation` defines the four root destinations.
- `core/application` provides immutable reductions, the `StateFlow`-based UDF component contract, and application/component scopes.
- `core/platform` defines narrow interfaces for secure storage, NFC, settings, clipboard, haptics, dispatchers, and networking. The shell bootstrap receives them explicitly; there is no service lookup or runtime string key.
- `feature/setup`, `home`, `spools`, `printers`, `assignment`, `tagmutation`, and `settings` establish feature ownership without adding premature product behavior.

## Definition of Done

- [x] Android mock application/component graphs compile and the debug shell renders `App(root)`.
- [x] iOS mock application/component graphs compile and `MainViewController` renders `App(root)`.
- [x] Metro generates application- and component-scoped graphs and resolves constructor injection at compile time.
- [x] UDF state is exposed as read-only `StateFlow`; intents enter through `accept`; reducers return immutable state/effects and are side-effect free.
- [x] Decompose owns the root destination stack for Home, Spools, Printers, and Settings.
- [x] Architecture tests prove their gates with negative fixtures and reject forbidden common platform imports, domain framework imports, cross-feature imports, Android shell domain/feature imports, and large `expect` services.
- [x] `MainActivity` only creates the root graph, connects the shell, and renders shared Compose UI; it contains no product or domain decisions.
- [x] Platform behavior is represented by narrow injected interfaces; no large `expect/actual` service, service locator, or runtime core-service string key was added.

## Verification

All checks used Microsoft OpenJDK 17.0.17.

```text
./gradlew \
  :shared:allTests \
  :shared:testAndroidHostTest \
  :androidApp:assembleDebug \
  :shared:compileKotlinIosArm64 \
  :shared:iosSimulatorArm64Test
```

Result: `BUILD SUCCESSFUL` (90 actionable tasks: 20 executed, 70 up-to-date).

The Android host task executes the architecture gates. The iOS device target compiles, and the iOS Simulator test binary links and runs. A separate Xcode shell build was not required because the Swift/Xcode integration and exported `MainViewController` signature did not change.

## Risks and limitations

- Platform services are intentional no-op/mock bindings. Production Android adapters and product implementations remain owned by later backlog tasks.
- Root destinations currently render a minimal architecture screen; feature-specific UI and behavior remain out of scope for INIT-004.
- The mock roots use a local Decompose lifecycle registry. Production lifecycle/state restoration integration remains follow-up work with the real platform graph.

## Push status

Not pushed.
