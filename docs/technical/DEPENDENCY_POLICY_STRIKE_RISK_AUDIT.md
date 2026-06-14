# PocketCraft Dependency and Policy Strike Risk Audit

Date: 2026-04-05

Scope: Android app dependencies and policy-sensitive implementation paths.

## Summary

No direct evidence of malware/ad fraud SDKs was found. Highest policy risk is privacy disclosure mismatch and consent handling around analytics/ads/push data processing.

## Observed SDKs and Services

From app/build.gradle.kts and app code:

- Firebase Analytics
- Firebase Firestore
- Firebase Cloud Messaging
- Firebase Remote Config
- Firebase In-App Messaging
- Google Mobile Ads (AdMob)
- Networking stack (OkHttp/Retrofit)

Also present in version catalog but not currently wired in app module/plugin:

- Firebase Crashlytics library alias and plugin alias exist in libs.versions.toml

## Potential Strike or Rejection Risks

### High

- Missing or inaccurate privacy disclosures for Analytics, FCM, Firestore, Remote Config, and AdMob.
- Data Safety form mismatch with actual runtime behavior.
- Ad/analytics processing before user consent in regulated regions.

### Medium

- usesCleartextTraffic is enabled in AndroidManifest.xml while relay registration endpoints use HTTP.
- Feedback payload includes device metadata and server log excerpt; this must be disclosed.
- Push topic subscription behavior requires clear disclosure and user controls.

### Low

- Open-source license notice completeness not yet verified for all transitive dependencies.

## Implemented Mitigations in This Change

- Added consent toggles in Settings for analytics and ads.
- Added push announcements preference toggle.
- Gated Firebase Analytics collection by consent at startup.
- Gated AdMob ad loading in BannerAdBox by ad consent.
- Added in-app legal links and legal policy version surface.
- Added legal prelaunch checklist and policy drafts.

## Remaining Actions Before Launch

- Publish final Privacy Policy and Terms URLs, then update BuildConfig URLs.
- Complete Google Play Data Safety declaration with exact data map.
- Verify consent UX for all regions you serve.
- Decide on cleartext relay transport hardening roadmap.
- Run license compliance audit and produce third-party notices.
- Legal review of jurisdiction-specific clauses.

## Data Collection Map (Current)

- Analytics events: app usage/server lifecycle fields via Firebase Analytics manager.
- Feedback flow: message text, generated user ID, device metadata, log excerpt to Firestore.
- Push flow: FCM token/topic subscription for announcements.
- Remote Config/Firestore: broadcast content fetch/display.
- Relay registration: userId and local relay metadata to relay control endpoints.

## Legal Note

This audit is technical and operational, not legal advice.
