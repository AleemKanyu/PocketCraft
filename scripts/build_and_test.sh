#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

./gradlew :app:installDebug --no-daemon -q
BUILD_EXIT=$?

if [ $BUILD_EXIT -eq 0 ]; then
    echo "[$(date)] BUILD SUCCESSFUL - App installed"
    sleep 3
    echo "[$(date)] Checking app on device..."
    adb shell pm list packages | grep pocketcraft || echo "App check pending..."
else
    echo "[$(date)] BUILD FAILED with exit code $BUILD_EXIT"
    tail -50 /tmp/gradle_build.log 2>/dev/null || echo "No detailed log available"
    exit 1
fi
