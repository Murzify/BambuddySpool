# TECHSPEC — Bambuddy NFC Spool Manager

**Status:** Approved technical baseline with explicit Open Questions  
**Document version:** 1.0  
**Product:** Bambuddy NFC Spool Manager  
**Platforms:** Android v1; Kotlin Multiplatform baseline for future iOS  
**Reference Bambuddy contract:** OpenAPI 0.2.4.7  
**Primary quality principles:** Ultimate Simplicity, High Performance, Fail Closed

---

## 1. Purpose

This document is the technical source of truth for implementation and backlog decomposition of Bambuddy NFC Spool Manager.

It defines:

- the system boundaries and supported workflows;
- architecture and module structure;
- domain, persistence, networking and NFC contracts;
- assignment orchestration and verification;
- security controls and accepted compromises;
- UI, accessibility and adaptive-layout rules;
- performance and reliability targets;
- test strategy and release gates;
- ADRs, risks, Open Questions and implementation epics.

The team must not weaken the product invariants in this document without an explicit ADR update.

---

## 2. Product and technical scope

### 2.1 Product objective

Assign an existing Bambuddy spool to a physical external printer slot through one NFC scan and no more than one user confirmation.

The standard unambiguous flow must be:

```text
Install spool
→ Scan NFC tag
→ Resolve spool, printer and external slot
→ POST assignment
→ Verify exact assignment
→ Show success
```

### 2.2 Included in v1

- one configured Bambuddy instance;
- manual URL and API token setup;
- NFC URI reading;
- NFC tag write, overwrite, clear and independent read-back verification;
- external-slot assignment;
- multi-external-slot selection UI;
- printer and current assignment viewing;
- spool browsing, filtering and search;
- manual assignment using the same orchestration as NFC;
- offline cached viewing;
- Android API 23+;
- shared Compose Multiplatform UI and common business logic;
- compilable iOS targets and a minimal iOS shell with mock platform services;
- public GitHub source repository and manually published signed APK.

### 2.3 Explicitly excluded

- creating or editing Bambuddy spools;
- editing remaining filament or printer configuration;
- multiple Bambuddy instances;
- offline/deferred mutation queue;
- background assignment processing;
- AMS assignment mutation;
- analytics, tracking, crash reporting or persistent technical logs;
- automatic update checking;
- full iOS functionality;
- irreversible NFC tag locking;
- a complete Bambuddy mobile client.

---

## 3. Non-negotiable invariants

1. Exactly one Bambuddy instance is configured.
2. Bambuddy is the sole authority for printers, spools, slot state and assignments.
3. Offline mutations are prohibited; no queue or deferred replay exists.
4. The normal NFC flow uses one scan and at most one confirmation.
5. Assignment success requires exact server verification of printer, slot and spool.
6. Unsupported or damaged NFC payloads are never partially recovered.
7. Moving a spool from another location is never hidden.
8. Tags remain rewritable; irreversible locking is unavailable architecturally.
9. Product UI, navigation, presentation state and business logic remain in shared Kotlin code.
10. Android framework types never enter common domain or presentation models.
11. No analytics, remote crash reporting or persistent diagnostic logging.
12. Critical and High defects block release.
13. Unknown external-slot topology fails closed; heuristic assignment is forbidden.
14. A successful HTTP response alone is never treated as a successful assignment or NFC write.

---

## 4. Reference API contract

### 4.1 Baseline

The implementation is based on the factual OpenAPI contract of Bambuddy **0.2.4.7**.

The application does not perform a runtime version check. Compatibility is determined by the actual shape and behavior of the required operations.

The private full OpenAPI export from the owner’s instance must not be committed to the public repository. The repository may contain only:

- a small manually maintained contract manifest;
- minimal synthetic JSON fixtures;
- sanitized shapes derived from real responses;
- the reference version number `0.2.4.7`.

### 4.2 Mandatory API subset

```text
GET  /api/v1/auth/me
GET  /api/v1/printers/
GET  /api/v1/printers/{printer_id}/status
GET  /api/v1/inventory/spools?include_archived=true
GET  /api/v1/inventory/spools/{spool_id}        # targeted validation when required
GET  /api/v1/inventory/assignments
GET  /api/v1/inventory/assignments?printer_id=<id>
POST /api/v1/inventory/assignments
```

The application must not issue a separate unassign before assignment.

### 4.3 Authentication

The client uses:

```text
X-API-Key: <token>
```

The Bambuddy API may also support Bearer authentication, but the application standardizes on `X-API-Key` to reduce ambiguity and simplify mandatory redaction.

### 4.4 JSON policy

Kotlinx Serialization baseline:

```text
ignoreUnknownKeys = true
explicitNulls = false
isLenient = false
coerceInputValues = false
```

Rules:

- unknown additive fields are ignored;
- mandatory used fields must exist and have valid types;
- missing data required for identification, mutation or verification produces `IncompatibleApiResponse`;
- transport DTOs may model documented nullability;
- domain models must be stricter than transport DTOs;
- raw OpenAPI DTOs are not exposed to the domain or UI.

### 4.5 Assignment request and response

Logical assignment request:

```json
{
  "printer_id": 1,
  "ams_id": 255,
  "tray_id": 0,
  "spool_id": 3
}
```

The response contains assignment coordinates and configuration state, including:

```text
configured: Boolean
pending_config: Boolean
```

Interpretation:

- exact assignment verification is the success boundary;
- `pending_config=true` produces a success result with a secondary pending-configuration note;
- `configured=true` indicates printer configuration was applied;
- `configured=false` and `pending_config=false` does not invalidate a verified inventory assignment.

---

## 5. Module and source-set architecture

### 5.1 AGP 9 module topology

AGP 9 requires the Android application plugin and Kotlin Multiplatform plugin to be separated.

```text
root
├── shared
│   ├── commonMain
│   ├── commonTest
│   ├── androidMain
│   ├── androidHostTest
│   ├── androidDeviceTest
│   ├── iosMain
│   └── iosTest
├── androidApp
│   ├── src/main
│   ├── src/debug
│   ├── src/release
│   ├── src/test
│   └── src/androidTest
└── iosApp
    └── Xcode shell
```

### 5.2 `shared`

Plugins:

```text
org.jetbrains.kotlin.multiplatform
com.android.kotlin.multiplatform.library
org.jetbrains.compose
org.jetbrains.kotlin.plugin.compose
```

Responsibilities:

- all product UI;
- Decompose navigation and components;
- UDF state and reducers;
- domain and application orchestration;
- Ktor networking contracts and implementations;
- Room KMP persistence;
- platform interfaces;
- Android platform adapters suitable for an Android KMP library;
- iOS mock platform bindings.

### 5.3 `androidApp`

Plugin:

```text
com.android.application
```

Responsibilities only:

- Android manifest;
- Application and launcher Activity;
- NFC intent filters and `singleTop` launch mode;
- `onNewIntent` routing;
- app icon and Android-only resources;
- debug/release build types;
- signing and R8 configuration;
- thin wiring into `shared`.

Product screens and business rules in `androidApp` are prohibited.

### 5.4 `iosApp`

Responsibilities:

- create shared root component;
- render shared Compose UI;
- provide explicit mock platform services;
- support launch and basic navigation;
- contain no separate product screens.

### 5.5 Package architecture

```text
app/
  bootstrap/
  root/
  navigation/

core/
  domain/
  application/
  network/
  database/
  sync/
  security/
  nfc/
  platform/
  testing/

feature/
  setup/
  home/
  spools/
  printers/
  assignment/
  tagmutation/
  settings/
```

Feature separation is enforced through package boundaries, visibility and architecture tests, not additional Gradle modules.

### 5.6 Dependency direction

```text
Compose UI
→ feature component/presentation
→ application use cases/orchestrators
→ domain interfaces and models
← infrastructure implementations
← Ktor / Room / DataStore / SecureStorage / NFC adapters
```

The domain layer must not depend on Compose, Decompose, Metro, Ktor, Room, DataStore, Android, JVM or iOS APIs.

---

## 6. Dependency injection and platform boundaries

### 6.1 Metro

Metro provides a compile-time dependency graph.

Rules:

- constructor injection is the default;
- interface bindings are explicit;
- service locator access is forbidden;
- runtime string keys for core services are forbidden;
- runtime navigation arguments are passed through component factories, not global DI.

### 6.2 Scopes

Use the minimum practical scopes:

```text
ApplicationScope
ComponentScope
OperationScope only when a workflow requires it
```

Application-scoped services include:

- Room database;
- Ktor clients;
- SecureStorage;
- ConnectionRepository;
- SnapshotSynchronizer;
- AssignmentOrchestrator;
- NfcOperationCoordinator;
- DiagnosticRedactor;
- Clock and dispatchers.

### 6.3 Platform interfaces

Narrow interfaces include:

```text
SecureStorage
NfcService / NFC session primitives
PlatformSettingsNavigator
ClipboardService
HapticsService
AppDispatchers
Platform networking configuration
```

Large `expect/actual` services are prohibited. `expect/actual` is reserved for factories and lightweight primitives where injection provides no practical value.

### 6.4 Activity boundary

The Android Activity may:

- create or obtain the root graph/component;
- receive the initial NFC intent;
- receive subsequent NFC intents through `onNewIntent`;
- convert Android framework events through an adapter;
- render shared Compose UI;
- connect lifecycle and window information.

It must not make domain decisions.

---

## 7. Domain model

### 7.1 IDs

Server database IDs use `Long` wrappers:

```kotlin
@JvmInline value class PrinterId(val value: Long)
@JvmInline value class SpoolId(val value: Long)
```

`amsId` and `trayId` remain bounded `Int` coordinates.

### 7.2 Slot identity

```kotlin
data class SlotKey(
    val printerId: PrinterId,
    val amsId: Int,
    val trayId: Int,
)
```

`SlotKey` is the immutable verification identity. Display labels do not participate in assignment verification.

### 7.3 Assignment command

```kotlin
data class AssignmentCommand(
    val spoolId: SpoolId,
    val slot: SlotKey,
    val source: AssignmentSource,
    val expectedSnapshotGeneration: Long,
)
```

The command is immutable across retries.

### 7.4 Typed results

Assignment terminal success variants:

```text
AssignedAndConfigured
AssignedConfigurationPending
AssignedInventoryOnly
AlreadyAssigned
```

Tag mutation results must distinguish:

```text
Success
NotApplied
AppliedButUnverified
OutcomeUnknown
```

Raw exceptions are not domain or presentation models.

---

## 8. Persistence and synchronization

### 8.1 Storage responsibilities

**Room:**

```text
printers
printer_slots
spools
assignments
sync_metadata
```

**DataStore:**

```text
canonical base URL
configured origin metadata
default printer ID
HTTP warning acknowledgement
TLS override hostname
settings/schema version
```

**SecureStorage:**

```text
API token only
```

Never persist:

- printer access codes;
- raw HTTP requests/responses;
- diagnostics;
- the private OpenAPI export;
- API token outside SecureStorage.

### 8.2 UI data source

UI observes Room, never network responses directly.

```text
Network
→ transport validation
→ domain mapping
→ Room transaction
→ reactive query
→ presentation state
```

### 8.3 Full snapshot

Mandatory datasets:

```text
printers
printer status / slot topology for each printer
spools including archived
assignments
```

A full snapshot is committed only when every mandatory dataset succeeds and validates.

One Room transaction must:

1. upsert printers;
2. replace current slot topology;
3. upsert spools;
4. replace assignments;
5. delete entities absent from the complete server snapshot;
6. update `lastSuccessfulSyncAt`;
7. increment `snapshotGeneration`.

Partial snapshot publication is prohibited.

### 8.4 Refresh triggers

Full refresh:

- after initial successful setup;
- application cold launch;
- foreground return;
- manual Retry/Refresh;
- confirmed connection replacement;
- after successful mutation.

Foreground debounce baseline: 2 seconds.

No periodic background sync exists.

### 8.5 Concurrency

- only one full sync job exists per process;
- new full-sync triggers join the current job;
- printer-status fan-out uses bounded concurrency, baseline 4;
- targeted assignment validation may run separately;
- sync results started before a newer mutation generation must not overwrite post-mutation state.

### 8.6 Stale state

Stale is based on failed/current validation, not a fixed TTL.

Cached content remains visible while:

- refresh is running;
- foreground refresh failed;
- Bambuddy is unreachable;
- authentication is invalid.

Mutation actions are disabled whenever fresh server context cannot be established.

### 8.7 URL replacement

Connection replacement is atomic:

1. syntactically validate new URL;
2. validate reachability and token without mutating active configuration;
3. show critical instance-change warning;
4. save new connection metadata and credentials;
5. clear Room domain snapshot;
6. reset default printer;
7. reset origin/host-scoped security acknowledgements when applicable;
8. run initial sync.

A failed validation leaves the previous connection active.

### 8.8 Token replacement

The replacement token is validated first. On failure, the old token and snapshot remain unchanged.

---

## 9. Printer and slot topology

### 9.1 Source of physical slots

`PrinterStatus.vt_tray` is the primary source of external physical slots.

Assignments provide:

- current assignment state;
- evidence for known coordinate mappings;
- post-mutation verification.

Absence of an assignment does not mean absence of a physical slot.

### 9.2 Known mapping only

A shared `SlotTopologyResolver` resolves printer status and assignments into domain slots.

```kotlin
interface SlotTopologyResolver {
    fun resolve(
        printer: Printer,
        status: PrinterStatus,
        assignments: List<Assignment>,
    ): SlotTopologyResolution
}
```

Unknown or partial mapping returns `Unsupported` and blocks mutation.

Forbidden heuristics:

- choose the first slot;
- derive assignment coordinates from list index;
- assume `255/0` for every printer;
- hide unresolved slots and expose an incomplete selectable list.

### 9.3 Confirmed A1 mapping

For the reference Bambu Lab A1 external slot:

```text
ams_id = 255
tray_id = 0
```

These values must remain isolated inside topology mapping rules rather than duplicated through feature code.

### 9.4 Multiple external slots

The common UI and domain model support multiple external slots.

- one resolved slot: auto-select;
- multiple resolved slots: user chooses in the single combined confirmation surface;
- unknown multi-slot topology: fail closed.

### 9.5 Slot labels

Display-label precedence:

1. API-provided label;
2. known physical topology name, such as `External`, `External Left`, `External Right`;
3. fallback `External slot 1`, `External slot 2`;
4. raw `ams_id/tray_id` only in technical details.

### 9.6 Freshness

Cached topology may be displayed, but before mutation the relevant printer status must be refreshed.

If the selected `SlotKey` disappears or changes before POST, the POST is not sent and the user is returned to the current slot selection.

---

## 10. Assignment orchestration

### 10.1 Shared orchestrator

NFC and manual assignment use the same orchestrator and repository contracts.

```kotlin
interface AssignmentOrchestrator {
    suspend fun execute(request: AssignmentIntent): AssignmentResult
}
```

### 10.2 State machine

```text
Idle
ReadingTag
ResolvingSpool
RefreshingContext
AwaitingCombinedConfirmation
ReadyToAssign
Assigning
RetryWaiting
Verifying
Success
Error
```

### 10.3 Zero-confirmation conditions

Automatic POST is permitted only when all are true:

- spool exists;
- exactly one printer exists;
- exactly one external slot is resolved;
- spool is not assigned elsewhere;
- topology and assignments are fresh;
- connection is valid;
- no unsupported mapping or inconsistent server state exists.

### 10.4 Single combined confirmation

When ambiguity or a move conflict exists, printer selection, slot selection and move warning must be combined into one confirmation surface.

The user must not receive sequential printer, slot and move confirmations.

Confirmation summary includes:

```text
spool
current assignment(s), if any
target printer
target external slot
target replacement, if any
effect: assign / move and assign
```

### 10.5 Already assigned

After fresh assignment retrieval, if the exact target `SlotKey` already contains the scanned spool:

- no POST is sent;
- the server state itself satisfies verification;
- an `Already assigned` success variant is shown.

### 10.6 Spool assigned elsewhere

Any assignment with the same `spoolId` and a different `SlotKey` requires explicit move confirmation.

If multiple conflicting locations exist, all known locations are shown.

### 10.7 Target replacement

A different spool already in the target slot does not require an additional confirmation. It may be displayed as secondary information in the combined confirmation.

### 10.8 POST retry

Retryable:

```text
connect timeout
request timeout
connection reset
temporary DNS/network failure
HTTP 5xx
```

Not automatically retryable:

```text
HTTP 4xx
serialization/contract failure
unsupported topology
TLS validation failure
invalid local command
```

Schedule:

```text
Attempt 1: immediately
Retry 1: 500 ms
Retry 2: 1 s
Retry 3: 2 s
```

Maximum POST calls: 4.

Each retry sends the same logical payload. No verification GET occurs before an automatic POST retry.

### 10.9 Verification

After POST success:

```text
GET assignments?printer_id=<target printer>
```

Exact success requires one matching assignment:

```text
printer_id
ams_id
tray_id
spool_id
```

Verification polling:

```text
immediately
+200 ms
+500 ms
```

Verification failure never automatically resends POST.

Multiple assignments for the same `SlotKey` are an inconsistent server state and must not produce success.

### 10.10 User Retry

The Error-screen Retry starts a new orchestration cycle:

1. refresh current printer status;
2. refresh assignments;
3. validate spool;
4. resolve topology again;
5. detect already-assigned or changed conflicts;
6. send a new POST only if still required.

### 10.11 Cancellation and process death

Before POST, workflow cancellation is cooperative.

After POST begins, the operation remains application-scoped through at least the verification attempt, unless the OS kills the process.

No mutation is persisted or replayed after process death.

---

## 11. NFC data and Android entry

### 11.1 Canonical payload

```text
bambuddy-spool://spool/<positive-decimal-id>
```

Parser rules:

- scheme comparison case-insensitive;
- host exactly `spool`;
- exactly one path segment for ID;
- no query or fragment;
- positive decimal integer;
- no sign, whitespace or non-decimal digits;
- overflow invalid;
- leading zero accepted on read but removed by canonical encoder.

### 11.2 NDEF record

Write exactly one NDEF URI record.

Do not add:

- Android Application Record;
- MIME metadata;
- server URL;
- instance identifier;
- checksum;
- schema version.

### 11.3 NFC entry

`androidApp` must:

- register only the canonical Bambuddy NFC URI intent filter needed for direct assignment entry;
- use `singleTop`;
- route initial and `onNewIntent` scans to the shared workflow;
- avoid a new Activity instance per scan.

Android `Intent`, `Tag` and `NdefMessage` never enter commonMain.

### 11.4 Cold NFC launch

Before full bootstrap completes:

1. create minimal root graph;
2. render processing shell;
3. read and route the NFC event;
4. load connection metadata;
5. start targeted workflow;
6. initialize secondary features asynchronously.

### 11.5 Duplicate scans

A platform observation contains a fingerprint and monotonic timestamp.

Duplicate suppression applies when the same fingerprint is received within 1 second and the prior event was already accepted.

Fingerprint precedence:

1. tag UID when available;
2. normalized NDEF payload hash.

### 11.6 Active workflow scan policy

- before POST: a new physical scan replaces the current workflow;
- after POST: current mutation/verification completes and the newest scan becomes the sole pending scan;
- on Success: a new scan immediately replaces Success with Processing;
- queue capacity: one active workflow plus one latest pending scan.

---

## 12. NFC read and tag mutation

### 12.1 Read classification

```text
Empty
ValidSpoolPayload
UnknownPayload
MalformedNdef
UnsupportedTag
ReadFailure
```

A valid spool tag contains exactly one supported URI record.

Multiple records are unsupported even if one record is valid.

### 12.2 Empty semantics

Empty means:

- no NDEF records; or
- a normalized empty NDEF message.

An empty text record, malformed URI, unknown MIME record or multiple records is not empty.

### 12.3 Platform capability

Supported write capabilities:

```text
Writable Ndef
NdefFormatable
```

Read-only NDEF and unsupported technologies may be read/classified but not mutated.

### 12.4 Link and overwrite

For an empty tag:

```text
select spool
→ hold tag
→ write
→ reread
→ verify
→ success
```

Any nonempty different/unknown payload requires explicit overwrite confirmation.

If the same spool URI is already present, the UI shows `Already linked`; rewriting is not automatic.

### 12.5 Preconditions

Before write/overwrite:

- selected spool is validated against fresh server state;
- NFC is enabled;
- canonical payload is available;
- operation authorization is current;
- server context is online and valid.

All tag mutations, including clear, are blocked offline for consistent product behavior.

### 12.6 Capacity

The Android adapter checks NDEF max size before writing. Payload truncation is forbidden.

### 12.7 Write transaction

```text
connect
→ inspect capability
→ write
→ reopen/reconnect as required
→ independent reread
→ canonical verification
→ success
```

The platform write result alone is insufficient.

### 12.8 Canonical verification

Verification compares the decoded and canonicalized application URI, not raw NDEF byte encoding. URI Identifier Code compression may differ while representing the same URI.

Requirements:

- exactly one URI record;
- successful decode;
- valid common payload;
- canonical URI equals expected canonical URI.

### 12.9 Automatic write retry

Physical NFC writes are not automatically repeated.

On an uncertain result:

1. attempt reread if possible;
2. if expected payload is present, report success;
3. otherwise report an unverified/unknown outcome;
4. user Retry begins with read-before-write on a newly presented tag.

### 12.10 Tag lost classification

```text
before write call          → TagRemoved / NotApplied
after write, before verify → OutcomeUnknown
while verifying            → AppliedButUnverified or VerificationInterrupted
```

### 12.11 Clear

Clear always requires explicit confirmation.

Postcondition:

```text
NDEF readable
record count normalized to 0
tag remains writable
```

If the platform requires a minimal empty representation, the adapter may use it only when common read normalization returns `Empty` and no user payload remains.

Clear never changes Bambuddy spool data.

### 12.12 Locking

No interface or implementation may expose Android `makeReadOnly` or equivalent irreversible functionality.

### 12.13 Session serialization

Only one physical NFC session may be active.

Write/clear sessions lock to the expected tag fingerprint. A different tag is rejected as `Different tag detected` and is never overwritten under the prior authorization.

---

## 13. Network security

### 13.1 URL model

Base URL is canonicalized as origin plus optional reverse-proxy base path.

Allowed:

```text
HTTP
HTTPS
IP addresses
local/public hostnames
custom ports
IPv6 literals
optional base path
```

Rejected:

```text
userinfo credentials
query
fragment
unsupported scheme
missing hostname
```

Endpoint construction uses a URL builder, never raw string concatenation.

### 13.2 Origin

Origin is:

```text
scheme + canonical host + effective port
```

Used for:

- redirect policy;
- credential forwarding;
- HTTP acknowledgement;
- connection-change comparison.

TLS override is hostname-scoped as required by the product decision.

### 13.3 Redirect policy

Automatic unrestricted redirect following is disabled.

Allowed:

- same-origin redirect;
- HTTP to HTTPS upgrade with the same hostname, regardless of port.

Denied:

- HTTPS to HTTP;
- hostname change;
- DNS name to IP or IP to DNS name;
- chains longer than 5;
- redirect loops.

`X-API-Key` is attached to the next request only after the redirect target passes policy.

### 13.4 HTTP

HTTP is allowed for any user-configured host.

First save for an HTTP origin requires explicit consent warning that the API token and data are transmitted without transport encryption.

Acknowledgement is scoped to canonical origin. Host or port changes require new consent.

### 13.5 TLS default and override

Default behavior uses platform trust and hostname verification.

On a classified certificate-validation failure only, the user may enable certificate verification bypass for the configured hostname.

Rules:

- no global trust-all client;
- override applies only to HTTPS and exact configured hostname;
- subdomains do not inherit;
- redirects to other hosts do not inherit;
- hostname change resets override;
- port/path changes do not reset a hostname-scoped override;
- no automatic HTTPS-to-HTTP fallback;
- no persistent insecure banner after consent;
- the setting remains visible and reversible in Settings.

### 13.6 Timeouts

```text
connect timeout: 3 seconds
total timeout per request: 10 seconds
```

### 13.7 Defensive response limits

Baseline decompressed limits:

```text
auth/me:             1 MiB
printer status:      4 MiB
assignments:        16 MiB
spools snapshot:    64 MiB
generic error body: 256 KiB
```

These are implementation-safety limits, not product-level entity limits.

### 13.8 Credential handling

The token:

- is stored only in Keystore-backed SecureStorage on Android;
- is not placed in Room, DataStore, navigation state or UI state;
- is not revealed or copied after saving;
- is loaded only through the network credential provider;
- is represented by a non-printing `SecretValue` wrapper where practical;
- may exist in process memory only while required for requests.

Android backup/restore must not migrate the encrypted token to another device.

### 13.9 Diagnostics and redaction

All technical details are centrally redacted before entering presentation or clipboard state.

Mandatory targets:

```text
X-API-Key
Authorization
Proxy-Authorization
Cookie / Set-Cookie
custom secret headers
known token value anywhere in text
URL userinfo
sensitive query parameters
JSON keys such as token, secret, password, access_code, client_secret
```

Error diagnostics may include:

```text
method and sanitized path
canonical origin
safe headers
redacted bounded body
HTTP status
request duration
attempt number
redirect count
safe TLS summary
```

Release builds must not log request/response bodies or secret-bearing headers.

Diagnostics are discarded when the Error screen is dismissed, replaced or the process dies.

---

## 14. UI architecture and navigation

### 14.1 Root destinations

```text
Home
Spools
Printers
Settings
```

Compact width uses bottom navigation. Wider layouts use a navigation rail.

Each destination maintains its own child stack.

### 14.2 Transient root workflows

NFC assignment and tag mutation are root-level transient flows, not ordinary destination stack entries.

```text
Processing
Combined confirmation
Success
Error
Tag mutation session
```

### 14.3 UDF contract

Each feature exposes:

```kotlin
interface FeatureComponent {
    val state: StateFlow<FeatureState>
    fun accept(intent: FeatureIntent)
}
```

Reducers are deterministic and side-effect free.

```text
Intent
→ reducer
→ state + effect
→ effect handler
→ result intent
→ reducer
```

Compose UI does not call repositories directly.

### 14.4 Screen state

Data screens distinguish:

```text
InitialLoading
Content
ContentRefreshing
FatalErrorWithoutCache
```

Content may additionally include:

```text
isStale
nonBlockingError
mutationAvailability
```

### 14.5 Home

Home contains only:

- primary NFC instruction;
- Bambuddy connection state;
- NFC availability state;
- stale/offline banner when required;
- navigation shell.

It must not become a dashboard.

### 14.6 Spool list

Default filter:

```text
active
nonarchived
remaining > 0
```

Additional views expose archived and empty spools.

Search fields:

```text
spool name
manufacturer / brand
material
color name
```

Search uses a cancellable Room query with 150 ms debounce.

Sorting:

1. `lastUsed` descending where available;
2. normalized display name ascending;
3. ID tie-breaker.

Color is never the sole identifier.

### 14.7 Printer screens

Printer list and detail show assignment-relevant state only.

External slots:

- selectable for manual assignment;
- show physical labels and current spool.

AMS slots:

- visible read-only;
- nonselectable as assignment targets.

### 14.8 Settings

Sections:

```text
Connection
  Base URL
  API token status / Replace
  Test connection

Assignment
  Default printer

Security
  HTTP acknowledgement state when relevant
  Invalid TLS verification override for configured host
```

No reset-app action.

### 14.9 Setup

First run is one scrollable screen containing URL, token, Test connection and Save.

The connection is saved only after reachability and authentication validation.

### 14.10 Default printer

- one server printer: automatically becomes default;
- multiple printers: selection deferred until first assignment;
- default preselects target but still requires confirmation when multiple printers exist;
- deleted default is cleared automatically.

### 14.11 Adaptive layout

Width classes:

```text
Compact:  < 600 dp
Medium:   600–839 dp
Expanded: ≥ 840 dp
```

Compact uses single-pane navigation. Medium/Expanded may use list-detail panes, but business flows and components remain shared.

Orientation is not locked.

### 14.12 State restoration

Restore:

```text
selected root destination
safe list/detail navigation
search and filter state
selected detail ID
settings navigation location
```

Do not restore:

```text
overwrite/clear authorization
pending API token text
platform NFC session
assignment replay
unverified mutation
```

---

## 15. Accessibility, themes and localization

### 15.1 Semantics

Every interactive element requires:

- semantic role;
- accessible label;
- selected/enabled/state description where relevant;
- meaningful focus order.

Processing uses a live-region announcement.

Success and Error titles receive initial focus.

Disabled mutation actions communicate the reason.

### 15.2 Confirmation focus order

```text
title
spool/current assignment
target printer
target slot
warning
primary action
cancel
```

Destructive actions must not receive focus before the user can review context.

### 15.3 Touch and input

- minimum interactive target: 48×48 dp;
- no swipe-only, long-press-only or gesture-only critical actions;
- keyboard and switch focus behavior uses standard Compose semantics.

### 15.4 Text scaling

No max font-scale restriction.

Critical content and actions must remain reachable at large font scales. Rows and dialogs may grow and scroll.

### 15.5 Color

When a color name exists, it is included in accessible text.

When absent, the swatch is decorative and other text identifies the spool.

### 15.6 Themes

Light and dark themes follow the system. No manual override in v1.

### 15.7 Localization

English only in v1, but all user-facing strings are resource-based.

Domain and repositories expose typed reasons, not hardcoded user text.

Technical diagnostics remain stable English.

---

## 16. Performance and reliability

### 16.1 Assignment latency budget

Target from NFC payload read to verified success publication:

```text
payload decode / local lookup       ≤ 100 ms
targeted context refresh            ≤ 600 ms
assignment POST                     ≤ 500 ms
verification GET                    ≤ 500 ms
state publication/render            ≤ 100 ms
reserve                             ≤ 200 ms
Total                               ≤ 2,000 ms
```

Measured with a monotonic clock.

User confirmation time and retry scenarios are excluded.

### 16.2 NFC cold launch

Processing state must be visible within 1 second of NFC-triggered cold start.

The first render must not wait for full sync or feature preloading.

### 16.3 Main-thread policy

Network, database and NFC I/O must run off the main thread.

Debug builds enable Android StrictMode for main-thread I/O and resource-leak detection.

### 16.4 Memory and large data

- no duplicate full snapshot in presentation state;
- Room queries return projections;
- large lists use lazy rendering and paging/limit-offset abstractions;
- stable keys use server/domain IDs;
- search remains database-backed;
- raw successful response bodies are not retained.

Synthetic engineering dataset baseline:

```text
100 printers
1,000 slots
25,000 spools
large assignment set appropriate to schema
```

This is not a product limit.

### 16.5 Failure isolation

Secondary failures must not invalidate verified core outcomes.

Examples:

- haptic failure does not turn assignment success into error;
- clipboard failure does not change assignment state;
- sync failure preserves cached data;
- one printer-status failure does not destroy other cached screens.

### 16.6 No circuit breaker/background monitor

No circuit breaker, periodic health worker, wake lock or background polling exists in v1.

Connection health is derived from current operations and refresh attempts.

### 16.7 Graceful degradation

```text
Server offline       → cached viewing + local NFC read
NFC unsupported      → viewing + manual assignment
NFC disabled         → viewing + manual assignment + settings action
Printer status stale → viewing; assignment blocked
Token unavailable    → cached viewing + replacement flow
Room cache failure   → preserve settings/token; rebuild cache from server
```

### 16.8 SLOs

```text
NFC processing visible on cold launch:       ≤ 1 s
Happy-path verified assignment:              ≤ 2 s
Valid NTAG213 read within two attempts:       ≥ 99%
Wrong-slot false success:                     0 tolerated
Offline mutation:                             0 tolerated
Tag write/clear success without verification: 0 tolerated
```

No production telemetry-based error budget is defined.

---

## 17. Testing strategy

### 17.1 Test pyramid

**commonTest:**

- domain rules;
- state machines and reducers;
- assignment orchestration;
- retry and verification;
- topology resolution;
- synchronization generations;
- redaction;
- URI codec;
- tag mutation workflows.

**Android host/device tests:**

- Room initialization and migrations;
- Android NFC adapter;
- Keystore-backed SecureStorage;
- platform settings navigation;
- Activity and intent routing.

**iOS Simulator:**

- actual execution of shared tests;
- mock graph construction;
- shared root UI instantiation;
- Android dependency leakage detection.

**Physical/manual:**

- RF reliability;
- real NTAG213 read/write behavior;
- real local-network latency;
- complete release regression.

### 17.2 Determinism

Use injected clock, dispatchers and virtual time.

Real sleeps are prohibited in unit/orchestrator tests.

Race tests use controlled barriers rather than probabilistic loops.

### 17.3 Fakes and mocks

Scripted fakes are preferred for repositories and services.

Mocking frameworks are allowed only when a fake would be materially more complex and must remain compatible with KMP target tests.

### 17.4 Fixture policy

Synthetic fixtures must:

- reflect real documented shape and nullability;
- include unknown additive fields;
- avoid private hostnames, API keys, access codes and personal inventory data;
- identify provenance as Bambuddy OpenAPI 0.2.4.7;
- remain minimal and reviewable.

Fixture categories per critical endpoint:

```text
minimal valid
representative valid
invalid/incompatible
```

### 17.5 Required business scenarios

- canonical and invalid NFC URI parsing;
- one-printer/one-slot zero-confirmation;
- combined printer/slot selection;
- spool move confirmation;
- already assigned;
- target replacement;
- retry counts and delays;
- POST success + immediate/delayed verification;
- verification failure without POST replay;
- pending configuration;
- duplicate scan suppression;
- scan supersession before and after POST;
- snapshot atomicity and rollback;
- stale viewing and offline mutation guard;
- URL and token replacement semantics;
- redirect and TLS override policy;
- central redaction adversarial inputs;
- process recreation without mutation replay.

### 17.6 Room tests

- complete snapshot replacement;
- deletion of server-absent entities;
- unique `SlotKey` constraints;
- indexed search/filter/sort;
- large dataset lazy/paged queries;
- migration from every released schema;
- URL-change cache clearing;
- corruption recovery preserving settings and token.

### 17.7 UI tests

Cover:

- setup;
- Home online/offline/NFC-disabled;
- assignment processing, combined confirmation, success and error;
- spool search/filter/detail;
- printer list/detail and manual assignment;
- tag write/overwrite/clear;
- compact/medium/expanded widths;
- light/dark themes;
- large font;
- critical accessibility semantics and focus.

### 17.8 Android shell tests

- canonical NFC cold start;
- canonical NFC `onNewIntent` while active;
- no Activity accumulation;
- unrelated NDEF URI not intercepted;
- API 23 install/launch smoke;
- rotation and process recreation.

### 17.9 Physical NFC matrix

Use at least three NTAG213 tags and the selected release test device.

Read protocol:

```text
100 controlled read attempts
success within at most two presentations
≥ 99% success
```

Mutation matrix:

- blank write;
- valid overwrite;
- unsupported-payload overwrite;
- clear;
- rewrite cleared tag;
- repeated write;
- tag removed before/during write;
- tag removed during verification;
- same-spool rewrite;
- read-only negative test when available;
- cold-launch scan;
- repeated scans.

### 17.10 Defect severity

**Critical:**

- secret leak;
- wrong printer/slot assignment without confirmation;
- destructive write to the wrong tag;
- false verified success;
- release binary unusable.

**High:**

- core assignment path broken;
- offline mutation;
- supported NFC read/write unreliability;
- cold NFC entry failure;
- inaccessible critical workflow.

Critical and High defects block release.

---

## 18. CI/CD and release engineering

### 18.1 GitHub Actions

Triggers:

- every push;
- every pull request;
- manual workflow dispatch.

Superseded PR runs are cancelled. Release/manual runs are not cancelled by normal branch concurrency.

### 18.2 Required jobs

```text
validate-wrapper-and-config
common-tests
android-build
android-host-tests
android-device-tests
compose-ui-tests
ios-compile-and-tests
lint-detekt-architecture
dependency-license-security
```

### 18.3 Toolchain baseline

```text
JDK 17
Gradle 9.1+
AGP 9.0.x
Kotlin/KGP compatible with AGP 9, Compose Multiplatform, Room KMP and Metro
```

Exact dependency versions are fixed in a version catalog after the compatibility spike.

Dynamic dependency versions are forbidden.

### 18.4 Android CI

Build:

```text
:androidApp:assembleDebug
:androidApp:assembleRelease
```

Ordinary CI release build is unsigned or uses non-production signing. Permanent release signing material never enters GitHub Actions.

Managed emulator matrix includes:

- API 23 compatibility smoke;
- a modern stable API for the primary suite.

### 18.5 iOS CI

macOS runner validates:

- `iosArm64` compilation;
- `iosSimulatorArm64` compilation;
- shared iOS Simulator test execution;
- minimal iOS app build/render smoke where supported by the harness.

### 18.6 Static gates

- Android Lint is blocking;
- Detekt configured rules are blocking;
- formatter is blocking;
- architecture checks are blocking;
- compiler warnings remain visible but do not globally fail the build;
- commonMain forbidden imports are checked;
- secrets and private fixture data are scanned;
- dependency licenses and Critical/High exploitable vulnerabilities block release.

### 18.7 Caching and supply chain

- Gradle/dependency caching enabled;
- signing material and credentials never cached;
- Gradle dependency verification enabled after graph stabilization;
- dependency locking used where compatible;
- dependency updates use separate PRs with the full matrix.

### 18.8 Release process

Only `debug` and `release` build types exist in `androidApp`.

Manual local release procedure:

```text
clean checkout
→ run required verification
→ assemble signed release APK
→ verify APK signature and manifest
→ compute SHA-256
→ install/smoke test
→ execute physical NFC and manual regression on the exact binary
→ create immutable Git tag
→ publish GitHub Release and signed APK
```

The permanent signing key remains outside the repository and GitHub Actions.

### 18.9 Release artifact checks

- correct application ID;
- monotonic versionCode;
- public versionName;
- minSdk 23;
- expected targetSdk;
- release/debuggable flags correct;
- no test URL, token or private OpenAPI artifact;
- valid signature;
- installability;
- SHA-256 checksum.

### 18.10 Release notes

Manually include:

- user-visible changes;
- supported Android/NFC test matrix;
- known limitations;
- Bambuddy reference baseline;
- APK checksum.

No automatic in-app update mechanism or rollback exists.

---

## 19. Threat model

### 19.1 Assets

```text
Bambuddy API token
assignment integrity
NFC tag payload integrity
configured server identity
cached inventory metadata
release signing key
```

### 19.2 Trust boundaries

```text
Android app ↔ NFC tag
Android app ↔ local/public network
common code ↔ platform adapters
process memory ↔ encrypted storage
user-configured URL ↔ HTTP client
repository ↔ public CI and release artifacts
```

### 19.3 Primary threats and controls

| Threat | Primary control |
|---|---|
| Token leakage in logs/errors | Central structural redaction; no persistent logs |
| Token forwarded to unrelated host | Manual redirect validation before header attachment |
| MITM over HTTP | Explicit origin-scoped consent |
| MITM with invalid TLS override | Explicit warning; exact hostname scope |
| Wrong physical slot | Known topology resolver; exact fail-closed mapping |
| Duplicate assignment POST | Serialized workflow; duplicate scan suppression; immutable retries |
| Malicious NFC payload | Strict canonical parser; no partial recovery |
| Wrong-tag overwrite | Tag fingerprint continuity; reread before retry |
| Stale offline mutation | Presentation and repository-level mutation guards |
| Server response memory exhaustion | Bounded decompressed body limits |
| Private instance data in public repository | Synthetic fixtures; secret/private-host scanning |
| Supply-chain tampering | Pinned versions; dependency verification; release review |

---

## 20. Accepted risks and debt

1. NFC URI has no schema version; future formats must preserve legacy parsing.
2. Numeric spool IDs may collide after changing Bambuddy instances.
3. HTTP transmits tokens without encryption after explicit consent.
4. TLS verification bypass permits MITM for the approved hostname.
5. API capabilities are not proactively negotiated.
6. Assignment POST may be resent after the server processed a prior attempt.
7. CI uses mocks and fixtures rather than a live Bambuddy instance.
8. No persistent diagnostics may make intermittent failures harder to investigate.
9. Multi-slot support depends on explicit known topology mappings.
10. Token necessarily exists in process memory during a request.
11. KMP/AGP/Compose/Room/Metro ecosystem compatibility may require coordinated version pinning.

---

## 21. Architecture Decision Records

### ADR-001 — Separate Android application shell for AGP 9

**Status:** Accepted  
**Decision:** use `shared`, `androidApp`, `iosApp`.  
**Reason:** AGP 9 does not support combining Android application and KMP plugins in one subproject.  
**Consequence:** Android manifest, build types, signing and Activity live in `androidApp`; product logic and UI stay shared.

### ADR-002 — Bambuddy 0.2.4.7 reference baseline

**Status:** Accepted  
**Decision:** use the factual API shape without runtime version checking.  
**Consequence:** private OpenAPI export is not committed; sanitized subset fixtures define tests.

### ADR-003 — Server-authoritative atomic snapshot

**Status:** Accepted  
**Decision:** Room is the UI source; complete snapshots commit atomically.  
**Consequence:** partial network success never creates a partial published snapshot.

### ADR-004 — Exact verified assignment

**Status:** Accepted  
**Decision:** product success requires exact GET verification of `printer_id + ams_id + tray_id + spool_id`.

### ADR-005 — Known slot topology only

**Status:** Accepted  
**Decision:** multiple external slots are supported only when coordinates are unambiguous. Unknown topology blocks mutation.

### ADR-006 — One combined confirmation

**Status:** Accepted  
**Decision:** printer choice, slot choice and move warning are combined into one user confirmation.

### ADR-007 — No resumable mutations

**Status:** Accepted  
**Decision:** no assignment or tag mutation is persisted for replay after process death.

### ADR-008 — Canonical NFC read-back verification

**Status:** Accepted  
**Decision:** tag mutation success requires independent reread and canonical application-payload equality.

### ADR-009 — Host-scoped TLS verification override

**Status:** Accepted security compromise  
**Decision:** invalid certificate verification may be disabled only for the configured hostname after explicit consent.

### ADR-010 — Minimal Gradle modularization

**Status:** Accepted  
**Decision:** only the platform-required modules are created; feature separation remains package-based.

---

## 22. Risk register

| ID | Risk | Probability | Impact | Mitigation |
|---|---|---:|---:|---|
| RISK-001 | Bambuddy API drift | Medium | High | Baseline contract, tolerant unknown fields, strict required fields, fixtures |
| RISK-002 | External slot mapping drift | Medium | Critical | Known topology only, exact coordinates, fail closed |
| RISK-003 | POST processed but response lost | Low/Medium | Medium | Immutable retry, upsert assumption, exact verification |
| RISK-004 | TLS override MITM | Environment-dependent | High | Explicit warning, hostname scope, no redirect inheritance |
| RISK-005 | HTTP token exposure | Environment-dependent | High | Explicit origin consent and trusted-network warning |
| RISK-006 | No persistent diagnostics | Medium | Medium | Rich ephemeral redacted details, deterministic tests |
| RISK-007 | NFC hardware variability | Medium | High | NTAG213 guarantee, selected device, physical matrix |
| RISK-008 | AGP/KMP dependency incompatibility | Medium | High | Compatibility spike, pinned versions, iOS CI |
| RISK-009 | Large snapshot memory pressure | Low/Medium | Medium | Body limits, Room projections, lazy UI, capacity tests |
| RISK-010 | Keystore key invalidation | Low | Medium | Token re-entry without cache/settings loss |
| RISK-011 | Verification visibility delay | Low | Medium | Bounded verification polling without POST replay |
| RISK-012 | Secret leakage in public repository | Medium | Critical | Synthetic fixtures, scans, no private OpenAPI or credentials |

---

## 23. Open Questions

### OPEN-NFC-DEVICE-01 — Supported physical Android device

**Release blocking.**

```text
Model:
Android version:
Build number:
NFC chipset, if known:
```

### OPEN-APP-ID-01 — Android application ID

Must be finalized before the first signed release.

Proposed:

```text
ru.nonamee.bambuddyspoolmanager
```

### OPEN-REPOSITORY-01 — Public repository coordinates

```text
GitHub owner:
Repository name:
License:
```

### OPEN-TOPOLOGY-01 — Multi-external-slot fixtures

Implementation requires sanitized fixtures or source-derived mapping rules for at least one multi-external-slot topology.

Until then:

- the shared model and UI support multiple slots;
- the known A1 single-slot mapping is supported;
- unknown multi-slot topologies fail closed.

### OPEN-VERSION-MATRIX-01 — Exact dependency versions

Resolve through a compatibility spike covering:

```text
Kotlin/KGP
Compose Multiplatform
AGP 9.x
Gradle
Room KMP
Decompose
Ktor
Metro
Kotlinx Serialization
```

The spike must prove Android build, iOS compilation/tests, Android API 23 compatibility and minimal shell launch.

---

## 24. Backlog epics

### EPIC-01 — Project bootstrap and toolchain

Scope:

- repository setup;
- AGP 9 `shared/androidApp/iosApp` topology;
- version catalog and wrappers;
- Metro graph skeleton;
- minimal shared UI;
- Android and iOS shells;
- baseline CI.

Exit:

- Android debug build;
- iOS targets compile;
- shared tests execute on JVM and iOS Simulator;
- iOS shell renders shared UI.

### EPIC-02 — Domain and API contract

Scope:

- IDs and domain models;
- narrow transport DTO subset;
- Ktor client;
- authentication validation;
- error taxonomy;
- sanitized fixtures and contract manifest.

### EPIC-03 — Security and connection management

Scope:

- URL canonicalization;
- SecureStorage;
- DataStore settings;
- setup and token replacement;
- HTTP warning;
- TLS override;
- redirect policy;
- central redaction.

### EPIC-04 — Persistence and synchronization

Scope:

- Room schema and indexes;
- atomic snapshot;
- full and targeted refresh;
- generation/race handling;
- stale state;
- URL-change cache clearing;
- migration tests.

### EPIC-05 — Printer and slot topology

Scope:

- printer/status mappings;
- `SlotTopologyResolver`;
- A1 mapping;
- multi-slot representation and labels;
- unsupported topology failures.

### EPIC-06 — Assignment orchestration

Scope:

- printer and slot resolution;
- combined confirmation;
- move conflict;
- already-assigned behavior;
- immutable POST retries;
- exact verification;
- pending configuration;
- manual assignment reuse.

### EPIC-07 — Android NFC read and entry

Scope:

- manifest intent filters;
- Activity routing;
- cold-start processing;
- URI/NDEF parsing;
- capability classification;
- duplicate scan suppression.

### EPIC-08 — NFC tag mutation

Scope:

- write/link;
- overwrite;
- clear;
- read-back verification;
- uncertain-outcome recovery;
- tag identity continuity;
- platform tests.

### EPIC-09 — Shared application UI

Scope:

- root navigation;
- Home;
- Spools;
- Printers;
- Settings;
- transient workflows;
- adaptive layouts;
- themes.

### EPIC-10 — Accessibility and localization readiness

Scope:

- TalkBack semantics;
- focus order;
- large fonts;
- touch targets;
- keyboard/switch behavior;
- resource strings.

### EPIC-11 — Automated verification

Scope:

- common business tests;
- network fixtures;
- Room integration;
- reducer/component tests;
- Compose UI tests;
- Android host/device tests;
- iOS tests;
- architecture/security checks.

### EPIC-12 — Performance and reliability hardening

Scope:

- cold-start instrumentation;
- latency budget validation;
- query and memory benchmarks;
- large-data tests;
- race/failure tests;
- StrictMode cleanup;
- bounded soak.

### EPIC-13 — CI and release engineering

Scope:

- all required CI jobs;
- managed emulators;
- macOS/iOS job;
- secret/license/vulnerability checks;
- signing documentation;
- release checklist and artifact verification.

### EPIC-14 — Physical acceptance and public release

Scope:

- choose supported NFC device;
- physical NTAG213 matrix;
- manual regression;
- local-network latency checks;
- signed release candidate;
- Critical/High gate;
- GitHub Release.

---

## 25. Critical path

```text
EPIC-01 Bootstrap
→ EPIC-02 API contract
→ EPIC-03 Connection/security
→ EPIC-04 Persistence/sync
→ EPIC-05 Slot topology
→ EPIC-06 Assignment
→ EPIC-07/08 NFC
→ EPIC-09 UI completion
→ EPIC-11/12 Verification and hardening
→ EPIC-14 Release
```

CI and release engineering begin in EPIC-01 and evolve continuously.

---

## 26. Definition of Done

An implementation item is complete only when:

- behavior matches this TECHSPEC;
- branching business logic has automated tests;
- common code compiles for iOS;
- no raw secret or persistent diagnostic path is introduced;
- offline and error states are handled;
- new UI has accessibility semantics;
- architecture boundaries remain intact;
- required CI passes;
- affected documentation/ADR is updated;
- no product invariant is weakened.

---

## 27. Release acceptance

Release is permitted only when:

- all v1 requirements are implemented except full iOS functionality;
- Android debug/release and iOS targets build;
- common, Android, Compose UI and iOS Simulator tests pass;
- Android Lint, Detekt, formatter, architecture, secret and dependency gates pass;
- minimal iOS shell launches and renders shared UI;
- physical NTAG213 matrix passes on the declared supported device;
- happy-path local-network assignment satisfies the 2-second target;
- NFC cold processing satisfies the 1-second target;
- manual regression is complete;
- no known Critical or High defect remains;
- the exact tested release-candidate APK is signed, verified and published to GitHub Releases.

---

## 28. Traceability summary

| PRD concern | Technical realization |
|---|---|
| Ultimate Simplicity | zero-confirmation safe path; one combined confirmation; minimal IA |
| High Performance | targeted refresh, latency budgets, cache-first UI, deferred bootstrap |
| Server as source of truth | atomic snapshot, fresh mutation context, exact verification |
| Explicit destructive behavior | move/overwrite/clear confirmations; wrong-tag protection |
| Minimal platform coupling | shared UI/business logic; narrow injected platform interfaces |
| Offline viewing | Room snapshot and stale content states |
| No offline mutation | presentation and repository mutation guards |
| NFC reliability | strict parser, serialized session, independent reread verification |
| Security | SecureStorage, redirect policy, host-scoped TLS compromise, redaction |
| Accessibility | semantics, focus, touch targets, large-font/adaptive support |
| Future iOS | shared code, iOS compile/tests, explicit mock platform graph |
| Public repository | sanitized fixtures, secret scanning, no private OpenAPI export |
| Release quality | deterministic tests, physical matrix, severity gates, signed APK |

---

**End of TECHSPEC.md**
