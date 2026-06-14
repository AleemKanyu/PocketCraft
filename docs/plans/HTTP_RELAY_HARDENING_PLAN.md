# PocketCraft HTTP Relay Hardening Plan

Date: 2026-04-05
Status: Required before release-candidate cut

## Why this is needed

Current audit shows relay control traffic includes HTTP paths while privacy-sensitive identifiers are transmitted. This can become a Play policy and security risk.

## Decision

Adopt HTTPS-only relay transport with phased fallback removal.

## Plan

1. Server transport baseline
- Stand up TLS termination for relay endpoints (443).
- Enforce HSTS and redirect all HTTP requests to HTTPS.
- Pin modern TLS config (TLS 1.2+).

2. Android client changes
- Replace cleartext relay base URLs with HTTPS URLs.
- Remove or tighten cleartext allowance in AndroidManifest (set usesCleartextTraffic=false unless strictly required for local loopback cases).
- Add network security config only for narrowly scoped exceptions.

3. Runtime resilience
- Add retry/backoff logic for TLS handshake/network failures.
- Surface actionable user messages for certificate/network errors.

4. Observability
- Add metrics for relay success/failure by endpoint and error class.
- Add alerting for TLS failure spikes.

5. Rollout
- Stage rollout to internal, then closed test track.
- Validate latency/regression versus current HTTP path.
- Remove HTTP fallback completely after stable rollout window.

## Acceptance Criteria

- No relay control payload is sent over HTTP in production builds.
- Android manifest does not allow broad cleartext traffic.
- QA confirms successful server start/register/stop across network conditions.
- Privacy policy wording updated to reflect transport security posture.

## Owner Checklist

- [ ] TLS endpoint ready in relay infrastructure
- [ ] App URLs migrated to HTTPS
- [ ] Manifest cleartext policy hardened
- [ ] Regression tests passed on device
- [ ] Documentation and Data Safety sheet updated
