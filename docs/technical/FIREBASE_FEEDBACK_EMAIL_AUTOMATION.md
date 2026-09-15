# Firebase Feedback Email Automation

This project now supports automatic outbound email forwarding for new feedback docs written to Firestore collection `beta_feedback`.

## What happens automatically

1. App writes feedback into Firestore (`beta_feedback`).
2. Cloud Function `forwardFeedbackEmail` triggers on document create.
3. Function sends email to `support@pockethost.online` using Resend API.
4. Function updates feedback doc with status fields:
   - `emailStatus`: `sent`, `failed`, or `skipped`
   - `emailSentAt`
   - `emailError` (if failed)
   - `emailProviderMessageId`

## One-time setup

1. Install Firebase CLI:

```bash
npm i -g firebase-tools
firebase login
```

2. Select Firebase project:

```bash
firebase use --add
```

3. Install function dependencies:

```bash
cd functions
npm install
cd ..
```

4. Set function secrets:

```bash
firebase functions:secrets:set RESEND_API_KEY
firebase functions:secrets:set EMAIL_FROM
```

Recommended `EMAIL_FROM`: a verified sender in Resend, for example `PocketHost <noreply@pockethost.online>`.

5. Deploy functions:

```bash
firebase deploy --only functions
```

## Verify

1. Submit feedback from app Settings.
2. In Firestore, open the created doc under `beta_feedback`.
3. Confirm `emailStatus` becomes `sent`.
4. Confirm support mailbox receives the message.

## Notes

- Function file: `functions/src/index.ts`
- Trigger path: `beta_feedback/{feedbackId}`
- Destination address is fixed to `support@pockethost.online`.
- Function is configured with `retry: false` to reduce duplicate sends.
