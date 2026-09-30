#!/usr/bin/env bash
# Builds the GeyserReversion Geyser extension from source and places it in the app's
# assets, so older Bedrock clients can join through the bundled Geyser.
#
# The extension is built here from a pinned commit instead of being downloaded at
# runtime or copied from someone else's release, and its GPL-3.0 / AGPL-3.0 corresponding
# source archive is kept next to it for the license's source offer.
#
# Usage: ./scripts/build_geyser_reversion.sh   (needs git and a JDK 21+ in JAVA_HOME)
set -euo pipefail

REPO_URL="https://github.com/AnarchadiaMC/GeyserReversion.git"
# ouranos branch, 2026-09-28: "Support Geyser 2.11.3 with protocol 1001 bridge".
# Bump deliberately together with the bundled Geyser build it was validated against.
PINNED_COMMIT="512e6c147934ead8ef89cd7bc0413e7cd8d489af"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$ROOT/build/third_party/GeyserReversion"
ASSET_DIR="$ROOT/app/src/main/assets/geyser_extensions"
SOURCE_DIR="$ROOT/third_party_sources"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$HOME/.jdk21" /opt/android-studio/jbr; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21+}"

if [ ! -d "$WORK_DIR/.git" ]; then
  rm -rf "$WORK_DIR"
  git clone --filter=blob:none "$REPO_URL" "$WORK_DIR"
fi
if ! git -C "$WORK_DIR" cat-file -e "$PINNED_COMMIT^{commit}" 2>/dev/null; then
  git -C "$WORK_DIR" fetch --quiet origin "$PINNED_COMMIT"
fi
git -C "$WORK_DIR" checkout --quiet --force "$PINNED_COMMIT"
git -C "$WORK_DIR" clean -fdxq

cd "$WORK_DIR"
chmod +x ./gradlew
# SKIP_TESTS=1 skips the upstream test suite (it needs ~3 GB of heap and several minutes).
if [ "${SKIP_TESTS:-0}" = "1" ]; then
  ./gradlew --no-daemon --console=plain clean shadowJar sourceRelease
else
  ./gradlew --no-daemon --console=plain clean test shadowJar sourceRelease
fi

JAR="$(ls build/libs/GeyserReversion-*-all.jar | head -n1)"
SRC_ZIP="$(ls build/libs/*corresponding-source*.zip | head -n1)"

mkdir -p "$ASSET_DIR" "$SOURCE_DIR"
rm -f "$ASSET_DIR"/GeyserReversion*.jar "$SOURCE_DIR"/GeyserReversion*corresponding-source*.zip
cp "$JAR" "$ASSET_DIR/GeyserReversion.jar"
cp "$SRC_ZIP" "$SOURCE_DIR/"

{
  echo "GeyserReversion (Geyser extension)"
  echo "source: $REPO_URL"
  echo "commit: $PINNED_COMMIT"
  echo "built-jar: $(basename "$JAR")"
  echo "sha256: $(sha256sum "$ASSET_DIR/GeyserReversion.jar" | cut -d' ' -f1)"
  echo "corresponding-source: third_party_sources/$(basename "$SRC_ZIP")"
  echo "license: GPL-3.0-only (extension), AGPL-3.0 (bundled Ouranos translation engine)"
} > "$ASSET_DIR/GeyserReversion.provenance.txt"

echo "Installed $ASSET_DIR/GeyserReversion.jar"
cat "$ASSET_DIR/GeyserReversion.provenance.txt"
