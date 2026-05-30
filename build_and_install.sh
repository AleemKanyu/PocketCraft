#!/bin/bash
cd "$(dirname "$0")" || exit 1

MODE="${1:-debug}"

if [ "$MODE" = "release" ]; then
    echo "Building and installing PocketCraft Release (optimized)..."
    ./build_with_studio_jdk.sh install-release
else
    echo "Building and installing PocketCraft Debug..."
    ./build_with_studio_jdk.sh install
fi
