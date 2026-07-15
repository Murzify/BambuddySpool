# Feature Security Review

**Date:** 2026-07-15
**Scope:** `[CODE]-[017]` feature paths through `task/code-016`
**Result:** No open Critical or High findings

## Method

The review traced assignment and tag-mutation authorization from shared presentation state to their lower-level
boundaries, then inspected Android NFC entry and NDEF write code. It also inspected saved-state registration,
clipboard contracts, and the existing deterministic tests. No private configuration was read and no Bambuddy
instance was contacted.

## Findings and disposition

| ID | Severity | Finding | Disposition | Evidence |
|---|---|---|---|---|
| FEATURE-SEC-001 | High | Two concurrent callers could both complete preflight before either POST became visible. | Fixed. `DefaultAssignmentOrchestrator` now gives one process-local mutex ownership of the full application-scoped POST/verification boundary. A competing request fails typed as `MutationInProgress`; cancellation cannot release the lock before the owned operation completes. | `AssignmentOrchestrator.kt`; `concurrentAssignmentCannotStartADuplicatePostWhileFirstMutationOwnsBoundary` |
| FEATURE-SEC-002 | High | Two concurrent confirmation deliveries could call the same physical tag writer. | Fixed. `TagMutationWorkflowController` serializes validation and physical I/O with a process-local mutex. The competing delivery fails typed as `OperationInProgress`; it cannot perform a second write. | `TagMutationWorkflow.kt`; `concurrentConfirmationCannotWriteTheSameAuthorizedTagTwice` |
| FEATURE-SEC-003 | High | An explicit Android Intent could imitate `ACTION_NDEF_DISCOVERED` and a canonical data URI without proving a matching NDEF record. | Fixed. Android entry now requires a non-empty framework `Tag`, exactly one framework NDEF message, and an independently decoded single canonical URI equal to the Intent data URI. Missing, multiple, malformed, or mismatching evidence is ignored before common state changes. | `AndroidNfcIntentAdapter.kt`; `missingOrMismatchedFrameworkNdefEvidenceCannotSpoofAScan` |

## Invariant review

| Area | Result |
|---|---|
| Authorization lifetime and recreation | Assignment intents, confirmations, NFC observations, tag-mutation authorization, retry data, and mutex ownership are process-local and absent from StateKeeper/navigation serializers. Process recreation cannot replay an operation. |
| Wrong-tag writes | Android mutation checks the expected tag fingerprint before NFC connection. Retries require a same-fingerprint reread; a different tag fails closed. |
| Duplicate POST | The new orchestrator ownership guard covers concurrent manual and NFC callers. Existing immutable retry policy uses one command, bounded retry delays, and never verifies before a retry. |
| Offline and stale mutation | Freshness gates execute before assignment preflight; tag mutation validates authentication and selected spool with GET-only calls before physical I/O. Blocked validation performs no POST or write. |
| Move and target replacement | Fresh assignment context detects move/replacement conflicts and requires one combined confirmation. Already-assigned exact targets are idempotent and do not POST. |
| Stale topology and server verification | Fresh printer/status/assignment reads, supported-slot validation, generation checks, and exact post-POST assignment polling are lower than UI. Duplicate slot state, changed topology, and HTTP-only success fail closed. |
| Clipboard and details | Clipboard is only an injected `copyRedacted` boundary; success feedback neither copies automatically nor changes a verified result when clipboard/haptic feedback fails. No technical detail is produced by this feature path. Full redaction implementation remains `[SEC]-[003]`. |
| NFC injection and intent spoofing | The manifest has one NDEF-discovery filter and `singleTop`. The adapter now additionally verifies framework tag/NDEF evidence and canonical equality before it creates a shared observation. |

## Adversarial and race coverage

- Assignment concurrent POST ownership, caller cancellation after POST scheduling, retry identity, exact verification,
  stale/offline rejection, changed/unsupported topology, duplicate server slots, and no-POST idempotency.
- Tag concurrent confirmation ownership, wrong-fingerprint retry rejection, offline/deleted validation rejection,
  overwrite/clear confirmation, and unverified physical outcomes.
- Android NDEF entry rejection for absent/mismatched evidence, unrelated scheme, leading-zero/noncanonical URI, and
  non-NDEF action; manifest resolution and `singleTop` remain device-test coverage.

## Residual scope and ownership

The review found no unresolved Critical or High defect in the implemented feature boundaries. The application still
has planned release gates outside this task: complete Android NFC shell coverage is `[TEST]-[004]`, technical-detail
redaction is `[SEC]-[003]`, and the deeper mutation-integrity audit is `[SEC]-[004]`. These are not treated as
implemented by this review.
