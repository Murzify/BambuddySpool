# Bambuddy NFC Spool Manager — Implementation Backlog

Sources: `slop/PRD.md` and `slop/TECHSPEC.md`, based on Bambuddy OpenAPI `0.2.4.7`. Tasks are ordered for execution. IDs express dependencies; a task is incomplete until its DOD is met. Changing a non-negotiable TECHSPEC invariant requires an ADR first.

## Stage 1. Initialization and Architecture Foundation

- [x] [INIT]-[001] Resolve Product and Release Parameters
Task Context
Resolve or record an owner and deadline for all release-blocking questions: supported Android device and OS/build/NFC chipset, application ID (proposed `ru.nonamee.bambuddyspoolmanager`), GitHub owner/repository/license, and sanitized multi-external-slot fixtures. Record them in `docs/decisions/project-parameters.md`. Missing multi-slot evidence must not block the known A1 mapping (`ams_id=255`, `tray_id=0`); unknown topology must fail closed. Never invent owner-provided facts.
Task DOD
Every decision has a status, owner, deadline, and impact; confirmed values match Gradle, manifest, and README; unresolved items are explicit release blockers; deviations from the baseline have ADRs; no secret or private OpenAPI export is committed.

- [x] [INIT]-[002] Run the Toolchain Compatibility Spike
Task Context
Prove a compatible pinned matrix for JDK 17, Gradle 9.1+, AGP 9.0.x, Kotlin/KGP, Compose Multiplatform, Room KMP, Decompose, Ktor, Metro, Serialization, and DataStore. Keep AGP application and KMP plugins in separate modules. Prove API 23, `iosArm64`, `iosSimulatorArm64`, JVM/iOS shared tests, and minimal shared Compose rendering. Dynamic versions are forbidden.
Task DOD
`docs/decisions/toolchain.md` records versions and rationale; wrapper and catalog are pinned; Android debug, iOS shell, and shared smoke tests build; workarounds are documented; dependency resolution is reproducible.

- [x] [INIT]-[003] Establish the shared/androidApp/iosApp Topology
Task Context
Implement ADR-001. `shared` owns KMP product UI and business logic; `androidApp` owns only manifest, Application, Activity, Android resources, build types, signing, and R8 wiring; `iosApp` is a thin Xcode shell. Add required common, Android host/device, and iOS source sets. Support minSdk 23, only debug/release, and do not lock orientation.
Task DOD
The module/source-set topology matches TECHSPEC; both shells render shared Compose UI; common code contains no Android types; Android debug/release and API 23 smoke builds pass; README documents the structure.

- [x] [INIT]-[004] Establish Packages, UDF, Navigation, and Metro DI
Task Context
Create the prescribed `app`, `core`, and `feature` package boundaries. Add a Decompose root, immutable UDF contract (`StateFlow`, intents, pure reducers, effects), and Metro constructor-injected graph with Application/Component scopes. Service locators and runtime string keys are forbidden. Define narrow platform interfaces for secure storage, NFC, settings, clipboard, haptics, dispatchers, and networking.
Task DOD
Android and iOS mock root graphs compile and render; compile-time bindings pass; architecture tests reject forbidden imports; Activity contains no domain decisions; no large `expect/actual` service exists.

- [x] [INIT]-[005] Add Baseline Quality Gates and CI
Task Context
Create GitHub Actions for push, pull request, and manual dispatch. Add wrapper/config, common, Android build/host/device/UI, iOS, lint/Detekt/architecture, and dependency/license/security jobs. Cancel superseded PR runs only. Configure formatting, Lint, Detekt, and forbidden-import checks.
Task DOD
Workflow syntax and documented local equivalents pass; JDK 17 and safe caches are configured; quality failures block CI; incomplete jobs report their limitation instead of false success; credentials and signing material are never cached.

- [x] [INIT]-[006] Remove Bootstrap Debris
Task Context
Remove generated demo screens/resources, obsolete Gradle settings, unused dependencies, IDE artifacts, and build outputs. Preserve required wrapper and iOS files. Update `.gitignore` for local, build, secret, IDE, and signing artifacts.
Task DOD
No template/demo code or sensitive/local artifact remains; smoke builds pass after cleanup; the diff contains only justified files.

- [x] [INIT]-[007] Deduplicate Build and Architecture Configuration
Task Context
Centralize dependency versions in the catalog and remove repeated SDK values, package names, compiler flags, repositories, and conventions. Do not add Gradle modules without a measurable need.
Task DOD
No dynamic or duplicate versions remain; module metadata agrees; configuration cache works; the topology remains limited to `shared`, `androidApp`, and `iosApp`, with documented exceptions.

- [x] [INIT]-[008] Review Foundation Security
Task Context
Review the foundation against the threat model: public repository safety, no private OpenAPI/signing material/tokens, no analytics/crash reporting/persistent diagnostics, safe cleartext defaults, backup posture, and secret-safe CI output.
Task DOD
`docs/security/bootstrap-review.md` maps threats to controls, evidence, and owners; all Critical/High findings are fixed; secret scans pass; accepted risks reference TECHSPEC.

- [x] [INIT]-[009] Polish Foundation Code and Documentation
Task Context
Format and inspect naming, visibility, package ownership, public contract KDoc, README, and ADRs. Avoid abstractions without a current consumer.
Task DOD
Format, Detekt, Lint, architecture checks, and smoke builds pass; warnings and TODOs are triaged; clean-checkout Android/iOS/test instructions are accurate.

## Stage 2. Data Model, API, Persistence, and Synchronization

- [x] [DATA]-[001] Create the Sanitized API Contract and Fixtures
Task Context
Document only the mandatory Bambuddy 0.2.4.7 endpoints. For every critical endpoint add minimal-valid, representative-valid, and incompatible synthetic JSON preserving real shape/nullability and additive fields. Exclude private hosts, tokens, access codes, personal inventory, and the private OpenAPI export.
Task DOD
The manifest records method, path, auth, used fields, request, and limits; all fixture categories exist, state provenance, remain reviewable, and pass secret/private-data scans.

- [x] [DATA]-[002] Implement Strict Common Domain Models
Task Context
Implement typed IDs, immutable `SlotKey`, printer/spool/slot/assignment models, generation-bound assignment commands, typed assignment results, NFC mutation outcomes, and typed failures for incompatibility, stale/offline state, unsupported topology, and verification mismatch. Raw exceptions and UI strings are not domain models.
Task DOD
Domain imports no UI, DI, transport, persistence, or platform APIs; invalid identifiers/coordinates cannot be silently created; SlotKey equality is exact; model tests and iOS compilation pass.

- [x] [DATA]-[003] Implement the Canonical NFC Payload Codec
Task Context
Implement `bambuddy-spool://spool/<positive-decimal-id>` in common code. Scheme is case-insensitive; host is exactly `spool`; require one path segment and reject query, fragment, signs, whitespace, non-decimal input, and overflow. Accept leading zeros on read and canonicalize on write. Model all TECHSPEC read classifications; multiple records are unsupported.
Task DOD
Codec has no Android dependency; exhaustive parsing/round-trip tests pass; unsupported payloads are never partially recovered; no tag-locking API exists.

- [x] [DATA]-[004] Implement DTO Validation and Domain Mapping
Task Context
Use Serialization with `ignoreUnknownKeys=true`, `explicitNulls=false`, `isLenient=false`, and `coerceInputValues=false`. Keep DTOs inside network code. Validate all identification/mutation/verification fields strictly and map failures to `IncompatibleApiResponse`.
Task DOD
All contract fixtures decode or fail predictably; required fields never receive silent defaults; DTOs/raw bodies do not reach domain/UI; exact assignment JSON and configured/pending variants are tested.

- [x] [DATA]-[005] Design the Room KMP Schema and Queries
Task Context
Create printers, printer_slots, spools, assignments, and sync_metadata with SlotKey uniqueness, snapshot generation, timestamp, cleanup semantics, indexes, and projections. Support indexed spool search/filter/sort and printer/assignment views. UI observes Room and never retains a full snapshot.
Task DOD
Schema and baseline migration are documented/exported; constraints prevent ambiguous slots; indexed query plans scale to the synthetic dataset; DAO returns persistence/domain projections, never transport DTOs; iOS targets compile.

- [ ] [DATA]-[006] Implement Settings and Credential Contracts
Task Context
Store canonical URL, origin metadata, default printer, origin-scoped HTTP consent, hostname-scoped TLS override, and schema version in DataStore. Store only the token in SecureStorage behind a non-printing secret type. Define atomic connection/token replacement so failed validation preserves active data.
Task DOD
Host changes reset TLS consent while port/path changes do not; origin changes reset HTTP consent; tokens cannot be revealed or serialized; atomic replacement and failure preservation are tested.

- [ ] [DATA]-[007] Implement Bounded Ktor Repositories
Task Context
Implement mandatory operations with Android and Darwin engines, X-API-Key credential provider, 3-second connect and 10-second request timeouts, URL builders, and decompressed response limits from TECHSPEC. Disable unrestricted redirects and expose hooks for the security policy. Return domain values and typed errors only.
Task DOD
MockEngine tests cover every operation; credentials never enter exception text; oversized responses fail safely; 4xx/5xx/transport/contract/TLS failures are distinct; base paths work; iOS compiles.

- [ ] [DATA]-[008] Implement Atomic Snapshot Synchronization
Task Context
Fetch printers, every status/topology, archived-inclusive spools, and assignments. Publish only a fully valid snapshot in one Room transaction. Join concurrent sync triggers, bound status concurrency to four, debounce foreground by two seconds, and prevent older sync generations from overwriting post-mutation state.
Task DOD
Rollback, stale preservation, joining, debounce, deletion, and generation races have deterministic tests without sleeps; partial responses never publish; cache rebuild preserves settings/token; UI reads Room.

- [ ] [DATA]-[009] Implement a Fail-Closed SlotTopologyResolver
Task Context
Use `PrinterStatus.vt_tray` as the physical source and assignments as state/evidence. Isolate the known A1 `255/0` rule, represent multiple external slots, apply label precedence, and keep AMS read-only. Never infer coordinates from order, choose the first slot, or expose a partial unknown topology.
Task DOD
Pure common tests cover A1, multiple slots, AMS, missing, contradictory, and unknown data; unsupported topology blocks mutation with a typed reason; mapping constants are not duplicated.

- [ ] [DATA]-[010] Implement Cache and Availability Projections
Task Context
Expose Room/DataStore flows for all features with InitialLoading, Content, ContentRefreshing, and FatalErrorWithoutCache plus stale/error/mutation availability. Cached viewing survives offline/auth failures; mutations require fresh server context. Implement database-backed cancellable search and default-printer lifecycle rules.
Task DOD
Features do not know Room; large lists are lazy/pageable; stale data remains visible; disabled mutations explain why; one/multiple/deleted default transitions are tested.

- [ ] [DATA]-[011] Remove Unused Models and Persistence Artifacts
Task Context
Remove unused DTO fields, entities, DAOs, indexes, fixtures, migrations, and duplicate models while retaining migration schema exports. Never persist raw responses, diagnostics, access codes, or unrelated Bambuddy data.
Task DOD
Dead code is gone; schema stores only required data; contract/database tests and iOS compile pass; secret scans remain clean.

- [ ] [DATA]-[012] Deduplicate Validation and Mapping Logic
Task Context
Centralize ID/URI/URL validation, DTO mapping, SlotKey identity, errors, and snapshot transactions without introducing speculative mapper/repository frameworks.
Task DOD
Each invariant has one authoritative implementation; NFC and manual flows share models; dependency direction and behavior-focused tests remain intact.

- [ ] [DATA]-[013] Review Data-Layer Security
Task Context
Review storage classification, SQL constraints, malformed/oversized responses, secret lifetime, URL metadata, fixtures, DB corruption, generation races, numeric overflow, and malicious NFC input.
Task DOD
Critical/High issues are fixed with negative tests; token storage is exclusive; allocation bounds are enforced where possible; evidence is recorded in `docs/security/data-review.md`.

- [ ] [DATA]-[014] Polish Data and Infrastructure Code
Task Context
Format code, narrow visibility, document public contracts, and inspect cancellation, dispatcher ownership, transaction boundaries, and compatibility workarounds.
Task DOD
Common, Room, network, formatting, static, architecture, and iOS checks pass; public API is minimal; cancellation is preserved; schema/contract/sync docs match code.

## Stage 3. Product Implementation

- [ ] [CODE]-[001] Implement Setup and Atomic Connection Management
Task Context
Build the shared one-page URL/token/Test/Save setup. Validate reachability and auth only. Support the permitted URL forms, critical instance-change warning, atomic connection replacement, cache/default reset, and initial sync. Validate token replacement before saving it.
Task DOD
Setup and Settings share one service; secrets are absent from saved/navigation state; validation failure changes nothing; confirmed server change resets correct data/consents; accessible reducer-tested UI uses resources.

- [ ] [CODE]-[002] Implement Root Navigation, Home, and Restoration
Task Context
Create Decompose Home/Spools/Printers/Settings roots with independent child stacks, bottom navigation below 600dp and rail otherwise. Assignment/tag mutation are transient root workflows. Restore only safe navigation/search/filter state, never credentials, authorization, NFC sessions, or mutations.
Task DOD
Android/iOS navigation works without Activity stacking; safe state survives recreation; Home remains minimal; online/offline/NFC states render; navigation arguments contain no Android type.

- [ ] [CODE]-[003] Implement Spools and Spool Details
Task Context
Implement default and extended filters, 150ms cancellable search, specified sorting, accessible metadata/color display, remaining amount, assignment, tag actions, and manual assignment. Do not edit spool metadata. A deleted tagged spool produces a precise relink option.
Task DOD
The lazy list handles 25k records; filters restore; offline content remains viewable while mutation is disabled; resources, semantics, reducer tests, and UI tests cover all actions.

- [ ] [CODE]-[004] Implement Printers and Manual Assignment Entry
Task Context
Show assignment-relevant printer details. External slots are labeled/selectable; AMS is visible read-only; unknown topology blocks mutation. Manual assignment creates the same AssignmentIntent used by NFC.
Task DOD
Screens use Room projections; all topology states render correctly; AMS cannot be targeted; manual and NFC share orchestration; stale/offline guards, accessibility, and tests pass.

- [ ] [CODE]-[005] Implement AssignmentOrchestrator
Task Context
Implement the complete shared state machine. Refresh relevant status/assignments and validate spool before mutation. If SlotKey changes, return to selection without POST. Allow zero confirmation only under every TECHSPEC safety condition. Keep commands immutable across retries and do not persist/replay mutations.
Task DOD
Decisions are pure and effects isolated; pre-POST cancellation and post-POST application scope are correct; stale/offline/unsupported states never POST; every branch is typed and deterministic.

- [ ] [CODE]-[006] Implement Combined Confirmation and Resolution UI
Task Context
Use one surface for printer, slot, move warning, all current locations, target replacement, and final effect. A default only preselects when several printers exist. Target replacement alone adds no confirmation. Fresh exact assignment returns AlreadyAssigned without POST.
Task DOD
Every path has zero or one confirmation; required focus order and move disclosure hold; tests prove selection, conflicts, replacement, and AlreadyAssigned behavior.

- [ ] [CODE]-[007] Implement POST Retry and Exact Verification
Task Context
Retry only retryable transport/5xx errors at 500ms, 1s, and 2s, maximum four identical POSTs, without pre-retry GET. Do not auto-retry 4xx, contract, topology, TLS, or invalid command failures. After POST poll assignments immediately, +200ms, +500ms and require exactly one matching printer/ams/tray/spool. Verification never resends POST.
Task DOD
Virtual-time tests prove calls/delays; duplicate slot state and HTTP-only success never pass; user Retry starts a fresh cycle; configured/pending outcomes and isolated haptic/clipboard failures render correctly.

- [ ] [CODE]-[008] Implement Android NFC Entry and Read Adapter
Task Context
Register only the canonical URI filter with `singleTop`; route initial and `onNewIntent` scans through a thin adapter. Render Processing within one second before full bootstrap. Keep devices without/with-disabled NFC usable and keep Android Intent/Tag/NdefMessage out of common code.
Task DOD
Cold and active scans work without Activity accumulation; unrelated URIs are ignored; first render is not sync-blocked; API 23, disabled/missing NFC, manifest, and device tests pass.

- [ ] [CODE]-[009] Implement NFC Scan Coordination
Task Context
Serialize one NFC session. Suppress accepted duplicate fingerprints within one second. Before POST, a new scan replaces the workflow; after POST, finish verification and retain only the newest pending scan; a Success scan immediately returns to Processing. Use a monotonic clock.
Task DOD
Deterministic race tests prove suppression, supersession, and active-plus-latest capacity; parallel POSTs cannot occur; Android objects and restorable mutation state are absent.

- [ ] [CODE]-[010] Implement Android NFC Write and Read-Back Primitives
Task Context
Support writable Ndef and NdefFormatable, enforce capacity, write one URI record, reconnect, and independently reread. Verify canonical application URIs, not bytes. Classify read-only/unsupported tags without mutation. Bind authorization to the expected fingerprint and expose no read-only locking.
Task DOD
All uncertain write phases have correct typed outcomes; overflow/different-tag cases never write; generated NdefMessage tests cover capabilities and records; irreversible APIs are absent.

- [ ] [CODE]-[011] Implement Link, Overwrite, and Clear Workflows
Task Context
Fresh-validate selected spools online. Empty tags can link; nonempty different/unknown payloads require explicit old/new overwrite confirmation; the same payload is Already linked. Clear always confirms and leaves a writable readable empty NDEF tag. Never mutate Bambuddy. Do not automatically retry physical writes; retry starts with read-before-write.
Task DOD
The shared state machine covers every read and mutation outcome; canonical reread is the sole success boundary; authorization is not restored; wrong tags are never written; lost-tag and deleted-spool scenarios are tested.

- [ ] [CODE]-[012] Implement Adaptive, Accessible, Resource-Based UI
Task Context
Keep all product UI in common Compose. Support compact/medium/expanded widths, unlocked orientation, system themes, English resources, semantic roles/labels/state, 48dp targets, Processing live region, Success/Error focus, disabled reasons, large fonts, scrolling, and non-gesture alternatives.
Task DOD
Critical screens work across widths/orientations/themes/font scales; TalkBack and confirmation focus order pass; color is never the sole identifier; no user-facing string is hardcoded.

- [ ] [CODE]-[013] Add Performance and Graceful-Degradation Hooks
Task Context
Measure monotonic stages for the two-second assignment and one-second cold-launch targets without telemetry or persistent logs. Keep network/DB/NFC I/O off main and enable debug StrictMode. Secondary failures must not invalidate verified outcomes. Add no worker, wake lock, background queue, polling, or circuit breaker.
Task DOD
Ephemeral/test timing is runnable; StrictMode-covered flows are clean; the TECHSPEC degradation table is implemented; release contains no analytics, crash reporting, or persistent diagnostics.

- [ ] [CODE]-[014] Implement the iOS Mock Shell
Task Context
Compile both iOS targets, run shared tests, render the shared root, and provide explicit deterministic mocks for unsupported platform services. Do not create separate Swift product screens or pretend mutations work.
Task DOD
iOS shell launches and navigates shared UI; unsupported actions are disabled; Simulator smoke passes; Android dependencies do not leak into common code.

- [ ] [CODE]-[015] Remove Feature Debris
Task Context
Remove prototypes, duplicate screens, temporary production fakes, unreachable states, debug buttons, obsolete routes/resources, and unused dependencies while retaining required test fakes and iOS mocks.
Task DOD
Only one assignment/NFC implementation exists; production contains no fake data or dead route; Android/iOS builds and feature tests pass; out-of-scope features are absent.

- [ ] [CODE]-[016] Deduplicate UI and Orchestration
Task Context
Unify NFC/manual assignment, setup/settings connection, result/error surfaces, responsive navigation, and tag operations where semantics match. Avoid god components and speculative frameworks.
Task DOD
Entry source is the only NFC/manual difference; business rules have one shared implementation; common components retain clear ownership; regressions pass.

- [ ] [CODE]-[017] Review Feature Security
Task Context
Review authorization lifetime, wrong-tag writes, duplicate POSTs, offline mutation, move confirmation, stale topology, recreation, clipboard/details, NFC injection, and intent spoofing against fail-closed requirements.
Task DOD
Critical/High findings are fixed with adversarial/race tests; authorization cannot survive restore or the wrong scan; evidence is in `docs/security/feature-review.md`.

- [ ] [CODE]-[018] Polish Product Code
Task Context
Format and inspect recomposition scope, stable keys, coroutine lifecycle, StateFlow ownership, reducer purity, resources, visibility, KDoc, and suppressions.
Task DOD
Format, Detekt, Lint, architecture checks, and tests pass; main thread and UI memory rules hold; warnings are triaged; flow documentation matches code.

## Stage 4. Testing, Reliability, and Acceptance

- [ ] [TEST]-[001] Complete Deterministic Common Business Tests
Task Context
Cover URI, topology, resolution, confirmations, move/replacement/already-assigned, retries, verification, pending state, scan races, tag outcomes, sync generations, stale/offline guards, connection replacement, and redaction. Use injected time/dispatchers, virtual time, scripted fakes, and barriers; no sleeps.
Task DOD
Every branching rule has positive/negative tests; races are deterministic; JVM and iOS suites pass; failures name the scenario; simple fakes are preferred.

- [ ] [TEST]-[002] Complete Network Contract and Policy Tests
Task Context
Test exact requests, paths, queries, JSON, credentials, base paths, timeouts, response limits, error taxonomy, incompatible fixtures, redirects, HTTP consent, and TLS scoping with MockEngine and platform policy tests. CI must not require a live server.
Task DOD
All fixture categories/endpoints pass; credentials never reach denied targets; redirect loops/length and body limits are proven; tests run on relevant targets.

- [ ] [TEST]-[003] Complete Room, Sync, and Large-Data Tests
Task Context
Test full replacement/rollback/deletion, SlotKey uniqueness, indexes, search/filter/sort, every released migration, URL clear, corruption recovery, sync joining/concurrency/generations, and a 100-printer/1,000-slot/25,000-spool dataset.
Task DOD
Atomicity and stale preservation are proven; reproducible query/memory thresholds are documented; migrations are committed; presentation never loads the entire dataset; CI is stable.

- [ ] [TEST]-[004] Complete Android NFC and Shell Tests
Task Context
Cover all generated NDEF forms/capabilities, capacity, reconnect, tag-loss phases, fingerprint mismatch, cold start, `onNewIntent`, unrelated URI, Activity count, missing/disabled NFC, API 23, rotation, and recreation. Trace hardware-only gaps to manual tests.
Task DOD
API 23 and modern device suites pass; false NFC success and mutation replay are impossible in tests; manifest assertions pass; manual gaps point to TEST-008.

- [ ] [TEST]-[005] Complete Compose and Accessibility Tests
Task Context
Cover setup, Home states, assignment transient states, Spools, Printers/manual flow, tag mutation, all width classes, themes, large font, semantics, live regions, and focus. Assert interaction and one-confirmation behavior, not screenshots alone.
Task DOD
Critical flows have stable semantic selectors and behavior assertions; all layout/accessibility variants pass; no Critical/High accessibility defect remains; CI runs the suite.

- [ ] [TEST]-[006] Complete iOS and Architecture Tests
Task Context
Compile both iOS targets on macOS, execute Simulator shared tests, construct the mock graph, and render the root. Enforce domain forbidden imports and prohibit product rules/UI in androidApp.
Task DOD
iOS executes real tests; shell smoke passes; a negative fixture proves the architecture gate; Android shell remains thin; CI artifacts contain no secrets.

- [ ] [TEST]-[007] Verify Performance and Resilience
Task Context
Measure cold Processing within one second and verified happy-path assignment within two seconds. Stress 25k spools, body limits, foreground/scan storms, retry races, rotation, and secondary failures without adding telemetry or product limits.
Task DOD
Protocol and results are reproducible; thresholds are non-flaky; StrictMode is clean; wrong-slot success, offline mutation, and unverified tag success remain zero; failures block release.

- [ ] [TEST]-[008] Run Manual and Physical NTAG213 Acceptance
Task Context
On the declared device and at least three NTAG213 tags, run 100 controlled reads requiring at least 99% success within two presentations. Execute the complete write/overwrite/clear/loss/cold/repeat matrix and the PRD manual regression, including connection security, topology, errors, offline, accessibility, and themes.
Task DOD
`docs/qa/release-acceptance.md` names device/build/tags/exact APK and evidence; the read threshold passes; Critical/High defects are zero; no unexecuted hardware case is marked passed.

- [ ] [TEST]-[009] Remove Flaky and Obsolete Tests
Task Context
Remove tests for deleted prototypes, redundant scenarios without added risk, sleeps, probabilistic retries, leaked reports/screenshots, and private fixtures. Preserve required traceability.
Task DOD
Repeated CI is stable; no critical test is ignored/quarantined; artifacts are ignored; fixtures remain minimal and sanitized; runtime/ownership is documented.

- [ ] [TEST]-[010] Deduplicate Fixtures and Test Harnesses
Task Context
Centralize synthetic API builders, clocks, dispatchers, scripted repositories, database generators, and NFC observations in test-only code without hiding scenarios behind an opaque DSL.
Task DOD
Contract shapes and deterministic primitives have one source; tests remain readable; helpers do not enter production artifacts; all target suites pass.

- [ ] [TEST]-[011] Review Test-System Security
Task Context
Inspect CI logs/artifacts, reports, emulator data, screenshots, caches, fixtures, and failures for secrets/private data. Confirm adversarial coverage of redaction, redirects, TLS/HTTP scope, malicious NFC/JSON, wrong tags, stale/offline state, and recreation.
Task DOD
Repository and generated-artifact secret scans pass; Critical/High scenarios are automated; evidence is in `docs/security/test-review.md`; unsafe debug hooks cannot reach release.

- [ ] [TEST]-[012] Polish Tests and QA Documentation
Task Context
Format tests, normalize naming, update requirements traceability, local commands, and manual checklists. Failures should identify the violated invariant.
Task DOD
All clean-checkout CI jobs pass; release acceptance is traceable; no disabled test/TODO lacks issue, reason, and owner; QA docs match the exact release process.

## Stage 5. Security, Supply Chain, and Release

- [ ] [SEC]-[001] Implement Android Keystore Storage and Backup Controls
Task Context
Store the token only through Keystore-backed SecureStorage. Never reveal, copy, persist elsewhere, navigate, save, or log it. Handle key invalidation through re-entry without losing cache/settings. Exclude encrypted token data from Android backup/migration and minimize in-memory lifetime.
Task DOD
Storage/replace/delete/invalidation and backup tests pass; release rules are verified; secret representations are redacted; failed replacement preserves the old token; repository scans are clean.

- [ ] [SEC]-[002] Enforce URL, Redirect, HTTP, and TLS Policies
Task Context
Canonicalize origin plus optional base path and reject userinfo/query/fragment/invalid schemes/hosts. Allow redirects only same-origin or same-host HTTP-to-HTTPS, maximum five; deny downgrade, host/DNS-IP changes, loops, and credential forwarding before validation. Require origin-scoped HTTP consent. Allow certificate-failure-only TLS bypass for exact configured HTTPS hostname, never globally or through redirects; reset by host, retain across port/path, and make reversible.
Task DOD
Enforcement exists in platform clients, not only UI; adversarial tests pass; no global permissive trust manager exists; base paths and consent/reset behavior are exact; warnings clearly explain risk.

- [ ] [SEC]-[003] Implement Ephemeral Diagnostics and Structural Redaction
Task Context
Pass all technical details through one redactor before UI/clipboard. Redact secret headers, known token occurrences, URL userinfo, sensitive queries, and sensitive JSON keys. Permit only bounded sanitized request metadata and summaries. Release logs no bodies/secrets; diagnostics disappear on dismiss, replacement, or death.
Task DOD
Adversarial casing/nesting/encoding tests reveal no secret fragments; only redacted immutable data reaches UI/clipboard; no diagnostic is persisted or saved; clipboard failure is isolated; bounds apply.

- [ ] [SEC]-[004] Harden Fail-Closed Mutation Integrity
Task Context
Audit fresh-online checks, exact topology/generation, one NFC session, fingerprint continuity, authorization, confirmations, exact server/tag verification, and no process replay. Enforce guards below the UI.
Task DOD
Bypassing UI produces typed failures without POST/write; unknown/stale/duplicate/different-tag/unverified states never succeed; adversarial concurrency tests pass; no accepted risk expands without ADR.

- [ ] [SEC]-[005] Lock Dependencies and Add Supply-Chain Gates
Task Context
Enable Gradle dependency verification and compatible locking after graph stabilization. Gate secrets/private fixtures, licenses, and exploitable Critical/High vulnerabilities. Run the full matrix for dependency updates. Pin Actions per repository policy and minimize workflow permissions.
Task DOD
Verification metadata and locks support clean builds; license inventory exists; a controlled fixture proves gates fail; untrusted PRs cannot access secrets; update procedure is documented.

- [ ] [SEC]-[006] Prepare Signed Release and Artifact Verification
Task Context
Keep the permanent key local and outside repository/CI. Document clean checkout, verification, signed APK, signature/manifest/SHA-256 checks, install/smoke, exact-binary physical regression, immutable tag, and GitHub Release. Verify ID/version/SDK/flags and absence of private/test material. Release notes include changes, matrix, limitations, baseline, and checksum.
Task DOD
A dry run is reproducible; APK has the expected certificate, is non-debuggable/installable, and matches the tested checksum; all CI/manual/physical and zero Critical/High gates are mandatory; publication requires explicit owner action.

- [ ] [SEC]-[007] Remove Insecure and Release-Irrelevant Artifacts
Task Context
Remove debug endpoints/buttons, test URLs/tokens, permissive network prototypes, verbose logging, unused permissions, sample data, signing paths, reports, and private traces. Keep safe StrictMode/testing tools only in debug/test variants.
Task DOD
Release inspection finds no debug/test/private material; permissions are minimal; R8 and secret scans pass; debug-only code is unreachable; clean checkout reproduces the artifact.

- [ ] [SEC]-[008] Deduplicate Security Controls
Task Context
Ensure credential injection, redirect validation, URL canonicalization, TLS scoping, redaction, mutation guards, and authorization lifetime each have one authoritative implementation. UI warnings consume policy state but do not enforce it.
Task DOD
No divergent redactor, trust client, URL parser, or guard remains; controls use narrow interfaces; tests target authoritative boundaries; platform code contains only necessary primitives.

- [ ] [SEC]-[009] Run the Final Threat-Model Review
Task Context
Revisit every asset, trust boundary, threat, and risk against the implementation, especially credential forwarding, HTTP/TLS compromises, wrong slot/tag, POST ambiguity, response exhaustion, public-repository leakage, and signing supply chain. Accept residual risk explicitly; Critical/High blocks release.
Task DOD
`docs/security/final-review.md` maps threat to control, evidence, and residual risk; all Critical/High issues are closed; Medium issues have owners/decisions; ADRs and risk register are current; owner sign-off is recorded.

- [ ] [SEC]-[010] Finalize the Repository and Release Candidate
Task Context
From a clean checkout run formatting, Lint, Detekt, architecture, all tests/builds, security, and license checks. Verify README, ADRs, setup/release/QA docs, notes, versions, and links. Any functional change after exact-binary acceptance requires the complete acceptance run again.
Task DOD
Every TECHSPEC release criterion passes; Android debug/release, iOS targets, and tests are green; the exact signed APK passed install, smoke, manual, and physical acceptance; no Critical/High defect or untracked TODO remains; the public repository, checksum, and supported-device data are release-ready.
