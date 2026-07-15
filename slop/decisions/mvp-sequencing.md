# MVP Sequencing Decision

**Status:** Accepted by owner on 2026-07-15

## Decision

The Stage 4 `[TEST]-*` completion matrix is deferred as release hardening so that a minimal, read-only
connection proof of concept can be assembled earlier. This does not weaken the product invariants or release
criteria in `slop/PRD.md` and `slop/TECHSPEC.md`.

MVP work is ordered as follows:

1. Wire the existing shared Setup/Settings form into the production root and retain only non-secret connection
   presentation state.
2. Implement `[SEC]-[001]` before any token can be retained beyond the immediate form action.
3. Implement `[SEC]-[002]` before any connection attempt. Redirect, HTTP-consent, and TLS policy enforcement must
   exist below the UI.
4. Only then perform the owner-authorized, read-only smoke flow: setup, validation, save, initial sync, and cached
   Spools/Printers viewing.

Assignment POSTs, NFC tag writes, overwrite, and clear remain disabled throughout this MVP sequence. No private
instance request is made as part of MVP implementation or automated tests.

## Consequences

- `[TEST]-[001]` through `[TEST]-[012]` remain open and are required before release.
- `[SEC]-[001]` and `[SEC]-[002]` are explicit security gates, not deferred work that UI can bypass.
- Until both gates are complete, the production form must explain that connection validation and saving are blocked;
  it must neither send a request nor retain the entered token.
- The non-secret URL/settings presentation and empty-cache integration may be wired now, but they do not claim a
  usable connection or synchronization path.
