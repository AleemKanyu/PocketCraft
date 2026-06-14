# PocketCraft Privacy Policy (Draft)

Effective date: 2026-04-06  
Version: 2026-04-06

PocketCraft ("we", "us", "our") provides a mobile app for hosting and managing a Minecraft server. This policy describes what data we collect, how we use it, and your choices.

This draft should be reviewed by qualified counsel before release.

## 1. Data We Collect

### 1.1 Data you provide

- Feedback text you submit in the app.
- Optional social/community actions (for example, opening Discord/Instagram links).

### 1.2 Data collected automatically

- App and device metadata:
  - App version and version code
  - Android SDK version
  - Device manufacturer and model
- Operational identifiers:
  - A generated app user ID stored locally on device
  - Relay session identifier derived from that local ID
- Server/hosting operational data:
  - Relay host selection
  - Relay registration payload fields (for example userId, local host/port)
  - Server telemetry events you trigger in-app (for example start/stop, settings changes)

### 1.3 Firebase and Google SDK data

PocketCraft includes Google Firebase and Google Ads SDKs. Depending on your consent settings, these SDKs may process:

- Firebase Analytics:
  - Event names and event parameters related to app/server usage
  - Firebase identifiers and device/app metadata
- Firebase Cloud Messaging (FCM):
  - Push token and topic subscriptions used to deliver announcements
- Firebase Remote Config:
  - Config fetch requests and app context used to serve remote values
- Firebase Firestore:
  - User-submitted feedback payloads and metadata
- Google Mobile Ads (AdMob):
  - Ad request metadata and identifiers required to serve ads

### 1.4 Crash and diagnostics data

- PocketCraft includes optional Firebase Crashlytics integration for crash diagnostics.
- When enabled by user consent, crash reports can include stack traces, app state metadata, and technical diagnostics needed to investigate bugs.
- PocketCraft can also process diagnostics through:
  - In-app feedback log excerpts (for example latest server log snippets)
  - Error information included in analytics-style events where enabled

## 2. How We Use Data

We use data to:

- Provide core app functionality (hosting workflow, relay registration, updates).
- Improve reliability and performance (diagnostics, troubleshooting, product analytics when consented).
- Deliver push announcements and configuration updates.
- Operate advertising (when ad consent is enabled).
- Respond to user support and feedback.

## 3. Legal Bases (GDPR)

Where GDPR applies, we process data under one or more legal bases:

- Contract/Service delivery: data needed to run requested app features.
- Legitimate interests: service security, abuse prevention, reliability.
- Consent: analytics/ads and similar optional tracking where required.
- Legal obligation: compliance with applicable law.

## 4. Consent and Controls

PocketCraft provides settings to control optional processing:

- Crash diagnostics consent toggle
- Usage analytics consent toggle
- Personalized ads consent toggle
- Push announcements toggle

You can change these anytime in Settings under Privacy and Legal.

## 5. Sharing and Processors

We may share or process data with:

- Google Firebase services (Analytics, Firestore, Messaging, Remote Config)
- Google Mobile Ads / AdMob
- Infrastructure providers needed for app operation and delivery

We do not sell personal information for money. If your jurisdiction defines "sharing" broadly (for example cross-context advertising), we provide controls to reduce such processing.

## 6. Retention

- Feedback and operational records are retained only as long as reasonably needed for support, quality, and security.
- Local files/logs on your device remain under your device storage until removed by app logic or user action.

A final retention schedule should be defined before production launch.

## 7. Security

We use reasonable safeguards appropriate to the data and risk, including transport security where available, access controls, and least-privilege practices. No method of transmission or storage is perfectly secure.

## 8. International Transfers

Your data may be processed in countries other than your own. Where required, we rely on legally valid transfer mechanisms.

## 9. Your Rights

Depending on your location, you may have rights to:

- Access personal data
- Correct inaccurate data
- Delete data
- Restrict or object to processing
- Data portability
- Withdraw consent at any time

## 10. Children

PocketCraft is not intended for children under 13 without parental involvement. If we learn we collected personal data from a child where prohibited, we will delete it as required by law.

## 11. Contact

Privacy contact: privacy@pocketcraft.online  
Support contact: support@pocketcraft.online

## 12. Changes to This Policy

We may update this policy from time to time. We will update the effective date and version and provide additional notice where required.

---

Implementation notes for this repository:

- In-app legal links are configurable via build constants in app/build.gradle.kts.
- Consent toggles are available in Settings under Privacy and Legal.
- Analytics and ad loading are now consent-gated in code.
