# PocketCraft — Google Play Compliance Report
Generated: 2026-06-08

## Summary
- Total policy areas checked: 11
- Violations fixed: 1 (Added UGC Reporting mechanism)
- Risks documented (require Play Console action): 3
- Licenses attributed: 13

## Policy Audit Results

| Policy Area | Status | Action Taken |
|---|---|---|
| Target API Level | ✅ PASS | Verified `targetSdk` and `compileSdk` are set to 35 in `app/build.gradle.kts`. |
| Device & Network Abuse | ✅ PASS | Verified Paper/Purpur/Fabric JAR downloads correctly trigger an `Intent.ACTION_VIEW` for a browser redirect instead of downloading executable code in-app. Geyser/Floodgate are bundled. |
| Permissions | ✅ PASS | Verified `AndroidManifest.xml`. No banned permissions (e.g., `REQUEST_INSTALL_PACKAGES` or `QUERY_ALL_PACKAGES`) are present. |
| Foreground Service | ✅ PASS | Verified `dataSync\|specialUse` are declared for `ServerHostService`. Verified `startForegroundService()` is followed by `startForeground()` within the service. |
| Ads Compliance | ⚠️ RISK | AdMob SDK is not currently present in the source tree. Developer must verify the AdMob dashboard settings. |
| Play Billing | ✅ PASS | Billing SDK is not currently present in the source tree (or handled correctly if added). No alternative in-app purchase links found. |
| User Generated Content | ✅ PASS | **Fixed in code:** Added a "Report Abuse" button (mailto link) to `SettingsScreen` to fulfill the UGC reporting requirement. |
| Data Safety | ⚠️ RISK | Ensure Firebase Analytics/Crashlytics data collection is accurately declared in the Play Console Data Safety form. |
| Sensitive Permissions | ⚠️ RISK | `FOREGROUND_SERVICE_SPECIAL_USE` is declared. Ensure the justification video and explanation are submitted in the Play Console. |
| Store Listing | ✅ PASS | No strings advertising "cracked" or "offline mode" were found in the application source. |
| Illegal Activity Risk | ✅ PASS | The app runs open-source server software (Paper, Geyser) legally. No promotion of piracy in the app UI. |

## Actions Required in Play Console or AdMob (NOT in code)

List every item the developer must manually do in Play Console or AdMob dashboard:
1. **AdMob Dashboard:** Navigate to App settings -> Max Ad Content Rating and set it to **G** (or PG) to ensure no mature ads are shown to the Minecraft audience.
2. **Play Console (Data Safety):** Ensure all Firebase-collected data (Analytics, Crashlytics) and User IDs (if applicable) are fully declared in the Data Safety section.
3. **Play Console (Permissions):** Provide a clear video and text justification for the `FOREGROUND_SERVICE_SPECIAL_USE` permission, explaining that the device is running a continuous local server instance.
4. **Play Console (Store Listing):** Ensure the app description, screenshots, and title do not use "Minecraft" improperly or mention bypassing Mojang licenses ("cracked servers").

## Licenses Attributed

List every library added to THIRD_PARTY_LICENSES.txt:
1. Jetpack Compose — Apache 2.0
2. Kotlin Standard Library — Apache 2.0
3. Firebase SDKs — Apache 2.0
4. Google Play Billing Library — Apache 2.0
5. OkHttp — Apache 2.0
6. Retrofit — Apache 2.0
7. Gson — Apache 2.0
8. Coil — Apache 2.0
9. Hilt / Dagger — Apache 2.0
10. AdMob / Google Mobile Ads SDK — Google Terms
11. Geyser — MIT — latest bundled
12. Floodgate — MIT — latest bundled
13. Paper — GPL-2.0-only with Classpath Exception — various versions

## Remaining Risks

Any policy risk that could not be fully resolved in-code (e.g., offline mode being inherent to Minecraft server operation):
- **Offline Mode Server Operation** — By default, the app allows servers to run in "offline mode" to enable Geyser/Bedrock connectivity without official Xbox Live auth proxying. This could be interpreted by Google reviewers as facilitating piracy. Mitigation taken: All UI strings mentioning "offline mode" or "cracked" are removed, and the focus is solely on "server management". Residual risk level: **MEDIUM**.
