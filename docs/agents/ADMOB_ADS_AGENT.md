# ADMOB_ADS_AGENT

## Goal

Integrate AdMob banner and interstitial ads into PocketCraft, replace all existing ad units in the app, and show a one-time announcement popup explaining the monetization model.

## Task 1 - Banner Ad on Home Screen

### Placement

- Bottom of `HomeActivity` or the main launcher screen shown after app open.
- Standard AdMob `BANNER` size (320 x 50).
- Must not overlap buttons or content; the layout must reserve space for it.

### Configuration

- AdMob app ID: `ca-app-pub-7133828334952044~8564169695`
- Release banner unit: `ca-app-pub-7133828334952044/1827357648`
- Debug/test banner unit: `ca-app-pub-3940256099942544/6300978111`

Initialize Mobile Ads once, load the banner on the home screen, and forward the activity lifecycle through `resume()`, `pause()`, and `destroy()`.

## Task 2 - Interstitial Ad on Server Start

When the user taps Start Server:

- Begin server startup immediately in the background.
- Show a preloaded interstitial over the loading/progress UI when available.
- Never block server startup waiting for an ad.
- Preload a replacement after the displayed ad is dismissed or fails to show.

## Task 3 - Interstitial Ad on Server Stop

When the user taps Stop Server:

- Begin server shutdown immediately in the background.
- Show the same preloaded interstitial when available.
- Never block server shutdown waiting for an ad.
- A rapid start/stop may show only one ad while the next one is loading.

### Interstitial Configuration

- Release interstitial unit: `ca-app-pub-7133828334952044/9514275973`
- Debug/test interstitial unit: `ca-app-pub-3940256099942544/1033173712`
- Do not show more than one interstitial for a single natural start or stop action.
- Keep at least five seconds between full-screen ad displays.

## Task 4 - One-Time Announcement Popup

Show once on launch after a fresh install or the first launch following this update. Store the completion flag as `announcement_v1_shown` in the `pocketcraft_prefs` SharedPreferences file. Once either action is selected, do not show the dialog again.

### Content

**Title:** A note from PocketCraft

**Body:**

Hey! Just wanted to be upfront - PocketCraft will always be completely free. No subscriptions, ever.

We use a small number of ads to keep the servers running and the app improving. That's it.

If you'd like to help us go fully ad-free, or just want to chat with the team, join our Discord - we'd love to have you.

**Actions:**

- `Join Discord`: open the existing PocketCraft Discord invite URL.
- `Got it`: dismiss the dialog.

The dialog must not be cancelable by tapping outside it or pressing Back.

## Required Changes

- Add a non-overlapping banner to the home screen.
- Initialize and manage the home banner lifecycle.
- Show preloaded interstitials on server start and stop while those operations continue independently.
- Add debug and release ad unit resources so development always uses Google's test IDs.
- Verify the AdMob app ID in the manifest.
- Find and reuse the Discord invite URL already present in the codebase.
- Add and invoke `AnnouncementDialog` on the main launch screen.
- Remove or replace every previous ad implementation and ad unit in the app.

## AdMob Console Checklist

- [x] Banner ad unit ID: `ca-app-pub-7133828334952044/1827357648`
- [x] Interstitial ad unit ID: `ca-app-pub-7133828334952044/9514275973`
- [x] App ID: `ca-app-pub-7133828334952044~8564169695`
- [x] `app-ads.txt` contains `pub-7133828334952044`
- [ ] Reuse the existing Discord invite URL.
- [ ] Use test ad unit IDs in debug builds and real IDs only in release builds.
