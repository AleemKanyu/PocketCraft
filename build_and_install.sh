#!/bin/bash
cd "$(dirname "$0")" || exit 1
echo "Building PocketCraft..."
./gradlew :app:installDebug --no-daemon --console=plain
if [ $? -eq 0 ]; then
    echo "✓ Build and install successful!"
    adb shell dumpsys package com.pocketcraft.server | grep -E "versionName|lastUpdateTime"
else
    echo "✗ Build failed"
    exit 1
fi
