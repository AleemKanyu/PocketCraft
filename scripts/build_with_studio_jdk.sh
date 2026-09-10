#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.." || exit 1

if [ -d "/home/aleemkanyu/.jdk21" ]; then
    STUDIO_JDK="/home/aleemkanyu/.jdk21"
elif [ -d "/opt/android-studio/jbr" ]; then
    STUDIO_JDK="/opt/android-studio/jbr"
else
    echo "JDK not found at /home/aleemkanyu/.jdk21 or /opt/android-studio/jbr"
    exit 1
fi

export JAVA_HOME="$STUDIO_JDK"
export PATH="$JAVA_HOME/bin:$PATH"

MODE="${1:-install}"
shift || true

case "$MODE" in
  apk)
    TASK=":app:assembleDebug"
    ;;
  install)
    TASK=":app:installDebug"
    ;;
  release)
    TASK=":app:assembleRelease"
    ;;
  install-release)
    TASK=":app:installRelease"
    ;;
  *)
    echo "Usage: ./build_with_studio_jdk.sh [apk|install|release|install-release] [extra gradle args...]"
    exit 1
    ;;
esac

echo "Using JAVA_HOME: $JAVA_HOME"
echo "Running Gradle task: $TASK"

./gradlew "$TASK" --no-daemon --console=plain "$@"

if [ "$MODE" = "apk" ]; then
    echo "APK ready at: app/build/outputs/apk/debug/app-debug.apk"
elif [ "$MODE" = "release" ]; then
    echo "APK/AAB outputs are in: app/build/outputs/"
else
    echo "Install successful."
    adb shell dumpsys package com.pocketcraft.server | grep -E "versionName|lastUpdateTime" || true
fi
