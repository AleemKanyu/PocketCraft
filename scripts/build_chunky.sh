#!/usr/bin/env bash
# Builds the Chunky world pre-generation plugin from source and places it in the app's
# assets, so "Pre-generate world" works without downloading anything at runtime.
#
# Chunky is GPL-3.0. It is built here from a pinned commit instead of being copied from
# a release, shipped under its own name, and its corresponding source archive is kept
# next to it for the license's source offer.
#
# Upstream compiles 1.5.0 for Java 25. The app also runs older Minecraft versions on
# Java 17 and 21, so this build targets Java 17; the sources compile for it unchanged.
#
# Usage: ./scripts/build_chunky.sh   (needs git and a JDK 25+ in JAVA_HOME)
set -euo pipefail

REPO_URL="https://github.com/pop4959/Chunky.git"
# Tag 1.5.0. The Bukkit plugin built from it runs on Paper and Purpur.
PINNED_COMMIT="4bec4848adc8417fca2a5579a904b17ce6ede841"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$ROOT/build/third_party/Chunky"
ASSET_DIR="$ROOT/app/src/main/assets/plugins"
SOURCE_DIR="$ROOT/third_party_sources"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /usr/lib/jvm/java-25-openjdk /usr/lib/jvm/java-26-openjdk; do
    if [ -x "$candidate/bin/javac" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 25+}"
JAVA_MAJOR="$("$JAVA_HOME/bin/javac" -version 2>&1 | sed -E 's/^javac ([0-9]+).*/\1/')"
[ "$JAVA_MAJOR" -ge 25 ] || { echo "JDK 25 or newer is needed, found $JAVA_MAJOR" >&2; exit 1; }

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
# Build-setup tweaks only, no source changes:
# - build just the Bukkit plugin and what it needs; the mod loaders pull in large toolchains
# - compile with the JDK at hand, targeting Java 17 so every bundled runtime can load it
sed -i -E '/^[[:space:]]*"(fabric|forge|neoforge|sponge)",?$/d' settings.gradle.kts
sed -i "s/JavaLanguageVersion.of(25)/JavaLanguageVersion.of($JAVA_MAJOR)/" build.gradle.kts
sed -i "s/options.release = 25/options.release = 17/" build.gradle.kts
chmod +x ./gradlew
# GRADLE_CMD lets a machine that cannot download the wrapper's Gradle use an installed one.
"${GRADLE_CMD:-./gradlew}" --no-daemon --console=plain :chunky-bukkit:shadowJar

JAR="$(ls bukkit/build/libs/Chunky-Bukkit-*.jar | head -n1)"
SRC_ZIP="$SOURCE_DIR/Chunky-${PINNED_COMMIT:0:12}-corresponding-source.zip"

mkdir -p "$ASSET_DIR" "$SOURCE_DIR"
rm -f "$SOURCE_DIR"/Chunky-*-corresponding-source.zip
cp "$JAR" "$ASSET_DIR/Chunky.jar"
git archive --format=zip --prefix=Chunky/ -o "$SRC_ZIP" "$PINNED_COMMIT"

{
  echo "Chunky (world pre-generation plugin)"
  echo "source: $REPO_URL"
  echo "commit: $PINNED_COMMIT"
  echo "built-jar: $(basename "$JAR")"
  echo "sha256: $(sha256sum "$ASSET_DIR/Chunky.jar" | cut -d' ' -f1)"
  echo "corresponding-source: third_party_sources/$(basename "$SRC_ZIP")"
  echo "build-changes: Bukkit module only, compiled for Java 17 (see scripts/build_chunky.sh)"
  echo "license: GPL-3.0"
} > "$ASSET_DIR/Chunky.provenance.txt"

echo "Installed $ASSET_DIR/Chunky.jar"
cat "$ASSET_DIR/Chunky.provenance.txt"
