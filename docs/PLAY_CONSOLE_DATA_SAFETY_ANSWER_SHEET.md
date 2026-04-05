# PocketCraft Play Console Data Safety Answer Sheet

Date: 2026-04-05
Basis: docs/DEPENDENCY_POLICY_STRIKE_RISK_AUDIT.md and docs/PRIVACY_POLICY_DRAFT.md

Use this as your paste-ready worksheet while completing Google Play Data Safety.

## 1) Does your app collect or share user data?

- Collect data: Yes
- Share data with third parties: Yes (Google services SDK processing: Firebase + AdMob)
- Data encrypted in transit: Partially (HTTPS for Firebase/Google; relay control path still includes HTTP per current implementation)
- Users can request data deletion: Not yet implemented as in-app self-service endpoint (manual support channel only)

## 2) Data Types Collected

### Personal info

- Email address: No
- Name: No
- Phone number: No
- User IDs: Yes
  - What: Locally generated app user ID; relay session identifier derived from it
  - Purpose: App functionality, diagnostics, abuse prevention
  - Collected: Yes
  - Shared: Processed by backend/relay endpoints and Firebase flows where applicable

### Financial info

- No

### Health and fitness

- No

### Messages

- In-app messages / feedback text: Yes
  - What: User feedback body submitted from Settings feedback form
  - Purpose: Support and app quality
  - Collected: Yes
  - Shared: Stored in Firebase Firestore

### Photos and videos

- No

### Audio files

- No

### Files and docs

- No user-uploaded personal files as a tracked analytics data type

### Calendar

- No

### Contacts

- No

### App activity

- App interactions: Yes
  - What: Analytics events for app/server usage flows
  - Purpose: Analytics, diagnostics, product improvement
  - Collected: Yes (only when analytics consent enabled)
  - Shared: Processed by Firebase Analytics
- In-app search history: No
- Installed apps: No
- Other user-generated content: Limited to feedback message text and optional diagnostics excerpt

### Web browsing

- No

### App info and performance

- Crash logs: No dedicated Crashlytics runtime integration in current build
- Diagnostics: Yes
  - What: Feedback log excerpt and operational diagnostics metadata
  - Purpose: Troubleshooting and support
  - Collected: Yes
  - Shared: Firestore feedback payload path
- Other app performance data: Yes via analytics events when consented

### Device or other IDs

- Device or other IDs: Yes
  - What: Firebase identifiers and device/app metadata via SDKs
  - Purpose: Analytics, messaging delivery, ads serving
  - Collected: Yes
  - Shared: Processed by Google Firebase/Ads SDKs

## 3) Data Sharing and Selling

- Data sold: No
- Data shared for advertising: Yes, when ad consent is enabled (AdMob ad request flow)
- Data shared for analytics: Yes, when analytics consent is enabled

## 4) Purpose Mapping

- App functionality: relay operation, push announcements, remote config
- Analytics: Firebase Analytics events (consent-gated)
- Developer communications/support: in-app feedback submitted to Firestore
- Fraud prevention/security: operational IDs and service reliability monitoring
- Advertising/marketing: AdMob requests (consent-gated)

## 5) Collection Optionality

- Required for core functionality:
  - Relay/user operational identifier
  - Server operation metadata
- Optional (user controls available):
  - Usage analytics (toggle)
  - Personalized ads (toggle)
  - Push announcements/topic subscription (toggle)

## 6) Security and Compliance Notes

- Add explicit HTTPS relay hardening before production to avoid policy/security concern.
- Keep Data Safety answers synchronized with settings toggles and runtime gating.
- Re-run this sheet whenever SDKs/dependencies change.
