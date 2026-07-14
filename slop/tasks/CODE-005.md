# CODE-005 Completion Report

## Summary

Implemented the common, fail-closed assignment preflight and initial mutation boundary. Manual and NFC entry share
the immutable `AssignmentIntent` input; no intent, command, authorization, or in-flight operation is persisted.

## Delivered

- Added `AssignmentOrchestrator` with a pure, inspectable `AssignmentPreflight` decision model for ready,
  already-assigned, confirmation-required, and typed blocked outcomes.
- Refreshes the selected spool, printer list, selected printer status, and assignments before considering a mutation.
  A required freshness gate rejects stale, offline, authentication-invalid, refresh-failed, or generation-mismatched
  state before any request or POST.
- Resolves current topology from the fresh status. A disappeared/changed selected slot, AMS slot, unsupported
  topology, offline printer, duplicate slot assignment, or missing target printer returns a typed failure and never
  posts.
- Enforces every zero-confirmation condition. Multiple printers, multiple external slots, and every known move
  conflict become one typed combined-confirmation requirement. A fresh exact assignment returns `AlreadyAssigned`
  without a POST.
- Captures one immutable `AssignmentCommand` before scheduling the POST. The POST is application-scoped after it is
  scheduled, while preflight cancellation remains cooperative. No command is stored or replayed after process death.
- Keeps the HTTP response fail-closed: it returns `VerificationRequired`, never success. CODE-007 owns retry and
  exact assignment polling/verified success.

## Verification

```text
./gradlew :shared:testAndroidHostTest --tests 'com.murzify.bambuddyspool.core.assignment.AssignmentOrchestratorTest'
```

The deterministic coroutine tests cover stale/offline rejection before reads or POST, unsupported/changed topology,
combined confirmation, idempotent already-assigned handling, immutable command capture, HTTP-success fail-closed
handling, and caller cancellation after application-scoped POST scheduling. No private configuration was read and no
Bambuddy request was made.

## Definition of Done

- [x] Decisions are immutable and presentation-independent; network effects are isolated behind repository/poster
  boundaries.
- [x] Fresh spool/status/assignments and topology validation run before mutation; changed slots return a typed failure
  without POST.
- [x] Zero-confirmation posting is restricted to every available TECHSPEC safety condition.
- [x] Commands are immutable, transient, and never persisted, queued, or replayed.
- [x] Pre-POST cancellation is cooperative; scheduled POST work is application-scoped.
- [x] Stale, offline, unsupported, inconsistent, and confirmation-required states never POST.

## Follow-up Ownership

- CODE-006 renders the one combined confirmation surface from `AssignmentPreflight.ConfirmationRequired`.
- CODE-007 replaces the initial-poster boundary with retry and exact verification; only its verified result may become
  an assignment success.
