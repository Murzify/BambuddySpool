# Data-Layer Security Review

**Date:** 2026-07-14
**Scope:** `[DATA]-[013]` data, network, persistence, settings, security, and NFC common code
**Result:** No open Critical or High findings

## Method

The review inspected storage boundaries, Room entities and transaction write sets, synthetic contract fixtures,
DTO mapping, response body handling, URL metadata parsing, secret wrappers, snapshot generation handling, and
NFC parsing. It also ran the repository policy gate. Private local configuration was not read and no Bambuddy
request was made.

## Findings

| ID | Severity | Finding | Resolution and evidence |
|---|---|---|---|
| DATA-SEC-001 | High | A server can declare an oversized successful response before the body reader sees its bounded stream. | Fixed in `KtorBambuddyRepository`: a declared `Content-Length` above the endpoint limit is rejected before body allocation. Unknown-length responses retain the existing `limit + 1` bounded read and negative oversized-body test. |
| DATA-SEC-002 | High | A malicious NFC URI had no size limit before parsing and substring creation. | Fixed in `CanonicalNfcPayloadCodec`: inputs over 256 characters fail closed as `PayloadTooLarge`; a negative test proves the boundary. |
| DATA-SEC-003 | Medium | Canonical URL metadata accepted host characters that are not valid DNS/IP authority characters. | Fixed in `parseCanonicalBaseUrl`: URL, base-path, host, and label bounds are enforced; unsafe/oversized hosts are rejected before persistence or request construction. |
| DATA-SEC-004 | Medium | Room itself cannot repair a physically corrupted database. | Accepted platform behavior. The application writes only validated domain data through one Room transaction, uses foreign keys and the unique assignment-slot index, and treats invalid server snapshots as non-publishable. Platform-level corruption recovery and encrypted secure storage remain `[SEC]-[001]` work. |

## Verified Controls

| Area | Control |
|---|---|
| Token storage and lifetime | `SecretValue` has no public plaintext accessor or serializable representation. The token is accepted only by `SecureTokenStore` and is exposed only within the trusted request/validation boundary. DataStore field names explicitly exclude token storage. |
| URL metadata | Base URLs reject userinfo, query, fragments, invalid schemes, invalid ports, unsafe hosts, and unbounded metadata. Origin metadata is canonicalized separately from base paths. |
| Network input | DTO mapping uses strict configured JSON, typed incompatible-response failures, positive IDs, bounded slot coordinates, and bounded successful response reads. Fixtures are synthetic and contract checks are local. |
| SQL and snapshot integrity | Room foreign keys and unique slot assignment constraints protect relational references. `SnapshotPublication` is the complete atomic write set; generation equality prevents stale publication and `nextAfter` rejects overflow. |
| NFC input | Only the canonical spool URI is accepted. Unknown, malformed, non-decimal, overflow, query/fragment, multi-record, and oversized payloads fail closed. |
| Fixture privacy | Contract fixtures use synthetic provenance and the repository policy gate rejects private configuration and sensitive artifacts. |

## Residual Work

- Keystore-backed token persistence and backup/invalidation tests are owned by `[SEC]-[001]`.
- Redirect, HTTP consent, and TLS override enforcement are owned by `[SEC]-[002]`.
- Structural diagnostic redaction and mutation integrity are owned by `[SEC]-[003]` and `[SEC]-[004]`.
