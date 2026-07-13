# DATA-004 Completion Report

## Outcome

Common network DTO validation and domain mapping now exists under `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/network`.

The implementation adds:

- a configured Bambuddy `Json` instance with `ignoreUnknownKeys=true`, `explicitNulls=false`, `isLenient=false`, and `coerceInputValues=false`;
- internal `@Serializable` DTOs for the mandatory sanitized contract shapes;
- public parsing functions that return domain values or `IncompatibleApiResponse` without exposing DTOs;
- assignment request encoding for the exact logical JSON shape `printer_id`, `ams_id`, `tray_id`, `spool_id`;
- assignment request validation for contract evidence;
- strict positive ID, tray-list, spool weight, and slot-coordinate validation before domain construction;
- configured and pending assignment state preservation in the domain `Assignment`;
- small domain status models for printer status and virtual trays.

The implementation does not add Ktor repositories, live networking, Room persistence, sync, UI, Gradle/CI changes, private-instance access, mutation requests, commits, or pushes.

## Validation Boundary

Transport DTOs remain in `core.network` and are internal. Domain-facing functions return `Printer`, `PrinterStatus`, `Spool`, `Assignment`, `Unit`, or typed incompatibility failures.

Missing non-null DTO fields fail through Serialization and are mapped to `IncompatibleApiResponse(MissingRequiredField)`. Unexpected wire shapes fail as `UnexpectedShape`. Domain-invalid identifiers, tray IDs, assignment coordinates, spool weights, and negative remaining values fail as `InvalidFieldValue`.

## Definition of Done

- [x] Serialization uses the required Bambuddy JSON policy.
- [x] DTO classes are kept inside network code and are not exposed to domain/UI.
- [x] Identification, mutation, and verification fields are validated before domain construction.
- [x] Failures map to `IncompatibleApiResponse`.
- [x] All sanitized contract fixtures decode or fail predictably.
- [x] Required assignment fields do not receive silent defaults.
- [x] Unknown additive fields are tolerated.
- [x] Exact assignment request JSON is tested.
- [x] Configured and pending assignment variants are tested.

## Verification

```text
./gradlew spotlessApply :shared:testAndroidHostTest
./gradlew spotlessCheck detekt :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
```

Both commands completed successfully.

## Limitations and Follow-up Ownership

`[DATA]-[007]` still owns bounded Ktor repositories, HTTP behavior, body limits, credential injection, and network error taxonomy.

`[DATA]-[005]` still owns Room schema and persistence.

`[DATA]-[009]` still owns slot topology resolution, including A1 mapping and fail-closed interpretation of `vt_tray`.
