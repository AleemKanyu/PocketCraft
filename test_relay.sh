#!/bin/bash
# Relay Debugging Script - Run this to capture app logs and test connection

set -e

echo "================================"
echo "PocketCraft Relay Debug Test"
echo "================================"
echo ""

# Check if device is connected
DEVICES=$(adb devices | grep -v "List")
if [[ -z "$DEVICES" ]]; then
    echo "❌ No devices connected!"
    echo "Please connect your Android device and enable USB debugging."
    exit 1
fi

DEVICE_ID=$(echo "$DEVICES" | awk 'NR==1 {print $1}')
echo "✓ Device found: $DEVICE_ID"
echo ""

# Clear existing logs
echo "Clearing previous logs..."
adb logcat -c

echo "Starting fresh logcat capture..."
echo ""
echo "📋 Logcat will show errors highlighted. WATCH FOR:"
echo "   • 'Register response code:' - Should be 200"
echo "   • 'phone-ready notification result:' - Should be HTTP 200"
echo "   • 'Socket added to pool' - Should show increasing pool size"
echo "   • '⚠️ CRITICAL: getLocalIpAddress() returned NULL' - BAD if present"
echo "   • 'Pool socket error:' - Indicates socket connection failed"
echo ""
echo "Press ENTER to start logcat (you'll see them as they appear)..."
read

# Start logcat with filtering
echo ""
echo "🔍 Capturing logs (filter: RelayManager|ServerAddressResolver|phone-ready|Register|Pool socket|Socket added|CRITICAL)"
echo "Press Ctrl+C to stop"
echo ""

adb logcat | grep -E --color=auto "RelayManager|ServerAddressResolver|phone-ready|Register|Pool socket|Socket added|CRITICAL|getLocalIpAddress"

echo ""
echo "================================"
echo "Logcat stopped"
echo "================================"
