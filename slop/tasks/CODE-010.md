# CODE-010 Completion Report

## Outcome

Android now has a fail-closed, Android-only NDEF mutation primitive. It writes one canonical Bambuddy spool URI,
then closes and reopens the NFC technology for an independent read-back; platform write completion alone cannot
produce success.

## Delivered

- Added `AndroidNdefTagMutator`, which accepts only the previously authorized tag fingerprint and a canonical
  application URI. A non-matching or missing fingerprint is rejected before any NFC connection or mutation.
- Supported writable `Ndef` tags and `NdefFormatable` tags. Writable NDEF capacity is checked before the write;
  Android exposes no pre-format capacity for `NdefFormatable`, so platform format failure is explicitly uncertain
  rather than reported as success.
- Enforced exactly one `NdefRecord.createUri` payload. Read-back decodes the URI through the common canonical codec
  and requires exactly one valid Bambuddy record, so byte-level URI compression differences do not affect valid
  verification.
- Classified read-only, unsupported, insufficient-capacity, different-tag, removed-before-write, removed-after-write,
  reread failure, verification interruption, and verification mismatch outcomes with existing typed domain models.
- Kept all Android NFC framework types in `androidApp`. The primitive runs NFC I/O on `Dispatchers.IO`, offers no
  automatic physical-write retry, and has no irreversible read-only/locking API.
- Added generated `NdefMessage` Android device tests for single-record encoding, canonical decoding, URI
  normalization, multi/non-URI rejection, and pre-write capability/fingerprint rejection.

## Verification

```text
./gradlew spotlessApply :androidApp:connectedDebugAndroidTest :shared:testAndroidHostTest detekt :androidApp:lintDebug
./ci/verify-repository.sh
git diff --check
```

All commands passed. The Android device test suite ran successfully on both the API 36 emulator and the connected
Android 13 acceptance device. No private configuration was read and no Bambuddy request was made.

## Definition of Done

- [x] Writable NDEF and NDEF-formatable paths are supported without an irreversible locking surface.
- [x] Writable-NDEF capacity and read-only state are rejected before mutation; format-only capacity uncertainty is
  never represented as success.
- [x] Only one URI record is written and read-back success requires independent canonical URI verification.
- [x] Different fingerprints, overflow, read-only, unsupported, lost-tag, uncertain, and interrupted verification
  outcomes are fail-closed and typed.
- [x] Generated Android `NdefMessage` tests cover capability preflight and record verification.

## Follow-up Ownership

`[CODE]-[011]` owns user authorization, link/overwrite/clear state machines, fresh server validation, and wiring
this primitive into shared mutation workflows. It must retain this primitive's expected-fingerprint boundary and
must not add automatic physical-write retries.
