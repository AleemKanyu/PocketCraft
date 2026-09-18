#!/usr/bin/env bash
#
# Package a built OpenJDK 25 into the asset layout PocketCraft ships.
#
# JreExtractor.kt expects assets/java/jre25/ to contain:
#   universal.tar.xz  -- architecture-independent: conf/, legal/, lib/modules,
#                        lib/security, *.properties ... (no bin, no *.so)
#   bin-<arch>.tar.xz -- bin/, lib/jexec, lib/server/libjvm.so, every *.so
#                        flattened into lib/, and release
#   release           -- a loose copy, for inspection without unpacking
#
# Usage:  ./package.sh [arm64|arm|x86_64]   (default: arm64)
#
# Deliberately NOT packaged (both were present in the third-party runtime this
# pipeline replaces, and neither is ours to ship):
#   * libawt_xawt.so -- an X11 AWT backend. It cannot load on Android at all,
#     and the copy that was circulating was a prebuilt blob from someone
#     else's packaging tree rather than a build output.
#   * lib/fonts/Lucida*.ttf -- licensed from Bigelow & Holmes and removed from
#     upstream OpenJDK years ago. A headless server never rasterises them.
# A headless Minecraft server needs neither; libawt_headless.so covers the
# BufferedImage work that Paper/Spigot actually does (map rendering, icons).

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=versions.conf
source "$HERE/versions.conf"

ARCH="${1:-arm64}"
WORK="${WORK_DIR:-$PWD/jdk25-build}"
OUT="${OUT_DIR:-$PWD/jre25-out}"

case "$ARCH" in
  arm64)  TARGET_JDK=aarch64 ;;
  arm)    TARGET_JDK=arm ;;
  x86_64) TARGET_JDK=x86_64 ;;
  *) echo "unknown arch: $ARCH" >&2; exit 2 ;;
esac

BUILD_CONF="linux-${TARGET_JDK}-server-release"
IMAGES="$WORK/openjdk/build/$BUILD_CONF/images"
BUILDJDK="$WORK/openjdk/build/$BUILD_CONF/buildjdk/jdk"
NDK="$WORK/android-ndk-$NDK_VERSION"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
STRIP="$TOOLCHAIN/bin/llvm-strip"

[[ -d "$IMAGES/jdk" ]] || { echo "no built JDK at $IMAGES/jdk -- run build.sh first" >&2; exit 1; }

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

STAGE="$WORK/pkg-$ARCH"
rm -rf "$STAGE"
mkdir -p "$STAGE"/{jdkout,jreout,uni,archroot} "$OUT"

cp -r "$IMAGES/jdk" "$STAGE/jdkout"
cp "$WORK/freetype-$FREETYPE_VERSION/build_android-$ARCH/lib/libfreetype.so" "$STAGE/jdkout/jdk/lib/" 2>/dev/null || true

# --------------------------------------------------------------------------
# 1. jlink down to the modules a Minecraft server actually resolves
# --------------------------------------------------------------------------
MODULES="java.base,java.compiler,java.datatransfer,java.desktop,java.instrument"
MODULES="$MODULES,java.logging,java.management,java.management.rmi,java.naming"
MODULES="$MODULES,java.net.http,java.prefs,java.rmi,java.scripting,java.se"
MODULES="$MODULES,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset"
MODULES="$MODULES,java.transaction.xa,java.xml,java.xml.crypto"
MODULES="$MODULES,jdk.accessibility,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec"
MODULES="$MODULES,jdk.dynalink,jdk.editpad,jdk.httpserver,jdk.jdwp.agent,jdk.jfr"
MODULES="$MODULES,jdk.jsobject,jdk.localedata,jdk.management,jdk.management.agent"
MODULES="$MODULES,jdk.management.jfr,jdk.naming.dns,jdk.naming.rmi,jdk.net"
MODULES="$MODULES,jdk.nio.mapmode,jdk.sctp,jdk.security.auth,jdk.security.jgss"
MODULES="$MODULES,jdk.unsupported,jdk.xml.dom,jdk.zipfs,jdk.hotspot.agent"
MODULES="$MODULES,jdk.incubator.vector"
# JVMCI and the internal jshell/jline modules only make sense on the 64-bit
# targets; the 32-bit ARM build has no JVMCI backend.
if [[ "$TARGET_JDK" == "aarch64" || "$TARGET_JDK" == "x86_64" ]]; then
  MODULES="$MODULES,jdk.internal.vm.ci,jdk.internal.jvmstat,jdk.internal.ed"
  MODULES="$MODULES,jdk.internal.le,jdk.internal.md,jdk.internal.opt"
fi

log "jlink -> runtime image"
"$BUILDJDK/bin/jlink" \
  --module-path="$STAGE/jdkout/jdk/jmods" \
  --add-modules "$MODULES" \
  --output "$STAGE/jreout/jre" \
  --strip-native-debug-symbols="exclude-debuginfo-files:objcopy=$OBJCOPY" \
  --no-man-pages --no-header-files \
  --release-info="$STAGE/jdkout/jdk/release" \
  --compress=0

JRE="$STAGE/jreout/jre"
cp "$WORK/freetype-$FREETYPE_VERSION/build_android-$ARCH/lib/libfreetype.so" "$JRE/lib/" 2>/dev/null || true

# --------------------------------------------------------------------------
# 2. Make the ELFs loadable by Bionic, then strip
# --------------------------------------------------------------------------
log "Running termux-elf-cleaner"
if [[ ! -x "$WORK/termux-elf-cleaner/build/termux-elf-cleaner" ]]; then
  git clone --depth 1 https://github.com/termux/termux-elf-cleaner "$WORK/termux-elf-cleaner" 2>/dev/null || true
  mkdir -p "$WORK/termux-elf-cleaner/build"
  ( cd "$WORK/termux-elf-cleaner/build" && CFLAGS="-D__ANDROID_API__=$ANDROID_API" cmake .. >/dev/null && make -j"$(nproc)" >/dev/null )
fi
find "$JRE" -type f ! -name '*.o' -print0 \
  | xargs -0 -r file --mime-type \
  | awk -F': ' '$2 ~ /x-(executable|sharedlib|pie-executable)/ {print $1}' \
  | xargs -r "$WORK/termux-elf-cleaner/build/termux-elf-cleaner" --api-level 24

log "Stripping shared objects"
find "$JRE" -name '*.so' -exec "$STRIP" {} \;

# Drop .gnu_debuglink. It points at .debuginfo files we deliberately do not
# ship, so it is dead weight -- and its trailing CRC32 is computed over that
# debug file, which embeds absolute build paths. That CRC was the *only* thing
# differing between a local build and a CI build of identical source: four
# bytes in 19 MB. Removing it makes the output bit-identical across machines,
# which is what lets anyone verify this runtime by rebuilding it.
find "$JRE" -name '*.so' -exec "$OBJCOPY" --remove-section=.gnu_debuglink {} \;

# Drop what is not ours to ship / not needed headless (see header comment).
rm -f "$JRE"/lib/libawt_xawt.so
rm -rf "$JRE"/lib/fonts

# --------------------------------------------------------------------------
# 3. Honest release metadata
# --------------------------------------------------------------------------
# The vendor identity below is not written here for the first time -- it was
# passed to configure and is already compiled into libjvm.so. This block only
# adds the provenance lines that describe how to reproduce this exact runtime.
source "$WORK/build-inputs.env"
{
  grep -v '^SOURCE=' "$JRE/release" || true
  echo "SOURCE=\"openjdk/jdk25u:$JDK_SHA\""
  echo "BUILT_BY=\"PocketCraft jdk25-android pipeline\""
  echo "BUILD_SOURCE=\"$VENDOR_URL/tree/main/tools/jdk25-android\""
  echo "OPENJDK_TAG=\"$JDK_TAG\""
  echo "ANDROID_NDK=\"$NDK_VERSION\""
  echo "ANDROID_API=\"$ANDROID_API\""
  echo "SOURCE_DATE_EPOCH=\"$SOURCE_DATE\""
} > "$JRE/release.new"
mv "$JRE/release.new" "$JRE/release"

log "release:"; cat "$JRE/release"

# --------------------------------------------------------------------------
# 4. Split into universal + per-arch, matching JreExtractor's expectations
# --------------------------------------------------------------------------
log "Splitting into universal / bin-$ARCH"
cp -r "$JRE"/. "$STAGE/uni/"
A="$STAGE/archroot"
mkdir -p "$A/lib"

mv "$STAGE/uni/bin" "$A/bin"
mv "$STAGE/uni/lib/server" "$A/lib/server"
[[ -f "$STAGE/uni/lib/jexec" ]] && mv "$STAGE/uni/lib/jexec" "$A/lib/jexec"
mv "$STAGE/uni/release" "$A/release"
# Every remaining .so is architecture-specific; flatten into lib/.
find "$STAGE/uni" -name '*.so' -exec mv {} "$A/lib/" \;
find "$STAGE/uni" -type d -empty -delete

XZ_OPT="-6 --threads=0" tar cJf "$OUT/bin-$ARCH.tar.xz" -C "$A" .
if [[ "$ARCH" == "arm64" ]]; then
  # The universal half is identical across architectures; cut it once, from
  # the arm64 build, so all arch tarballs stay consistent with each other.
  XZ_OPT="-6 --threads=0" tar cJf "$OUT/universal.tar.xz" -C "$STAGE/uni" .
  cp "$A/release" "$OUT/release"
  cp -r "$IMAGES/jdk/legal" "$OUT/legal"
fi

# --------------------------------------------------------------------------
# 5. Provenance record
# --------------------------------------------------------------------------
{
  echo "PocketCraft OpenJDK 25 for Android -- build provenance"
  echo "======================================================"
  echo
  echo "Architecture:        $ARCH ($TARGET_JDK)"
  echo "OpenJDK source:      openjdk/jdk25u @ $JDK_TAG"
  echo "OpenJDK commit:      $JDK_SHA"
  echo "Android NDK:         $NDK_VERSION (min API $ANDROID_API)"
  echo "Boot JDK:            Temurin $BOOT_JDK_VERSION"
  echo "Patch 0001 sha256:   $PATCH_0001_SHA256"
  echo "Patch 0002 sha256:   $PATCH_0002_SHA256"
  echo "SOURCE_DATE_EPOCH:   $SOURCE_DATE ($(date -u -d @$SOURCE_DATE +%Y-%m-%d))"
  echo "Vendor:              $VENDOR_NAME"
  echo
  echo "Output hashes:"
  ( cd "$OUT" && sha256sum ./*.tar.xz release 2>/dev/null )
  echo
  echo "Embedded VM version string:"
  strings -a "$A/lib/server/libjvm.so" | grep -m1 "Server VM (.*) for " || true
} > "$OUT/BUILD-PROVENANCE-$ARCH.txt"

log "Done. Artifacts in $OUT"
ls -la "$OUT"
cat "$OUT/BUILD-PROVENANCE-$ARCH.txt"
