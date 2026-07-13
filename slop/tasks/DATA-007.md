# [DATA]-[007] Implement Bounded Ktor Repositories

Status: Completed on 2026-07-13.

Implemented scope:

- Added a bounded Ktor repository for the mandatory Bambuddy API subset.
- Added `X-API-Key` credential injection through the existing non-printing `SecretValue` boundary.
- Added endpoint URL construction with Ktor URL builders, including configured reverse-proxy base paths.
- Added Android OkHttp and iOS Darwin engine factories with redirects disabled and 3-second connect / 10-second request timeouts.
- Added typed network failures for 4xx, 5xx, unexpected status, transport, contract, TLS/security policy, missing credentials, and oversized responses.
- Added endpoint-specific decompressed response limits before DTO parsing.
- Added explicit security-policy hooks for initial requests and future redirect validation.
- Added MockEngine tests for every mandatory operation, credential redaction behavior, base paths, oversized responses, and distinct failure classes.

Notes:

- The repository does not follow redirects automatically. Future SEC-002 work can implement policy-approved manual redirect handling through the exposed security-policy hook.
- No live Bambuddy instance or private credentials were used.
