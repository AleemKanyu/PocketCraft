# Theme, Contrast, Shadow, and Sound Polish

Date: 2026-04-10
Chat: Polished light/dark mode contrast, restored visible card shadows, improved relay region readability, updated startup sound, and tuned broadcast colors.

## What Changed

- Strengthened light-mode card shadows and raised `GameCard` elevation so cards read more clearly again.
- Added themed shadows to the relay/server selection cards and made their text explicitly readable in both light and dark mode.
- Fixed storage screen top text contrast so the backup banner and current path remain visible in dark mode.
- Changed the home broadcast palette in dark mode from greenish tones to warmer orange/red tones so it matches the light-mode feel better.
- Reworked onboarding/landing page theming so it now follows the active theme instead of forcing light mode.
- Updated onboarding text, card, border, and background colors to stay readable in both light and dark mode.
- Replaced the plain startup acknowledgement beep with a short multi-tone startup chime that feels more intentional.

## Files Changed

- `app/src/main/kotlin/com/pocketcraft/server/ui/theme/PocketThemeTokens.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/components/GameCard.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/components/BroadcastBanner.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/StorageScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/screens/RelayRegionScreen.kt`
- `app/src/main/kotlin/com/pocketcraft/server/ui/onboarding/OnboardingActivity.kt`
- `app/src/main/kotlin/com/pocketcraft/server/sound/SoundManager.kt`

## Verification Plan

- Rebuild and install debug APK.
- Check light mode card shadows on home and region-selection screens.
- Check onboarding/landing page in dark mode and confirm text remains readable.
- Check storage screen top banner/path text in dark mode.
- Start the server and listen for the new startup sound.
- Confirm dark-mode broadcast banners use the warmer orange/red styling.
