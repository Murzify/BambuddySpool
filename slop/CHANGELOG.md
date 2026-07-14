# Agent Documentation Changelog

## 2026-07-14

- Completed `[CODE]-[006]` with one shared, transient combined-assignment confirmation surface. It presents the
  spool, every known current location, target printer and slot, target replacement, and final assign or move-and-
  assign effect together; replacement alone is secondary information and does not create another confirmation.
- Kept the confirmation state out of restoration and operation authorization. Its stable shared focus order exposes
  the title, spool/current locations, target printer, target slot, warning, primary action, and cancel action in
  that order. Deterministic tests cover move conflicts, target replacement, an unassigned target, focus order, and
  the existing assignment preflight's no-POST `AlreadyAssigned` result.
- Completed `[CODE]-[005]` with a common, fail-closed assignment preflight and initial application-scoped POST
  boundary shared by manual and NFC entry. It refreshes spool, printer, status, and assignments, rejects stale,
  offline, unsupported, changed-slot, inconsistent, or unconfirmed context before POST, and never persists/replays a
  command.
- Added immutable pure preflight decisions for Ready, AlreadyAssigned, and one combined-confirmation requirement;
  HTTP success remains fail-closed pending CODE-007 exact verification. Deterministic tests cover cancellation
  ownership, stale/unsupported blocking, confirmation, and idempotency.
- Completed `[CODE]-[004]` with shared Room-projection printer and slot screens. External slots are the only
  selectable manual-assignment targets; AMS slots are visibly read-only and unknown, stale, offline, or unsupported
  topology leaves mutation disabled.
- Added the transient common `AssignmentIntent` boundary used by manual selection and reserved for NFC resolution;
  it carries the validated snapshot generation but is never serialized, restored, or replayed.
- Fixed the compact-width Spools-filter regression: filter controls now flow across rows with 48 dp targets, backed
  by a narrow-width Compose regression test.
- Completed `[CODE]-[003]` with a shared resource-backed Spools browser and details surface, safe restoration of
  query/filter/detail state, Room-projection search integration, and the indexed 100-item lazy page suitable for
  large inventories.
- Added default active/nonarchived/nonempty browsing with explicit inactive, archived, and empty extensions;
  the existing 150ms cancellable Room/FTS search pipeline remains the single search implementation.
- Spool rows/details show independent text metadata, color name, remaining amount, and assignment state without
  relying on color alone. Cached content stays visible during refresh/failure while assignment and tag-link actions
  are disabled unless the projection grants fresh mutation availability.
- Added transient manual-assignment and NFC-tag-link entry intents, plus a precise deleted-spool relink path. Those
  intents deliberately retain no operation authorization, NFC session, or mutation state across recreation.
- Wired the Spools component to a required cache-projection graph boundary, moved the screen into `feature/spools`,
  and added lifecycle cancellation, dedicated detail projections, stable UI semantics, and Android device UI tests.
- Completed `[CODE]-[002]` with a shared Decompose/UDF root for Home, Spools, Printers, and Settings, responsive
  bottom navigation below 600 dp and a navigation rail at wider widths, plus explicit transient root workflow state.
- Added independent, safely restorable Decompose child histories for every primary destination. Credentials,
  authorization, NFC sessions, transient workflows, and mutation state are intentionally excluded from saved state.
- Added the minimal resource-backed Home screen with configured/online/offline/stale connection messaging and NFC
  available/unavailable/disabled states, along with deterministic restoration and reducer tests on Android host and
  iOS Simulator targets.
- Added ADR-011 for durable fail-closed connection replacement and recovery across settings, credentials, cache,
  and initial-sync scheduling.
- Removed the unsafe compensating multi-store replacement path. `ConnectionReplacementService` now invokes only an
  injected ADR-011 transaction that includes initial-sync scheduling, and deterministic tests prove scheduling
  failure and cancellation cannot be surfaced as a successful save.
- Completed `[CODE]-[001]` with one resource-backed shared URL/token/Test/Save form for Setup and Settings. The
  token is kept only in unsaved Compose memory and is converted directly to the non-printing secret boundary;
  reducer, navigation, and saved state contain only safe form metadata.
- Added validation-only connection testing, reducer coverage for required inputs and sequential safety warnings, and
  critical instance-change/HTTP confirmations. Connection replacement now validates reachability and authentication
  before showing the instance-change warning, then retains the existing atomic cache/default/security reset and
  initial-sync path.
- Completed `[DATA]-[014]` by documenting the public network, synchronization, client-lifecycle, and
  connection-replacement contracts; narrowing the Room snapshot adapter to internal visibility; and preserving
  coroutine cancellation through the Ktor request boundary with deterministic MockEngine coverage.
- Confirmed the existing dispatcher, Room transaction, schema, sanitized contract, synchronization, and pinned
  toolchain-workaround boundaries remain aligned with their authoritative documentation and checked the common,
  Room, network, static, architecture, and iOS matrix without private-instance access.
- Completed `[DATA]-[013]` with a data-layer security review and evidence covering storage classification, SQL
  constraints, synthetic fixtures, malformed and oversized responses, secret handling, URL metadata, generation
  races, numeric overflow, and malicious NFC input.
- Added early oversized `Content-Length` rejection, a fail-closed NFC URI length limit, and bounded strict
  canonical URL metadata validation with adversarial tests.

## 2026-07-13

- Completed `[DATA]-[012]` by centralizing raw database slot-coordinate conversion in `SlotKey`, snapshot-generation advancement, and the complete Room snapshot publication write-set. Retained distinct canonical parsers for Bambuddy base URLs and NFC payloads because their grammar and typed failures are intentionally different.

- Completed `[DATA]-[011]` by removing unused Room DAO surfaces, assignment projections and query variants, transport fields that never reached domain behavior, redundant normalized spool copies, and unused persistence timestamps/indexes.
- Preserved the version 1 baseline schema export unchanged, introduced a minimized version 2 schema, and added an explicit Room KMP `1 -> 2` migration registry that copies retained snapshot data before rebuilding tables and FTS.
- Completed `[DATA]-[010]` with common cache projection contracts for `InitialLoading`, `Content`, `ContentRefreshing`, and `FatalErrorWithoutCache`, plus stale state, nonblocking refresh errors, and typed mutation availability.
- Added a Room-backed projection adapter over DAO flows so feature code can observe printer, slot, spool detail, and lazy limit-offset spool page/search projections without importing Room, entities, DTOs, or raw responses.
- Added cancellable/debounced database-backed spool search and deterministic common tests proving stale cached content remains visible, mutations are disabled with reasons, no-cache failures are fatal, and superseded search flows are cancelled.
- Added default-printer lifecycle rules and tests for single-printer auto-selection, multiple-printer deferral, and deleted/default-missing transitions.
- Completed `[DATA]-[009]` with a fail-closed common slot topology resolver, centralized A1 mapping, multiple-known-slot representation, label precedence, AMS read-only slots, and typed unsupported-topology mutation blocking.
- Updated atomic snapshot sync to use the resolver and reject unsupported topology atomically instead of persisting partial slot rows.
- Completed `[DATA]-[008]` with an atomic snapshot synchronizer that fetches printers, bounded-concurrency status/topology, archived-inclusive spools, and assignments before publishing through a generation-guarded `SnapshotStore`.
- Added a Room transaction adapter for complete snapshot rebuilds, generation cleanup, sync metadata advancement, and stale-generation rejection so older refreshes cannot overwrite newer post-mutation state.
- Added deterministic common tests for rollback/stale preservation, partial response rejection, trigger joining, foreground debounce, status concurrency, deletion by replacement, generation races, and archived-inclusive spool fetches.
- Completed `[DATA]-[007]` with bounded Ktor repositories for the mandatory Bambuddy operations, typed network errors, URL-builder endpoint construction with base-path support, `X-API-Key` credential injection, and response-size enforcement before parsing.
- Added Android OkHttp and iOS Darwin engine factories with disabled automatic redirects and 3-second connect / 10-second request timeouts, plus security-policy hooks for later redirect/TLS enforcement.
- Added MockEngine coverage for every mandatory operation, credential redaction, oversized responses, base paths, and distinct 4xx, 5xx, transport, contract, and TLS/security-policy failures.
- Completed `[DATA]-[006]` with common settings and credential contracts for canonical Bambuddy base URLs, configured origin metadata, default printer, HTTP consent origin, TLS override hostname, settings schema version, and explicit nonsecret DataStore field names.
- Added a non-printing `SecretValue`, a secure token-store boundary, and validation-first connection/token replacement orchestration with tests for HTTP/TLS consent scoping and failure preservation.
- Completed `[DATA]-[005]` with Room KMP entities, DAO query surfaces, persistence/domain projections, SlotKey uniqueness constraints, generation-based cleanup methods, and URL-change cache clearing surfaces for the Bambuddy snapshot cache.
- Added an indexed spool browsing contract using default active/nonarchived/nonempty filters, explicit default index usage, stable recently-used/name/ID sorting, and an FTS4 search table over spool name, manufacturer, material, and color name.
- Documented the version 1 baseline schema in `slop/database/schema-v1.sql` while retaining the current no-Room-compiler setup and avoiding speculative KSP/toolchain changes.
- Completed `[DATA]-[004]` with configured Bambuddy network JSON policy, internal Serialization DTOs, strict DTO-to-domain mapping, assignment request encoding/validation, and `IncompatibleApiResponse` failures for malformed or invalid required fields.
- Added common mapping tests for assignment JSON, configured and pending assignment variants, unknown additive fields, and invalid request/response fields; added fixture-backed host mapping tests over the sanitized Bambuddy 0.2.4.7 contract set.
- Completed `[DATA]-[003]` with a common canonical NFC payload codec for `bambuddy-spool://spool/<positive-decimal-id>`, strict parse failures, leading-zero read canonicalization, common NDEF-shaped read classification, and exhaustive common tests.
- Completed `[DATA]-[002]` with strict common domain models for typed printer/spool IDs, bounded slot identity, inventory entities, generation-bound assignment commands, assignment results, NFC tag mutation outcomes, and typed domain failures.
- Added focused common domain tests for valid/invalid identifiers, exact `SlotKey` equality, generation validation, success/failure taxonomy, and tag mutation result states; verified shared tests, Detekt, formatting, and iOS compilation.
- Completed `[DATA]-[001]` with a machine-readable manifest for the eight mandatory Bambuddy 0.2.4.7 operations and synthetic minimal-valid, representative-valid, and incompatible fixtures for every endpoint form.
- Recorded request parameters, authentication, used wire fields with types/nullability, response limits, JSON policy, provenance, and explicit unknown limits without retaining the private OpenAPI export or live data.
- Added host contract tests and repository policy checks for endpoint/category completeness, JSON validity, additive fields, synthetic provenance, incompatible evidence, and exclusion of private connection, credential, device, and tag material.
- Completed `[INIT]-[009]` by narrowing foundation implementation visibility, documenting the remaining public application and platform contracts, and replacing the template README with accurate clean-checkout, architecture, security, and CI guidance.
- Aligned CI with the repository's Microsoft JDK 17 daemon requirement, constrained Spotless to production source roots and Gradle scripts, and retained the Compose Android resource wiring required by device-test packaging.
- Triaged Stage 1 warnings and suppressions, refreshed resolved toolchain notes, and verified the complete Android, iOS, Xcode, quality, architecture, repository-policy, and configuration-cache matrix.
- Completed `[INIT]-[008]` with an adversarial foundation review covering the tracked tree, reachable history, Android manifests, CI, caches, dependencies, logging, signing, and private configuration boundaries.
- Closed the High backup/migration finding with explicit legacy, cloud, and device-transfer exclusions; made cleartext deny-by-default; restricted CI cache writes to trusted pushes; and expanded strong secret/signing signatures.
- Recorded threat-to-control evidence and assigned remaining implementation and release debt to the corresponding security backlog tasks without claiming unimplemented product controls.
- Completed `[INIT]-[007]` with shared Gradle/Xcode product metadata, catalog-owned JVM and ktlint versions, package-derived Android/shared/framework identifiers, and root-level Detekt defaults.
- Enforced settings-owned dependency repositories, retained the two required Gradle repository scopes, and confirmed configuration-cache storage and reuse without adding a build-logic module.
- Verified aligned Android debug/release, iOS framework/Simulator, and Xcode shell metadata while preserving the `shared`, `androidApp`, and `iosApp` topology.
- Completed `[INIT]-[006]` by removing generated greeting/platform samples, arithmetic placeholder tests, Compose template artwork, launcher icons, preview assets, stale resource settings, and unused dependency aliases.
- Replaced the common placeholder assertion with a root-navigation invariant, retained the dependency toolchain smoke coverage and required Ktor engines, and kept the Android device test as a supported-device/library-load smoke boundary.
- Expanded `.gitignore` coverage for local configuration, build output, IDE metadata, secrets, signing material, and Xcode user state while preserving required Gradle wrapper and iOS project files.
- Completed `[INIT]-[005]` with nine blocking GitHub Actions jobs covering wrapper/config validation, common tests, Android builds and host tests, iOS compilation/tests, formatting, Detekt, Android Lint, architecture checks, and baseline dependency/security inspection.
- Added JDK 17 setup, immutable action revisions, pull-request-only cancellation, safe Gradle caching, and repository checks that reject tracked local credentials, signing files, private keys, dynamic dependency versions, and mutable third-party action references.
- Marked device execution, Compose UI harness coverage, and authoritative license/CVE auditing as explicit CI limitations so they cannot be mistaken for completed release gates.
- Completed `[INIT]-[004]` with the prescribed `app`, `core`, and `feature` package boundaries, a Decompose root, an immutable UDF contract, and Metro application/component graphs.
- Added narrow secure storage, NFC, settings, clipboard, haptics, dispatchers, and networking interfaces with explicit compile-time mock bindings for both platform shells.
- Added Android host architecture gates for platform imports, domain framework imports, cross-feature imports, Android shell domain imports, and large `expect` services.

## 2026-07-12

- Completed `[INIT]-[003]` with the ADR-001 `shared` / `androidApp` / `iosApp` topology, thin platform shells, Android host/device test boundaries, and aligned iOS deployment targets.
- Verified Android debug/release and device-test APKs, physical device smoke on HONOR 50, both iOS targets, iOS Simulator tests, and the Xcode shell build.
- Completed `[INIT]-[002]` with a pinned JDK 17, Gradle, AGP, Kotlin, Compose, Room, Decompose, Ktor, Metro, Serialization, and DataStore compatibility matrix.
- Lowered the Android minimum SDK declaration from 26 to the required API 23 and aligned JVM bytecode with JDK 17.
- Added a cross-target dependency smoke test and verified Android debug, Android host tests, iOS device compilation, iOS Simulator tests, and the iOS shell build.
- Resolved `[INIT]-[001]` product and release parameters.
- Declared HONOR 50 as the physical Android/NFC acceptance device and recorded the API 36 emulator as non-physical test coverage.
- Retained Android application ID `com.murzify.bambuddyspool`.
- Confirmed GitHub repository `Murzify/BambuddySpool` and MPL-2.0 licensing.
- Scoped v1 topology support to the confirmed Bambu Lab A1 external slot (`255/0`) and recorded multi-slot evidence as a blocker only for multi-slot support claims.
