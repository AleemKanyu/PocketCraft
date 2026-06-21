# Local Firestore Admin Dashboard

This project now includes a local-only admin dashboard for managing app-visible Firestore content without manually editing documents in Firebase Console.

## What it manages

- `broadcasts` collection
- `promotions` collection
- `remote_commands` collection
- `app_config/update` document
- Announcement campaigns
- Poll campaigns
- Opt-in campaigns
- Free/Pro promo banners
- Remote command messages and force actions
- Update popup configuration
- Broadcast date windows
- Version targeting
- Poll result summaries from `broadcast_results`
- Opt-in counts from `broadcast_results`
- Detailed poll responses from `broadcast_responses`
- Read-only Firestore collection explorer

## How to run it

Use either a Firebase service account JSON file or application default credentials.

If this is your first time using the local dashboard on this machine, install the Functions dependencies once:

```bash
cd functions
npm install
```

### Option 1: Service account JSON

```bash
node scripts/local_admin_dashboard.cjs --service-account=/absolute/path/to/service-account.json
```

### Option 2: Environment variable

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
node scripts/local_admin_dashboard.cjs
```

### Option 3: From the `functions` folder

```bash
npm run admin-dashboard
```

Then open:

```text
http://127.0.0.1:4310
```

## Notes

- The server binds to `127.0.0.1` only, so it is not publicly hosted.
- Leave document ID blank to create a fresh broadcast document.
- Reuse a document ID only when you want to edit the same campaign.
- To show a new popup to everyone later, create a new broadcast document rather than overwriting the old one.
- `startDate` and `expiryDate` are stored as Firestore timestamps by the dashboard.
- Date/time inputs now use separate date and time fields, with time defaulting to `00:00`.

## Recommended workflow

1. Start the dashboard locally.
2. Create a new poll, opt-in, or announcement.
3. Set the start and end window.
4. Save it.
5. Refresh the list to confirm it is live.

## Credential reminder

This dashboard uses Firebase Admin privileges. Keep the service account file private and do not ship it inside the Android app.
