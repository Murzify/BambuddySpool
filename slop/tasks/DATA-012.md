# DATA-012 - Deduplicate Validation and Mapping Logic

**Status:** Completed
**Branch:** `task/data-012`

## Scope

Consolidate only duplicated data-layer invariants while retaining the existing domain, network, settings, NFC, and Room boundaries.

## Delivered

- Added the raw database-coordinate overload of `SlotKey.from`. Network DTO validation and Room projections now use the same printer-ID and coordinate validation path as all domain slot identity.
- Added `SnapshotGeneration.nextAfter` so snapshot generation increment and overflow behavior have one definition.
- Replaced the positional Room snapshot publish parameter set with `SnapshotPublication`, keeping the expected-generation guard and complete atomic write-set together.
- Retained the separate canonical parsers for Bambuddy base URLs and NFC payloads. Their grammars and typed errors differ; a shared generic parser would add coupling without removing duplicated behavior.

## Verification

- Added behavior-focused tests for raw `SlotKey` identity, snapshot generation overflow, and stale/complete ordered Room snapshot publications.
- Ran focused common tests, formatting, static analysis, and iOS compilation before push.
