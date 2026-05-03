# Build Instructions

To build the APK with the fix:

1.  Ensure you have Android SDK, NDK, and Gradle installed.
2.  Run the following command in the project root:
    ```bash
    ./gradlew assembleRelease
    ```
3.  The APK will be generated in `app/build/outputs/apk/release/`.

**Important:**
- The fix changes the extraction marker to `jre_v8_extracted`.
- **You must uninstall the previous version or clear app data** before installing this new APK, otherwise the JRE might not be re-extracted and the issue will persist.