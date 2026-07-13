# DATA-001 Completion Report

## Outcome

The repository now contains a deliberately small public contract for the eight Bambuddy 0.2.4.7 operations required by the application. Its machine-readable manifest records method, path, `X-API-Key` authentication, path/query/body request inputs, client-used response fields with types and nullability, decompressed response/error limits, and the fixture associated with each operation.

Each operation has minimal-valid, representative-valid, and incompatible JSON. List and object fixtures preserve the confirmed endpoint envelopes. Representative fixtures exercise documented nullable fields and an unknown additive field. Incompatible fixtures remain parseable JSON data tagged for the later `IncompatibleApiResponse` mapping work in `[DATA]-[004]`; this task does not introduce DTOs or mapping behavior.

The private owner-provided OpenAPI contract was consulted locally in read-only mode to confirm structural facts that TECHSPEC does not reproduce. Only a sanitized structural projection was inspected. The export was never saved or committed, no live endpoint response was needed, and no mutation request was sent.

## Sanitization Boundary

All fixture values were authored from scratch. They contain only synthetic IDs, timestamps, labels, colors, and names. They contain no host, URL, API token, printer serial number, IP address, access code, tag identifier, personal identity, or inventory record.

The printer-list fixture is intentionally the application's client-used projection. Bambuddy's raw printer response has additional required server fields that include sensitive device/network material. Those fields are listed only as excluded schema names in the manifest and are absent from fixture payloads; future transport DTOs must ignore them rather than model or retain them.

## Definition of Done

- [x] Only the eight mandatory TECHSPEC operations are present.
- [x] The manifest records method, path, authentication, request inputs, used fields, and known limits.
- [x] Used fields include confirmed wire type and nullability.
- [x] Every operation has minimal-valid, representative-valid, and incompatible synthetic JSON.
- [x] Representative fixtures contain documented nullable values and an unknown additive field.
- [x] Fixtures state Bambuddy OpenAPI 0.2.4.7 provenance and remain compact/reviewable.
- [x] Host tests enumerate operations/categories, parse every JSON document, and preserve incompatible cases as data.
- [x] Tests and repository policy reject private URLs/IP addresses and credential/device/tag fields in payloads.
- [x] No private OpenAPI export or live response was retained.

## Verification

```text
./gradlew spotlessCheck detekt \
  :shared:testAndroidHostTest \
  :shared:compileKotlinIosArm64 \
  :shared:iosSimulatorArm64Test

./ci/verify-repository.sh
git diff --check
jq empty shared/src/commonTest/resources/contracts/bambuddy/0.2.4.7/*.json
```

The Gradle matrix completed successfully. The host run executes the common tests plus the contract/privacy tests; both native targets remain compatible. Every manifest/fixture document parses as strict JSON, and the repository supply-chain/privacy baseline passes.

## Limitations and Follow-up Ownership

TECHSPEC specifies no decompressed response limit for the printer list or targeted spool detail operation. Their manifest limits are therefore `null` with an explicit note instead of inheriting or inventing a number. `[DATA]-[007]` must resolve and enforce those two limits before production networking is complete.

These fixtures define the sanitized input evidence but do not prove DTO rejection yet. Strict DTO/domain validation and `IncompatibleApiResponse` classification belong to `[DATA]-[004]`. The fixture scanner is a narrow repository baseline, not a substitute for the later authoritative dependency, secret, or release artifact gates.
