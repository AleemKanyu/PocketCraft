# PocketCraft Play Console Data Safety Answer Sheet

Date: 2026-10-11
Basis: docs/DEPENDENCY_POLICY_STRIKE_RISK_AUDIT.md and docs/PRIVACY_POLICY_DRAFT.md

Use this as your paste-ready worksheet while completing Google Play Data Safety.

## 1) Does your app collect or share user data?

- Collect data: Yes
- Share data with third parties: Yes (Google services SDK processing: Firebase + AdMob)
- Data encrypted in transit: Partially (HTTPS for Firebase/Google; relay control path still includes HTTP per current implementation)
- Users can request data deletion: Yes. In the app under Settings > Account > Delete Account, and on the web at https://pockethost.online/delete-account

## 2) Data Types Collected

### Personal info

- Email address: Yes (optional)
  - What: The email of an account the user creates or signs in to with Google
  - Purpose: Account management, app functionality
  - Collected: Yes, only if the user signs in
  - Shared: No (stored in Firebase Authentication and Firestore, which act as processors)
- Name: No
- Phone number: No
- User IDs: Yes
  - What: Locally generated app user ID; relay session identifier derived from it
  - Purpose: App functionality, diagnostics, abuse prevention
  - Collected: Yes
  - Shared: Processed by backend/relay endpoints and Firebase flows where applicable

### Financial info

- Purchase history: Yes (optional)
  - What: Supporter tier and its status, for users who donate through Google Play
  - Purpose: App functionality, account management
  - Collected: Yes
  - Shared: No
- Payment info, credit score, other financial info: No (payments are handled by Google Play)

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

- Crash logs: Yes
  - What: Firebase Crashlytics crash and non-fatal error reports; server start failures include the last console lines
  - Purpose: Analytics (diagnostics)
  - Collected: Yes in release builds
  - Shared: No (Crashlytics acts as a processor)
- Diagnostics: Yes
  - What: Feedback log excerpt and operational diagnostics metadata
  - Purpose: Troubleshooting and support
  - Collected: Yes
  - Shared: Firestore feedback payload path
- Other app performance data: Yes via analytics events when consented

### Device or other IDs

- Device or other IDs: Yes
  - What: The Android advertising ID (through the Google Mobile Ads SDK), Firebase installation and messaging identifiers
  - Purpose: Advertising or marketing, analytics, app functionality (push delivery), fraud prevention
  - Collected: Yes
  - Shared: Yes, with Google AdMob and its ad partners for advertising

Related Play Console declarations:

- App content > Ads: Yes, the app contains ads
- App content > Advertising ID: Yes, the app uses it; purpose "Advertising or marketing" (the manifest gains com.google.android.gms.permission.AD_ID through the ads SDK)
- App content > Target audience: 13 and over only

## 3) Data Sharing and Selling

- Data sold: No
- Data shared for advertising: Yes. AdMob ad requests carry the advertising ID. In the EEA, the UK and Switzerland no request is made until the user has answered the consent form
- Data shared for analytics: Yes, when analytics consent is enabled

## 4) Purpose Mapping

- App functionality: relay operation, push announcements, remote config
- Analytics: Firebase Analytics events (consent-gated)
- Developer communications/support: in-app feedback submitted to Firestore
- Fraud prevention/security: operational IDs and service reliability monitoring
- Advertising/marketing: AdMob requests (consent form shown where the law requires it)

## 5) Collection Optionality

- Required for core functionality:
  - Relay/user operational identifier
  - Server operation metadata
- Optional (user controls available):
  - Usage analytics (toggle)
  - Ad consent (consent form in the EEA, the UK and Switzerland, reopened from Settings > About > Ad privacy choices; elsewhere through the Android ad settings)
  - Push announcements/topic subscription (toggle)

## 6) Security and Compliance Notes

- Add explicit HTTPS relay hardening before production to avoid policy/security concern.
- Keep Data Safety answers synchronized with settings toggles and runtime gating.
- Re-run this sheet whenever SDKs/dependencies change.
