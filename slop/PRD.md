# Product Requirements Document

## Bambuddy NFC Spool Manager

**Status:** Ready for implementation  
**Version:** 1.0  
**Platform:** Android  
**Architecture:** Kotlin Multiplatform with future iOS support  
**Distribution:** Public GitHub repository and GitHub Releases

---

## 1. Product summary

Bambuddy NFC Spool Manager is an Android application for rapidly assigning a physical filament spool to a 3D printer managed by a self-hosted Bambuddy instance.

The product is designed primarily for printers without AMS, where assigning a newly installed spool through the Bambuddy web interface requires too many manual steps.

The application reduces the primary workflow to:

1. Install the physical spool.
2. Scan its NFC tag with the phone.
3. Confirm the target printer or slot only when the destination is ambiguous.
4. Receive confirmation that the spool was assigned.

The application is not a complete Bambuddy mobile client. Its primary purpose is fast and reliable spool assignment through NFC.

---

## 2. Product principles

### 2.1 Ultimate Simplicity

The default workflow must require one NFC scan and no unnecessary confirmation.

A confirmation is allowed only when the application cannot safely determine the intended printer or external spool slot.

### 2.2 High Performance

On a normal local network, a successful first-attempt NFC assignment must complete within two seconds.

The application must display an active processing state within one second of a cold start initiated by an NFC scan.

### 2.3 Server as source of truth

Bambuddy is the only authoritative source for:

- printers;
- spools;
- spool assignments;
- remaining filament;
- slot state.

The application may cache data for viewing but must not perform offline mutations.

### 2.4 Explicit destructive behavior

Potentially ambiguous operations, such as moving a spool from another printer or overwriting an existing NFC tag, require confirmation.

### 2.5 Minimal platform coupling

Product UI, navigation, state management and business logic must reside in shared Kotlin Multiplatform code.

Platform APIs must remain behind narrow interfaces.

---

## 3. Target user

The first version is intended for one technically experienced user operating a personal self-hosted Bambuddy instance.

The product must nevertheless be engineered as a complete and reliable first version rather than a disposable prototype.

---

## 4. Primary problem

Without AMS, assigning a spool currently requires the user to:

1. Remove the current spool.
2. Install a new spool.
3. Open the Bambuddy website.
4. Navigate to the printer.
5. Select “Assign spool.”
6. Find the correct spool.
7. Confirm the assignment.

This workflow creates unnecessary context switching between the physical printer and the web interface.

---

## 5. Product objective

Allow a spool to be assigned to a Bambuddy printer through one NFC scan and no more than one confirmation.

---

## 6. Success criteria

The product is successful when:

- the standard assignment flow requires one NFC scan;
- no more than one confirmation is required;
- at least 99% of valid NTAG213 reads succeed within two scan attempts on the supported test device;
- every NFC write is verified by reading the exact payload back;
- ambiguous assignment states always require explicit user confirmation;
- manual assignment remains available without being treated as a failure condition.

No product analytics or remote telemetry are used to measure these criteria. Verification is performed through automated tests and manual acceptance testing.

---

## 7. Scope

### 7.1 Included in v1

- Connection to one Bambuddy instance.
- Manual Bambuddy URL configuration.
- API-token authentication.
- Reading NFC spool tags.
- Writing, overwriting, verifying and clearing NFC tags.
- Assigning tagged spools to external printer slots.
- Viewing printers and current slot assignments.
- Viewing Bambuddy spools.
- Manually assigning spools without NFC.
- Searching and filtering spools.
- Viewing cached data while offline.
- Android application built with Kotlin Multiplatform and Compose Multiplatform.
- Compilable iOS targets and a minimal iOS shell using mock platform services.

### 7.2 Explicitly out of scope

- Creating or editing Bambuddy spools.
- Editing printer configuration.
- Editing remaining filament.
- Multiple Bambuddy instances or connection profiles.
- Offline or deferred assignments.
- Background assignment queue.
- User accounts or multi-user collaboration.
- Product analytics.
- Crash reporting.
- Persistent diagnostic logs.
- Automatic update checks.
- Full iOS product functionality in v1.
- NFC assignment to AMS slots.
- Complete Bambuddy mobile-client functionality.

---

## 8. Supported hardware and tags

### 8.1 Android devices

The application must support Android devices from API 23.

Installation is allowed on devices without NFC. On such devices, manual assignment and data viewing remain available.

### 8.2 NFC tags

NTAG213 is the officially supported and tested tag type.

Other writable NDEF-compatible tags may function but are not guaranteed or included in the release test matrix.

### 8.3 Orientation and device classes

The interface is designed for portrait use but must not lock orientation in the Android manifest.

The UI must adapt to phones, tablets and foldable devices without introducing a separate tablet information architecture.

---

## 9. NFC data contract

### 9.1 Payload

Each linked tag contains one NDEF URI record:

`bambuddy-spool://spool/<id>`

`<id>` is the internal numeric spool identifier returned by Bambuddy.

### 9.2 Instance relationship

The tag does not contain the Bambuddy server URL or instance identifier.

The application supports exactly one configured Bambuddy instance.

Changing the configured server may cause existing tags to resolve to unrelated numeric spool IDs. The application must display a critical warning before saving a different Bambuddy URL.

### 9.3 Versioning limitation

The URI format does not include a schema version.

Future incompatible formats must therefore preserve explicit backwards compatibility with:

`bambuddy-spool://spool/<id>`

This is accepted architectural debt.

---

## 10. NFC operations

### 10.1 Read

When a valid supported NFC tag is scanned:

1. Parse the NDEF URI.
2. Extract the numeric spool ID.
3. Load the spool from current or refreshed Bambuddy data.
4. Resolve the destination printer and external slot.
5. Perform the assignment or request confirmation when required.
6. Verify the resulting Bambuddy assignment.
7. Display the result.

### 10.2 Unknown or empty tag

An empty or unknown tag must offer to link the tag to an existing Bambuddy spool.

The user selects a spool first and then writes it to the tag.

Spool creation is not supported.

### 10.3 Overwrite

If the tag already contains a spool association, the application must display:

- the currently encoded spool;
- the newly selected spool;
- an explicit overwrite confirmation.

### 10.4 Write verification

After writing, the application must read the NDEF message back and verify exact equality with the expected payload.

A write is unsuccessful if verification fails, regardless of the result returned by the platform NFC write API.

### 10.5 Clear

Clearing a tag writes an empty NDEF message while keeping the tag NDEF-formatted and writable.

### 10.6 Damaged or unsupported payload

The application must:

- explain that the payload is invalid or unsupported;
- avoid attempting partial recovery;
- offer to overwrite the tag.

### 10.7 Tag locking

The application must not provide irreversible tag locking.

Tags remain rewritable.

---

## 11. NFC application entry

A supported NFC tag must be able to launch the Android application without the user opening it first.

When the application is already active, the existing Activity receives the new NFC intent and immediately starts a new NFC flow.

A new Activity instance must not be added to the back stack for every scan.

A new scan may interrupt and replace an existing success-result screen.

---

## 12. Printer resolution

### 12.1 One printer

When Bambuddy contains one printer:

- it is selected automatically;
- the assignment proceeds without confirmation;
- the result screen is shown after verification.

### 12.2 Multiple printers with a default

When multiple printers exist and a default printer is configured:

- the default printer is preselected;
- the user must confirm the assignment.

### 12.3 Multiple printers without a default

The application displays a minimal printer list.

The selection UI includes an option to save the selected printer as the default.

### 12.4 Default-printer setup

If exactly one printer is found during setup or synchronization, it becomes the default automatically.

If multiple printers exist, default selection is deferred until the first assignment flow.

---

## 13. Slot resolution

NFC assignment is limited to external spool slots.

The application obtains available external slots from actual printer state rather than relying only on the printer model name.

When one external slot exists, it is selected automatically.

When several external slots exist, the user must select the target slot.

AMS slots may be displayed on the printer screen but are not selectable as NFC assignment destinations in v1.

---

## 14. Assignment behavior

### 14.1 API operation

The application calls the Bambuddy assignment API once for the target:

- `printer_id`;
- `ams_id`;
- `tray_id`;
- `spool_id`.

Bambuddy is responsible for replacing the previous assignment of that slot.

The application must not first issue a separate unassign request.

### 14.2 Previous spool

The currently assigned spool is automatically replaced.

No confirmation is required solely because the target slot already has another spool.

### 14.3 Spool already assigned to target

If the scanned spool is already assigned to the resolved target slot:

- no mutation is performed;
- the application shows that the spool is already assigned.

The operation must be idempotent from the user’s perspective.

### 14.4 Spool assigned elsewhere

If the scanned spool is currently assigned to another printer or slot:

- the application explains the current assignment;
- moving it requires explicit confirmation.

### 14.5 Retry

The assignment request may be repeated automatically for all failures except HTTP 4xx responses.

There may be:

- one initial request;
- up to three retry requests.

Retry delays are:

- 500 milliseconds;
- 1 second;
- 2 seconds.

The client resends the same assignment without first issuing a verification request.

This behavior is accepted because the Bambuddy assignment operation performs an upsert of the target slot.

### 14.6 Verification

After a successful assignment response, the application reloads assignments and verifies an exact match of:

- `printer_id`;
- `ams_id`;
- `tray_id`;
- `spool_id`.

An HTTP success response alone is insufficient.

### 14.7 Pending printer configuration

If Bambuddy reports that the inventory assignment succeeded but printer configuration is pending:

- the operation is considered successful;
- the result screen displays secondary text explaining that printer configuration will be applied later.

### 14.8 Undo

No Undo action is provided.

A correction is performed through another scan or manual assignment.

---

## 15. Manual assignment

The printer screen must allow a spool to be assigned manually.

Manual assignment uses the same:

- printer-resolution rules;
- slot-resolution rules;
- conflict handling;
- API mutation;
- retry policy;
- result verification.

Manual assignment is a first-class supported workflow and is not limited to devices without NFC.

---

## 16. Spool list

### 16.1 Default dataset

The default list shows active, nonarchived spools with remaining filament greater than zero.

Other spools remain accessible through an additional filter.

### 16.2 Assigned spools

Spools assigned to a printer or slot remain visible.

Their current assignment is displayed as secondary information.

### 16.3 Presentation

Each spool item shows a compact composition of:

- spool name when available;
- manufacturer;
- material;
- color;
- remaining filament as secondary text.

A color swatch must be accompanied by a textual color name when Bambuddy provides one.

When no color name exists, color is treated as a secondary decorative attribute and must not be the sole means of identification.

### 16.4 Search

Search must match:

- spool name;
- manufacturer;
- material;
- color.

### 16.5 Sorting

Recently used spools appear first.

Remaining spools are sorted by name.

### 16.6 Deleted spool referenced by NFC

When a tag refers to a spool that no longer exists:

- display a precise error;
- offer to link the tag to another existing spool;
- do not silently treat the tag as empty.

---

## 17. Information architecture

The application contains four primary destinations:

1. Home
2. Spools
3. Printers
4. Settings

### 17.1 Home

The home screen contains:

- primary instruction: “Hold an NFC tag near your device”;
- Bambuddy connection status;
- NFC availability state;
- navigation to other sections.

It must not resemble a dashboard or duplicate the Bambuddy web interface.

### 17.2 Printers

The printer list shows current assignment state.

A printer detail screen provides:

- printer identity;
- external and AMS slot state;
- current spool assignment;
- manual spool assignment for external slots.

### 17.3 Spools

The spool list supports browsing, filtering and search.

The spool detail screen provides:

- spool metadata;
- remaining filament;
- current assignment;
- write spool to NFC tag;
- overwrite NFC tag;
- clear NFC tag;
- assign spool manually.

The application does not edit Bambuddy spool metadata.

### 17.4 Settings

Settings provide:

- Bambuddy base URL;
- API token replacement;
- connection test;
- default printer selection when applicable;
- insecure connection configuration when required.

There is no application-reset action.

---

## 18. First-run setup

The first launch opens a single connection screen rather than a multipage wizard.

The screen contains:

- Bambuddy base URL;
- API token;
- connection test;
- save action.

The URL and token are entered on the same screen.

The application validates:

- server reachability;
- successful authentication.

It does not proactively validate:

- Bambuddy version;
- API capabilities;
- support for every required endpoint.

Incompatibilities are reported when the affected operation is executed.

---

## 19. Bambuddy connection

### 19.1 URL support

The application accepts any URL supported by the Android networking stack, including:

- HTTP;
- HTTPS;
- IP addresses;
- local hostnames;
- public hostnames;
- custom ports.

### 19.2 Authentication

Authentication uses a Bambuddy API token.

The token must never be displayed after being saved.

The user may replace it but cannot reveal or copy its current value.

### 19.3 Secure storage

The common application layer accesses credentials through a `SecureStorage` interface.

Android uses Keystore-backed storage.

Future iOS implementation uses Keychain.

### 19.4 Connection validation

Saving a connection validates server access and authentication only.

### 19.5 URL change

Changing the Bambuddy base URL requires explicit confirmation explaining that existing NFC tags contain only numeric spool IDs and may map incorrectly on the new instance.

After confirmation, all cached domain data is cleared.

---

## 20. Network security

### 20.1 HTTP

HTTP is allowed for any host.

When the user saves an HTTP URL for the first time, the application displays a warning that the API token will be sent without transport encryption.

The user must explicitly confirm.

### 20.2 Invalid TLS certificates

When an HTTPS connection fails certificate validation, the application may offer to disable TLS certificate verification for the current host.

This requires an ordinary confirmation dialog.

The override applies only to the configured host.

After confirmation, the application does not display a persistent insecure-connection indicator.

This is an explicitly accepted security compromise for self-hosted environments.

### 20.3 Redirects

The application follows redirects only within the same origin, except that an HTTP-to-HTTPS upgrade is permitted when the hostname remains the same, regardless of port.

The API token must not be forwarded to an unrelated host.

### 20.4 Diagnostic redaction

Technical error details must remove:

- API tokens;
- `Authorization` headers;
- secret custom headers;
- credentials embedded in URLs;
- sensitive query parameters.

---

## 21. Offline behavior and caching

### 21.1 Snapshot

The application stores a complete local snapshot of all Bambuddy entities and fields required by supported screens and workflows.

This includes the application-relevant subset of:

- printers;
- slots;
- spools;
- assignments;
- display metadata.

“Complete snapshot” does not mean mirroring unrelated Bambuddy database entities.

### 21.2 Offline viewing

When Bambuddy is unavailable:

- cached data remains viewable;
- cached screens are marked as stale;
- assignment, writing-related server operations and other mutations are blocked.

NFC tags may be read locally, but the application must not claim that a spool has been assigned without server confirmation.

### 21.3 Synchronization

Data is refreshed:

- at application launch;
- whenever the application returns to foreground;
- after every successful mutation.

Bambuddy always wins when cached data differs from server data.

### 21.4 Server change

Changing the configured Bambuddy URL clears all cached domain data.

---

## 22. UI states and feedback

### 22.1 Processing

Immediately after an NFC scan, the application displays a clear processing state.

The UI must prevent the same tag intent from starting duplicated parallel assignments.

### 22.2 Success

A successful assignment displays a dedicated result screen containing:

- spool;
- printer;
- external slot;
- pending-configuration note when applicable;
- one primary action: “Done.”

Success feedback includes system vibration.

No success sound is required.

The result screen remains visible until the user selects “Done” or scans another tag.

“Done” returns to Home.

### 22.3 Error

After retries are exhausted, the application shows a full-screen error state containing:

- a plain-language reason;
- “Retry”;
- connection settings action when relevant;
- expandable technical details;
- copy technical details action.

Technical details include the request and response after secret redaction.

Error details are not persisted after the screen is dismissed.

---

## 23. NFC availability states

### 23.1 Device without NFC

The application remains installable and usable.

NFC features are marked unavailable.

Manual assignment and data viewing remain enabled.

### 23.2 NFC disabled

Home displays a nonblocking banner.

When the user initiates an NFC operation, the application offers a direct transition to Android NFC settings.

---

## 24. Accessibility

The application must support:

- TalkBack;
- semantic descriptions;
- meaningful focus order;
- non-color-only identification;
- keyboard and switch-compatible focus behavior where supported by the platform;
- readable light and dark themes.

There is no separate numeric acceptance requirement for 150% or 200% font scaling, but the application must avoid deliberate text-scale restrictions.

---

## 25. Localization and themes

The first version contains English UI only.

The project must use standard resource-based localization and remain compatible with Android per-app language support for future translations.

Light and dark themes follow the system setting.

No manual theme override is required.

---

## 26. Performance requirements

### 26.1 Assignment latency

Under normal local-network conditions, when the first request succeeds, the time from NFC read to verified assignment result must not exceed two seconds.

Retry scenarios are excluded from this target.

### 26.2 Cold launch

The application must display processing state within one second of an NFC-triggered cold launch.

### 26.3 Network timeout

- Connect timeout: 3 seconds.
- Total timeout per HTTP request: 10 seconds.

### 26.4 Process death

If the application process terminates during assignment:

- the operation is not persisted or resumed;
- the application does not automatically repeat it after restart;
- the next application launch reloads actual state from Bambuddy.

### 26.5 Data volume

No artificial product-level limit is imposed on snapshot size.

Lists, database queries and UI rendering must use paging, lazy rendering or indexed queries where required to avoid loading or composing the entire dataset unnecessarily.

---

## 27. Technical architecture

### 27.1 Core stack

- Kotlin Multiplatform.
- Compose Multiplatform.
- Decompose.
- Ktor Client.
- Kotlinx Serialization.
- Room KMP.
- DataStore.
- Metro dependency injection.

### 27.2 UI sharing

All product UI, navigation and presentation state reside in `commonMain`.

Platform-specific product screens are not permitted without a documented technical exception.

### 27.3 Navigation

Decompose manages:

- component lifecycle;
- child stacks;
- navigation state;
- state restoration;
- presentation components.

Navigation arguments must use common serializable types and must not expose Android framework classes.

### 27.4 State model

Features use unidirectional data flow:

- immutable state;
- explicit user intents;
- deterministic state reduction;
- side effects executed through injected services or repositories.

### 27.5 Platform abstraction

Platform capabilities are represented by small interfaces in shared code.

`expect/actual` is reserved for:

- platform factories;
- lightweight platform primitives;
- cases where interface injection would add no value.

Large platform services must not be implemented as large `expect/actual` classes.

### 27.6 NFC abstraction

Shared code owns:

- NFC payload model;
- URI parser;
- URI encoder;
- validation;
- NFC workflow state;
- assignment orchestration.

Android owns:

- NFC intent dispatch;
- `Tag` and `Ndef` access;
- NDEF read/write operations;
- transition to NFC system settings.

Future iOS implementation owns Core NFC integration while preserving the same URI and product flow.

### 27.7 Network engines

Ktor uses platform engines:

- Android-compatible engine for Android;
- Darwin engine for iOS compilation and future runtime.

TLS overrides remain platform implementations behind a common networking configuration contract.

### 27.8 Persistence

Room stores the application-relevant Bambuddy snapshot.

DataStore stores nonsecret settings.

API token storage is delegated to `SecureStorage`.

### 27.9 Modules

Use the minimum practical number of Gradle modules.

Recommended structure:

```text
:composeApp
  commonMain
  commonTest
  androidMain
  androidUnitTest
  androidInstrumentedTest
  iosMain
  iosTest

:iosApp
  Xcode shell application
```

Feature separation is enforced through package boundaries rather than separate Gradle modules.

New modules require a measurable reason such as:

- build-time isolation;
- platform boundary;
- independent artifact;
- dependency restriction that cannot be enforced reasonably through packages.

---

## 28. iOS preparation

The first version does not deliver a functional iOS product.

However:

- `iosArm64` must compile;
- `iosSimulatorArm64` must compile;
- shared tests must run on iOS Simulator;
- a minimal iOS shell must launch;
- the shell must render shared UI;
- platform services not yet implemented on iOS must use explicit mock implementations;
- common code must not depend on Android classes.

The future iOS product is expected to preserve the same NFC URI and overall spool-assignment flow using Core NFC.

---

## 29. Testing strategy

### 29.1 Business logic

All business logic must have automated tests.

No global line-coverage percentage is required.

Required coverage includes:

- URI parsing and encoding;
- invalid NFC payloads;
- printer resolution;
- slot resolution;
- already-assigned behavior;
- move confirmation;
- retries;
- assignment verification;
- pending configuration;
- cache synchronization;
- offline mutation blocking;
- connection changes;
- redaction.

### 29.2 KMP targets

Shared tests run on:

- JVM/Android;
- iOS Simulator.

### 29.3 Network tests

Ktor `MockEngine` is used with fixed Bambuddy request and response fixtures.

Fixtures must originate from real responses of the supported Bambuddy installation.

No real Bambuddy instance or container is required in CI.

### 29.4 NFC automated tests

Testing includes:

- fake common `NfcService`;
- URI and NDEF codec tests;
- Android instrumented tests with generated `NdefMessage` objects;
- Android component tests for NFC-intent routing.

### 29.5 Physical NFC tests

Before release, manually verify on real NTAG213 tags:

- blank tag;
- valid written tag;
- damaged or unsupported payload;
- cleared tag;
- overwrite;
- failed verification behavior;
- repeated scan;
- cold-launch scan.

### 29.6 UI tests

Compose Multiplatform UI tests cover:

- connection setup;
- Home states;
- standard NFC assignment;
- printer confirmation;
- slot selection;
- spool-move confirmation;
- tag linking;
- tag overwrite;
- offline state;
- final error state;
- success state;
- light and dark themes;
- critical TalkBack semantics.

---

## 30. CI/CD

### 30.1 Repository

The source repository is public on GitHub.

### 30.2 Workflow triggers

CI runs on every push and pull request.

### 30.3 Required CI jobs

Every CI run performs:

- Android compilation;
- iOS target compilation;
- JVM/common tests;
- iOS Simulator shared tests;
- Android Lint;
- Detekt static analysis.

Compiler warnings are displayed but do not fail the build.

### 30.4 Optimization

CI uses:

- Gradle caching;
- dependency caching;
- workflow concurrency cancellation for superseded commits.

Because the repository is public and uses standard GitHub-hosted runners, GitHub Actions quota is not considered a product risk.

### 30.5 Release process

APK creation and GitHub Release publication are manual.

The release APK is signed with a permanent local signing key stored outside the repository.

The application contains:

- `debug`;
- `release`;

build types only.

There is no automated release-note generation.

---

## 31. Privacy and observability

The application contains:

- no product analytics;
- no tracking;
- no remote crash reporting;
- no persistent local technical log;
- no background telemetry.

Contextual technical details exist only on the current error screen and are redacted before display or copy.

---

## 32. Release acceptance criteria

The first version is ready for release only when all conditions below are satisfied.

### 32.1 Functional completeness

All requirements in this PRD are implemented except full iOS platform functionality.

### 32.2 iOS readiness

- iOS targets compile.
- iOS Simulator tests pass.
- Minimal iOS shell launches.
- Shared UI renders.
- Mock platform services are available.

### 32.3 Automated verification

The following pass:

- Android build;
- iOS compilation;
- common unit tests;
- iOS Simulator tests;
- Android unit tests;
- Android Lint;
- Detekt;
- Compose UI tests;
- Android instrumented tests.

### 32.4 Manual regression

A complete regression checklist is executed, covering:

- initial setup;
- valid and invalid connection;
- HTTP warning;
- TLS-verification override;
- server URL change;
- one-printer flow;
- multiple-printer flow;
- default-printer flow;
- multiple external slots;
- valid tag;
- empty tag;
- invalid tag;
- deleted spool;
- overwrite;
- clear;
- repeat scan;
- spool assigned elsewhere;
- API timeout;
- HTTP 4xx;
- HTTP 5xx;
- retry exhaustion;
- pending configuration;
- offline viewing;
- process restart;
- manual assignment;
- device without NFC;
- disabled NFC;
- TalkBack;
- light theme;
- dark theme.

### 32.5 Physical NFC matrix

The full NTAG213 matrix passes on at least one supported physical Android device.

### 32.6 Defects

There are no known Critical or High severity defects.

### 32.7 Release artifact

A signed release APK is published in GitHub Releases.

---

## 33. Key product invariants

The following rules must not be weakened during implementation:

1. One Bambuddy instance only.
2. Bambuddy is the only source of truth.
3. No offline mutation queue.
4. One NFC scan and no more than one confirmation in the primary flow.
5. Assignment success requires verification of the exact target slot and spool.
6. No automatic tag payload recovery.
7. No hidden spool move between printers.
8. NFC tags remain rewritable.
9. All product UI remains in shared Compose Multiplatform code.
10. Android framework types never enter the common domain or presentation model.
11. No analytics, crash reporting or persistent diagnostic logging.
12. Critical and High defects block release.

---

## 34. Accepted risks and architectural debt

### 34.1 Unversioned NFC URI

Existing tags may require explicit legacy parsing if the format changes.

### 34.2 Numeric spool identifier

Changing Bambuddy instances may cause tag-ID collisions.

### 34.3 HTTP support

API tokens may be transmitted without encryption after explicit user approval.

### 34.4 Disabled TLS verification

The configured host may be vulnerable to man-in-the-middle attacks after the user enables the override.

### 34.5 No API capability validation

Bambuddy incompatibility may be detected only when a feature is used.

### 34.6 Direct POST retry

An assignment request may be resent after the server processed an earlier request but the client did not receive its response.

The expected final state remains stable because assignment uses slot upsert semantics.

### 34.7 Mock-only API testing

CI does not automatically detect breaking changes introduced by a new Bambuddy version.

### 34.8 No persistent diagnostics

Failures that cannot be reproduced immediately may be harder to investigate.

These risks are consciously accepted for the personal self-hosted product context.