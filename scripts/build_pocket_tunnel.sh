#!/usr/bin/env bash
# Builds PocketCraft's playit.gg tunnel agent (native/pocket-tunnel) for Android and
# places it in jniLibs, where Android extracts it as an executable the server service
# can run.
#
# The agent is our own small program on top of playit's open-source agent core
# (https://github.com/playit-cloud/playit-agent, BSD-2-Clause), pinned by commit in
# native/pocket-tunnel/Cargo.toml and compiled here from source. Nothing is downloaded
# at runtime and no prebuilt agent binary is copied in.
#
# Usage: ./scripts/build_pocket_tunnel.sh
# Needs cargo with the aarch64-linux-android target (rustup target add aarch64-linux-android)
# and the Android NDK (ANDROID_NDK_HOME, or the newest one under the SDK).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CRATE_DIR="$ROOT/native/pocket-tunnel"
OUT_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
TARGET="aarch64-linux-android"
API_LEVEL=26

# An isolated toolchain, for machines whose system Rust has no Android target.
if [ -x "$HOME/.cache/pocketcraft-rust/cargo/bin/cargo" ]; then
  export RUSTUP_HOME="$HOME/.cache/pocketcraft-rust/rustup"
  export CARGO_HOME="$HOME/.cache/pocketcraft-rust/cargo"
  export PATH="$CARGO_HOME/bin:$PATH"
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
  ANDROID_NDK_HOME="$(ls -d "$SDK"/ndk/* 2>/dev/null | sort -V | tail -1)"
fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an Android NDK}"

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
CLANG="$TOOLCHAIN/${TARGET}${API_LEVEL}-clang"
[ -x "$CLANG" ] || { echo "NDK compiler not found: $CLANG" >&2; exit 1; }

export CC_aarch64_linux_android="$CLANG"
export AR_aarch64_linux_android="$TOOLCHAIN/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CLANG"
# 16 KB page alignment, required for new devices and by Google Play.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"

cargo build --manifest-path "$CRATE_DIR/Cargo.toml" --release --locked --target "$TARGET"

mkdir -p "$OUT_DIR"
cp "$CRATE_DIR/target/$TARGET/release/pocket-tunnel" "$OUT_DIR/libpockettunnel.so"
ls -l "$OUT_DIR/libpockettunnel.so"
