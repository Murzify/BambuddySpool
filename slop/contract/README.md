# Sanitized Bambuddy 0.2.4.7 Contract

This directory documents the public, manually maintained subset of the Bambuddy API required by the application. The machine-readable manifest and synthetic JSON fixtures live under `shared/src/commonTest/resources/contracts/bambuddy/0.2.4.7` so contract tests and later DTO mapping tests consume the same evidence.

## Provenance and sanitization

The endpoint list, client policy, request limits, and assignment semantics come from `slop/TECHSPEC.md`. On 2026-07-13, the private owner-provided Bambuddy OpenAPI 0.2.4.7 contract was consulted locally in read-only mode to confirm mandatory response envelopes, field names, required fields, types, and nullability. The full export was not retained, copied, or committed. No live endpoint response was required.

Fixtures were written from scratch with synthetic IDs, timestamps, labels, and colors. They contain no private host, token, printer serial number, IP address, access code, tag identifier, user identity, or personal inventory record. The printer fixture is intentionally a client-used projection: sensitive server fields that the application does not consume are omitted and must remain unknown to transport DTOs.

## Scope

Only the eight operations listed in TECHSPEC section 4.2 are represented. The two assignment GET forms share a response schema but remain separate manifest operations because their requests and synchronization roles differ. No create/edit spool, printer mutation, unassign, or unrelated Bambuddy operation belongs in this subset.

The fixture categories are:

- `minimal_valid`: the smallest payload accepted by the planned client projection;
- `representative_valid`: a synthetic payload with documented nullable fields and an unknown additive field;
- `incompatible`: parseable JSON that the later strict DTO/domain mapper must classify as `IncompatibleApiResponse`.

The incompatible fixtures are data evidence only. DTO validation and domain mapping belong to `[DATA]-[004]`.
