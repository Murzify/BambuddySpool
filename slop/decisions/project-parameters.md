# Project and Release Parameters

**Decision date:** 2026-07-12
**Decision owner:** Project owner (Murzify)
**Scope:** `[INIT]-[001]`

This file is the authoritative resolution record for the product and release parameters that were open in `slop/TECHSPEC.md`. The backlog originally names `docs/decisions/project-parameters.md`; repository-level `AGENTS.md` instead requires agent-facing decisions under `slop/`, so this file uses `slop/decisions/`.

## Decision register

| Parameter | Status | Value or evidence | Owner | Deadline | Impact |
|---|---|---|---|---|---|
| Supported physical Android device | Confirmed | HONOR 50 (`NTH-NX9`), Android 13 / API 33, build `NTH-N29 7.1.0.345(C10E1R5P1)`; Android NFC features were confirmed through ADB. The NFC chipset is unknown. | Project owner (Murzify) | Resolved 2026-07-12 | This is the release acceptance device. The unknown chipset is recorded rather than inferred and does not block testing because NFC capability is confirmed. |
| Additional Android environment | Confirmed, non-acceptance | Existing `Medium_Phone_API_36.0` emulator. It has no physical NFC acceptance role. | Project owner (Murzify) | Resolved 2026-07-12 | May be used for CLI-driven UI, compatibility, and regression checks; it cannot satisfy physical NFC acceptance. |
| Android application ID | Confirmed | `com.murzify.bambuddyspool` | Project owner (Murzify) | Resolved 2026-07-12 | Retains the current Gradle identifier and avoids an unnecessary package migration. The earlier `ru.nonamee.bambuddyspoolmanager` value was a proposal in an open question, not an accepted invariant, so no ADR is required. |
| Public repository | Confirmed | GitHub owner `Murzify`, repository `BambuddySpool` (`Murzify/BambuddySpool`) | Project owner (Murzify) | Resolved 2026-07-12 | Release documentation, source links, and GitHub Releases must use these coordinates. |
| License | Confirmed | Mozilla Public License 2.0 (`MPL-2.0`) | Project owner (Murzify) | Resolved 2026-07-12 | Source distribution and dependency/license review must remain compatible with MPL-2.0. A canonical root license file remains release-repository work if not already present. |
| Known A1 external slot | Confirmed for v1 | Bambu Lab A1 external mapping: `ams_id=255`, `tray_id=0` | Project owner (Murzify) | Resolved 2026-07-12 | A1 implementation and acceptance may proceed. The mapping must remain isolated in the topology resolver. |
| Sanitized multi-external-slot fixture | Unavailable; release blocker for multi-slot support claim only | Bambuddy virtual printers are not exposed through the relevant API, so no factual fixture is available. No coordinate mapping may be invented. | Project owner (Murzify) | Milestone deadline: before claiming support for any multi-external-slot printer | Does not block the known A1 flow. Unknown or multi-slot topology must fail closed and must not be advertised as supported until sanitized evidence or source-derived mapping rules are reviewed. |

## Release interpretation

- V1 acceptance is scoped to the confirmed Bambu Lab A1 external slot and the HONOR 50 physical Android device.
- Physical NFC acceptance still requires at least three NTAG213 tags and the complete protocol in `[TEST]-[008]`.
- API 23 remains the minimum supported Android baseline; the declared API 33 device is the primary physical acceptance device, not the minimum-API compatibility proof.
- Multi-slot domain and UI design may remain generic, but mutation is prohibited for an unresolved topology.
- The private local Bambuddy instance may be used only under `AGENTS.md`: CI remains synthetic, private values and exports are never committed, and mutation requires explicit operation-specific authorization.

## ADR assessment

No ADR is introduced by this task:

- retaining `com.murzify.bambuddyspool` resolves a proposed open value and does not weaken a non-negotiable invariant;
- limiting factual v1 topology support to the known A1 mapping applies existing ADR-005 (known slot topology only) and its fail-closed rule;
- the device, repository, and license choices are product/release parameters rather than architecture changes.

## Remaining release blockers

| Blocker | Owner | Milestone deadline | Release effect |
|---|---|---|---|
| Complete physical NTAG213 acceptance evidence on the declared HONOR 50 | Project owner (Murzify) | Before release acceptance | Blocks the v1 release until `[TEST]-[008]` passes; it does not block implementation. |
| Obtain and review sanitized evidence before claiming support for a multi-external-slot printer | Project owner (Murzify) | Before any multi-slot support claim | Blocks only that support claim; A1 remains supported. |
