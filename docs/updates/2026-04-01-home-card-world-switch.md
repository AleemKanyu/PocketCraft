# Chat Update - 2026-04-01 - Home Card World Switch

## Request
Allow changing world from the home screen server-name card. Add a small arrow icon at bottom-left of tile and include an Add option in the dropdown.

## Progress Log
- Created this update file before code changes.
- Updated home server-name card in `ConsoleScreen` so tapping the card opens world selection dropdown.
- Added small bottom-left arrow icon on the card tile for subtle discoverability.
- Added world list entries in dropdown using current discovered worlds from state holder.
- Added `Add world...` action in the same dropdown.
- Added Add World dialog from home card; entered world becomes active and can be generated on next server start.
- Added offline guard text and disabled actions while server is running.
- Ran local diagnostics and file-level error check (`ConsoleScreen.kt` reports no IDE errors).
- Build/install command output stream is truncating before final task completion in this environment, so definitive install confirmation could not be read from the command log.
- Re-ran build/install and clean install commands again on request (`build_and_install.sh` and `./gradlew clean :app:installDebug`). Terminal stream remained unstable/truncated in this session runner.
- Re-ran `bash ./build_and_install.sh` again; command returned exit code 1 in this session.
