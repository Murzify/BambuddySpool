# Foundation Security Review

Date: 2026-07-13
Scope: repository foundation through `[INIT]-[008]`
Result: no open Critical or High findings

## Method

The review inspected the tracked tree, filenames and strong secret signatures in history reachable from `HEAD`, ignore rules, Android debug/release merged manifests, Gradle dependencies and plugins, GitHub Actions permissions/actions/cache behavior, signing configuration, and source references to logging, analytics, crash reporting, telemetry, persistent diagnostics, private hosts, and private contract artifacts.

Potential secret values were never printed. The private local configuration file and private Bambuddy instance were not read or contacted.

## Findings

| ID | Severity | Finding | Disposition | Evidence | Owner |
|---|---|---|---|---|---|
| BOOT-SEC-001 | High | Android backup was enabled, allowing app data and a future encrypted token blob to enter cloud backup or device migration. | Fixed. Backup is disabled and all supported storage domains are excluded from legacy backup, cloud backup, and device transfer. | `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/res/xml/backup_rules.xml`, `androidApp/src/main/res/xml/data_extraction_rules.xml`; debug and release merged manifests. | `[INIT]-[008]`; `[SEC]-[001]` must retain and test the control with the real Keystore implementation. |
| BOOT-SEC-002 | Medium | Cleartext denial relied on the target-SDK default and could silently change during later manifest work. | Fixed. The application explicitly denies cleartext traffic. Re-enabling HTTP requires the origin-consent policy and tests from TECHSPEC 13.4. | Android manifest and debug/release merged manifests. | `[INIT]-[008]`; `[SEC]-[002]` owns the deliberate HTTP/TLS policy implementation. |
| BOOT-SEC-003 | Medium | Most CI jobs could request Gradle cache writes even though only trusted pushes need to populate the cache. | Fixed. The composite action is read-only by default; only the validation job on a push requests write access. Pull requests remain read-only. | `.github/actions/setup-build/action.yml`, `.github/workflows/ci.yml`. | `[INIT]-[008]`; `[SEC]-[005]` owns final supply-chain hardening. |
| BOOT-SEC-004 | Medium | The baseline scanner covered only a narrow set of signing files and repository-specific environment assignments. | Fixed. Signing extensions and strong provider/token/private-key signatures were expanded while match content remains suppressed. | `ci/verify-repository.sh`; current-tree and reachable-history scans passed. | `[INIT]-[008]`; `[SEC]-[005]` owns controlled negative fixtures and authoritative gates. |
| BOOT-SEC-005 | Medium | Dependency verification, locking, authoritative license classification, and exploitable CVE gating are not implemented. | Accepted release-blocking debt. Versions, wrapper checksum, and Actions are pinned today; this review does not claim comprehensive supply-chain coverage. | TECHSPEC 18.6-18.7 and 19.3; the CI job is visibly marked limited. | `[SEC]-[005]`. |
| BOOT-SEC-006 | Medium | Production secure storage, redirect/TLS policy, redaction, and mutation guards are interfaces or planned work, not implemented controls. | Accepted implementation debt, not a currently exposed credential path: the bootstrap accepts no token, performs no production request, and performs no mutation. | Platform contracts and mock bootstrap graph; TECHSPEC 13.3-13.9 and 19.3. | `[DATA]-[006]`, `[DATA]-[007]`, `[SEC]-[001]` through `[SEC]-[004]`. |
| BOOT-SEC-007 | Medium | Permanent release signing and exact artifact verification are intentionally absent. | Accepted release-blocking debt. CI builds unsigned/non-production artifacts and contains no signing material. | TECHSPEC 18.4, 18.8-18.9; Android build configuration and CI workflow. | `[SEC]-[006]`. |

The backup fix follows Android's documented requirement for both legacy rules and `data-extraction-rules`; Android also warns that `allowBackup=false` alone may not disable device-to-device transfer on every manufacturer implementation. See [Android Auto Backup guidance](https://developer.android.com/identity/data/autobackup).

## Threat-to-Control Map

| TECHSPEC 19.3 threat | Foundation control and evidence | Residual risk / owner |
|---|---|---|
| Token leakage in logs/errors | No token input or persistence exists; no logging, analytics, crash, telemetry, HTTP logging, or persistent-diagnostic dependency/call was found. Repository scanner suppresses match contents. | Structural redaction and ephemeral diagnostics remain `[SEC]-[003]`; credential storage remains `[SEC]-[001]`. |
| Token forwarded to unrelated host | There is no production HTTP client path; cleartext is explicitly denied. | Redirect/header attachment rules are `[DATA]-[007]` and `[SEC]-[002]`. |
| MITM over HTTP | Cleartext is deny-by-default in both debug and release manifests. | HTTP after explicit origin consent is an accepted product compromise in TECHSPEC 13.4 and RISK-005, owned by `[SEC]-[002]`. |
| MITM with invalid TLS override | No permissive trust manager or TLS override implementation exists. | Host-scoped override is the accepted ADR-009 / RISK-004 compromise and must be implemented by `[SEC]-[002]`. |
| Wrong physical slot | The bootstrap has no assignment transport or mutation path. Architecture boundaries are present. | Fail-closed topology is `[DATA]-[009]`; enforcement is `[SEC]-[004]`. |
| Duplicate assignment POST | No POST implementation exists. | Immutable retry and exact verification are `[CODE]-[005]`, `[CODE]-[007]`, and `[SEC]-[004]`. |
| Malicious NFC payload | No Android NFC adapter or tag mutation implementation exists. | Canonical parsing and mutation authorization are `[CODE]-[008]` through `[CODE]-[011]` and `[SEC]-[004]`. |
| Wrong-tag overwrite | No tag write primitive exists. | Fingerprint continuity and reread verification are `[CODE]-[010]`, `[CODE]-[011]`, and `[SEC]-[004]`. |
| Stale offline mutation | No repository mutation path exists. | Freshness guards are `[DATA]-[010]`, `[CODE]-[005]`, and `[SEC]-[004]`. |
| Server response memory exhaustion | No production response handling exists. | Decompressed limits and strict mapping are `[DATA]-[004]`, `[DATA]-[007]`, and `[DATA]-[013]`. |
| Private instance data in public repository | Private local configuration is ignored; no tracked private contract/host pattern, sensitive historical path, generated artifact, strong secret signature, or signing artifact was found. | Fixture sanitization remains mandatory under TECHSPEC 17.4 and `[DATA]-[001]`; recurring gates belong to `[SEC]-[005]`. |
| Supply-chain tampering | Versions, Gradle distribution checksum, and Actions are immutable; workflow permissions are read-only; checkout does not persist credentials; PR caches are read-only. | Verification metadata, locks, licenses, and CVE gates remain `[SEC]-[005]`. |

## Additional Evidence

- Reachable history: zero sensitive configuration/signing paths and zero strong secret-signature commits detected.
- Tracked tree: zero generated build/IDE paths and zero private OpenAPI/Swagger artifact names detected.
- Source/dependencies: no analytics, crash reporter, telemetry, HTTP logger, persistent logger, stack-trace printer, or Android logging call detected.
- CI: repository contents permission only, immutable action SHAs, non-persistent checkout credentials, no secret variables, unsigned Xcode build, and read-only caches outside trusted pushes.
- Android: debug and release builds, Lint, Detekt, formatting, and host tests pass; release merged manifest has no debuggable declaration and explicitly denies backup and cleartext.

## Accepted Risks

The following are accepted only as already documented, owned work; they do not authorize release without their gates:

- HTTP token exposure after explicit consent: TECHSPEC 13.4, 20 item 3, RISK-005; owner `[SEC]-[002]`.
- Host-scoped invalid-certificate override: TECHSPEC 13.5, ADR-009, RISK-004; owner `[SEC]-[002]`.
- No persistent diagnostics: TECHSPEC 13.9, 20 item 8, RISK-006; owner `[SEC]-[003]`.
- Token lifetime in process memory: TECHSPEC 13.8 and 20 item 10; owners `[DATA]-[006]` and `[SEC]-[001]`.
- Mock/fixture CI instead of a live private instance: TECHSPEC 20 item 7; owners `[DATA]-[001]` and `[SEC]-[009]`.
- Supply-chain gate incompleteness: TECHSPEC 18.6-18.7; owner `[SEC]-[005]`.

Any implementation that introduces credentials, HTTP/TLS exceptions, persistence, diagnostics, mutation, NFC writes, signing, or release publication must reopen the corresponding row and supply adversarial test evidence.
