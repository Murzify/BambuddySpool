# ADR-011 — Durable fail-closed connection replacement

**Status:** Accepted

## Decision

Connection replacement is a recoverable durable operation. Before changing a configured connection, persistence writes
a pending replacement record containing the prior non-secret settings and a generated operation identifier. The
platform secure-token store and Room snapshot store participate through an implementation-owned transaction boundary.
The boundary commits new settings/token, clears the snapshot, marks the operation committed, and schedules initial
sync. A failure or cancellation leaves the operation pending; startup recovery restores the prior settings/token and
snapshot when commit is incomplete, or completes cache clearing before exposing a committed replacement.

## Rationale

DataStore, SecureTokenStore, and Room have independent durability and cannot safely be composed by sequential calls.
The product invariant requires no observable old/new mixed connection state.

## Alternatives rejected

- Sequential writes with best-effort compensation: cache clearing is destructive and cancellation can interrupt
  compensation.
- Clearing cache first: failure can erase valid old-instance cached viewing.
- Treating initial-sync scheduling failure as success: produces a false committed-success surface.

## Recovery semantics

Recovery runs before connection state is exposed. Pending operations fail closed: mutations remain unavailable and
the application reports recovery failure rather than using mixed state. Cancellation is handled in a non-cancellable
recovery section while preserving the original cancellation/failure for the caller.

## Consequences

Platform persistence adapters must implement the transaction boundary and deterministic fault-injection tests cover
settings, token, snapshot, commit-marker, scheduling, rollback, and cancellation stages.
