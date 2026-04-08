#!/bin/bash
export JAVA_HOME=/opt/android-studio/jbr
echo "Using JAVA_HOME: $JAVA_HOME"
./gradlew :app:installDebug --no-daemon --console=plain
if [ $? -eq 0 ]; then
    echo "✓ Build and install successful!"
    adb shell dumpsys package com.pocketcraft.server | grep -E "versionName|lastUpdateTime"
else
    echo "✗ Build failed"
    exit 1
fi
