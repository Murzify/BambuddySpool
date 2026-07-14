# CODE-013 — Performance and Graceful-Degradation Hooks

## Scope

- Add process-local monotonic timing hooks for assignment stages and NFC cold-launch Processing composition.
- Keep the hooks explicitly non-persistent and unsuitable for analytics or mutation decisions.
- Encode TECHSPEC 16.7 as an executable, fail-closed graceful-degradation policy.
- Enable Android StrictMode only in debug builds for main-thread I/O and resource-leak detection.
- Retain isolation of non-essential haptic and clipboard failures from verified assignment results.

## Explicit non-goals

- No telemetry, analytics, crash reporting, diagnostic persistence, or production timing export.
- No worker, wake lock, background queue, polling, monitor, or circuit breaker.
- No private-instance access or mutation.

## Verification

- Common tests cover timing monotonicity, one-shot cold-launch measurement, policy outcomes, and verified-outcome secondary-effect isolation.
- Android build checks compile the debug and release StrictMode source-set implementations.
