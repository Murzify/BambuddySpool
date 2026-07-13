# DATA-006 Completion Report

## Outcome

Common settings and credential contracts now exist under:

- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/settings`
- `shared/src/commonMain/kotlin/com/murzify/bambuddyspool/core/security`

The implementation adds:

- common URL canonicalization for HTTP/HTTPS Bambuddy base URLs with scheme, host, effective port, and optional base path;
- origin metadata for consent and connection comparison;
- a nonsecret `ConnectionSettings` model for canonical base URL, configured origin, default printer, HTTP consent origin, TLS override hostname, and schema version;
- explicit DataStore field names for the required nonsecret settings, with no token or secret fields;
- `SecretValue`, a non-printing token wrapper without a public value property or serialization annotation;
- `SecureTokenStore`, a common secure-token boundary that stores only the Bambuddy API token;
- `ConnectionReplacementService`, which validates URL syntax and connection/token reachability before changing active settings or token data;
- token replacement that validates against the active base URL before saving the new token.

The existing platform `SecureStorage` contract now extends the common `SecureTokenStore`. Stage 1 mock platform services were updated to the new token method names.

## Security and Atomicity Boundary

Connection replacement performs validation before any mutation. If syntax validation, HTTP warning acknowledgement, instance-change acknowledgement, or connection validation fails, the previous settings, token, and cache snapshot are left unchanged.

Token replacement validates the new token first. A validation failure preserves the previous token and does not clear the cached domain snapshot.

HTTP consent is scoped to the canonical origin. A scheme/host/port origin change requires a fresh acknowledgement. TLS override retention is scoped to the exact canonical hostname: host changes clear the override, while port and base-path changes retain it.

## Definition of Done

- [x] DataStore field contract covers canonical URL, configured origin metadata, default printer, HTTP consent, TLS override hostname, and schema version.
- [x] The token is represented only by `SecretValue` and stored only through `SecureTokenStore`.
- [x] `SecretValue.toString()` is redacted and no serializable token state is introduced.
- [x] Host changes reset TLS override.
- [x] Port/path changes preserve the hostname-scoped TLS override.
- [x] Origin changes reset HTTP consent and require renewed HTTP acknowledgement.
- [x] Failed connection validation preserves previous settings, token, and cache snapshot.
- [x] Failed token validation preserves the previous token and cache snapshot.

## Verification

```text
./gradlew :shared:testAndroidHostTest
./gradlew spotlessCheck detekt
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
./gradlew spotlessCheck detekt :shared:testAndroidHostTest :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
```

All commands completed successfully after fixing implementation and style issues found during the first runs.

## Limitations and Follow-up Ownership

This task intentionally does not implement Android Keystore storage, actual DataStore persistence, Ktor repositories, HTTP clients, redirect enforcement, TLS clients, sync execution, setup/settings UI, or Room snapshot synchronization.

`[DATA]-[007]` owns bounded Ktor repositories and credential injection into requests. `[CODE]-[001]` owns setup/settings UI and user-facing connection management. `[SEC]-[001]` owns Android Keystore-backed token storage. `[SEC]-[002]` owns platform HTTP/TLS enforcement.
