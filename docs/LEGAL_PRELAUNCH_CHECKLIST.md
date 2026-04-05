# PocketCraft Pre-Launch Legal and Compliance Checklist

This checklist is for launch readiness tracking and does not replace legal advice.

## How to use this file

1. Assign an owner and due date for each item.
2. Attach evidence (doc link, screenshot, policy URL, or code path).
3. Mark status as one of: NOT STARTED, IN PROGRESS, BLOCKED, DONE.
4. Do not publish until all P0 items are DONE.

## Current project signals (auto-audit)

These codebase facts indicate legal/compliance work is required:

- Firebase Analytics enabled and events logged: app/src/main/kotlin/com/pocketcraft/server/analytics/FirebaseAnalyticsManager.kt
- Firebase SDKs present (analytics, firestore, messaging, remote config, in-app messaging): app/build.gradle.kts
- Google Mobile Ads SDK present: app/build.gradle.kts
- AdMob app id configured: app/src/main/AndroidManifest.xml
- Network + notification + install package permissions present: app/src/main/AndroidManifest.xml
- Cleartext traffic currently enabled (android:usesCleartextTraffic="true"): app/src/main/AndroidManifest.xml

---

## P0 Must-Have Before Publish

### 1) Privacy Policy (Required for app stores)

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Public URL added to Play Console/App Store listing
- [ ] Public URL added in-app (Settings -> Legal)
- [ ] Data collected documented (analytics events, ads identifiers, crash logs, push tokens, account data if any)
- [ ] Data purposes documented (app functionality, analytics, messaging, ads)
- [ ] Third-party sharing documented (Google/Firebase/AdMob and any others)
- [ ] Retention/deletion process documented
- [ ] Security controls described (at least transport + storage basics)
- [ ] Contact method provided for privacy requests
- [ ] Evidence:

### 2) Terms of Service / Terms of Use

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Limitation of liability clause included
- [ ] Disclaimer of warranties included
- [ ] Acceptable use rules included
- [ ] IP ownership rules included
- [ ] Termination/suspension rights included
- [ ] Dispute process defined (arbitration/class action waiver only if lawyer-approved for your jurisdiction)
- [ ] Governing law/venue included
- [ ] Public URL added in-app and store listing
- [ ] Evidence:

### 3) Data Compliance (GDPR/CCPA/CPRA/COPPA)

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Data inventory completed (what is collected, where, why, retention, processors)
- [ ] Lawful basis documented for each processing purpose (GDPR)
- [ ] Consent flow implemented where required before analytics/ads tracking
- [ ] User rights flow implemented (access/deletion/correction requests)
- [ ] Do Not Sell/Share mechanism evaluated and implemented if required (CCPA/CPRA)
- [ ] Children policy and age-gating decision documented (COPPA risk assessment)
- [ ] Breach response runbook includes 72-hour GDPR path
- [ ] Evidence:

### 4) Third-Party SDK Disclosure and Controls

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] All SDKs listed in privacy policy
- [ ] Data Safety form in Play Console matches real behavior
- [ ] Any SDK initialization gated by consent where legally required
- [ ] Ad personalization controls documented and wired
- [ ] Evidence:

### 5) Open Source License Compliance

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Dependency license scan completed (Gradle dependencies + transitive)
- [ ] No incompatible copyleft obligations for your distribution model
- [ ] Required notices/attributions bundled or linked
- [ ] Third-party notices file prepared and reviewed
- [ ] Evidence:

### 6) Trademark Clearance

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Name search done (USPTO, EUIPO, WIPO, app stores, web)
- [ ] Logo/icon conflict search done
- [ ] Decision logged on whether to file trademark
- [ ] Evidence:

---

## P1 Strongly Recommended Before Publish

### 7) Payments and Platform Billing Rules

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Revenue model documented (ads, subscriptions, IAP, none)
- [ ] If digital goods exist: store billing policy compliance verified
- [ ] If external payments exist: PCI scope reviewed and minimized
- [ ] Evidence:

### 8) Accessibility Baseline

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Screen reader pass on key flows (TalkBack/VoiceOver)
- [ ] Color contrast checks completed
- [ ] Text scaling does not break key screens
- [ ] Touch targets and labels reviewed
- [ ] Evidence:

### 9) Encryption and Export Controls

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Encryption usage documented (HTTPS + any custom crypto)
- [ ] App Store export/compliance questions answered
- [ ] BIS self-classification reviewed if applicable
- [ ] Evidence:

### 10) Incident and Enforcement Readiness

- [ ] Status:
- [ ] Owner:
- [ ] Due date:
- [ ] Security contact email set up
- [ ] Abuse/reporting channel set up
- [ ] Takedown process documented
- [ ] Evidence:

---

## PocketCraft Action Plan (fill as you execute)

- [ ] Publish final Privacy Policy URL: __________________________
- [ ] Publish final Terms URL: __________________________________
- [ ] Add in-app Legal screen with both URLs
- [ ] Add consent gate before analytics/ads collection where required
- [ ] Complete Play Console Data Safety answers from data inventory
- [ ] Generate and review third-party license notices
- [ ] Run trademark search and archive screenshots
- [ ] Get 2-3 hour counsel review before release

---

## Sign-off

- Product owner sign-off: __________________  Date: __________
- Engineering sign-off: _____________________ Date: __________
- Legal review sign-off: ____________________ Date: __________
- Release decision: GO / NO-GO
