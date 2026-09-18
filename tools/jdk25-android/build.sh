#!/usr/bin/env bash
#
# Build OpenJDK 25 for Android from source.
#
# This produces PocketCraft's own JDK binary. It clones the upstream OpenJDK
# 25 update repository at a pinned GA tag, applies the Android/Bionic port
# patch in patches/, and cross-compiles it with the Android NDK. Nothing
# prebuilt is copied in from anywhere.
#
# Usage:  ./build.sh [arm64|arm|x86_64]     (default: arm64)
#
# Expects a Debian/Ubuntu host with sudo, or the container from Dockerfile.
# Output lands in $WORK/openjdk/build/<conf>/images/jdk; run package.sh next.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=versions.conf
source "$HERE/versions.conf"

ARCH="${1:-arm64}"
WORK="${WORK_DIR:-$PWD/jdk25-build}"
JOBS="${JOBS:-$(nproc)}"

case "$ARCH" in
  arm64)  TARGET=aarch64-linux-android; TARGET_JDK=aarch64 ;;
  arm)    TARGET=armv7a-linux-androideabi; TARGET_JDK=arm ;;
  x86_64) TARGET=x86_64-linux-android; TARGET_JDK=x86_64 ;;
  *) echo "unknown arch: $ARCH (expected arm64, arm or x86_64)" >&2; exit 2 ;;
esac

JVM_VARIANTS=server
JDK_DEBUG_LEVEL=release
JVM_PLATFORM=linux
BUILD_CONF="${JVM_PLATFORM}-${TARGET_JDK}-${JVM_VARIANTS}-${JDK_DEBUG_LEVEL}"

mkdir -p "$WORK"
cd "$WORK"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# --------------------------------------------------------------------------
# 1. Host build dependencies
# --------------------------------------------------------------------------
if [[ "${SKIP_APT:-0}" != "1" ]]; then
  log "Installing host build dependencies"
  sudo apt-get update -qq
  sudo apt-get install -y -qq --no-install-recommends \
    autoconf python3 python-is-python3 unzip zip xz-utils file cmake build-essential \
    systemtap-sdt-dev libxtst-dev libasound2-dev libelf-dev libfontconfig1-dev \
    libx11-dev libxext-dev libxrandr-dev libxrender-dev libxt-dev
fi

# --------------------------------------------------------------------------
# 2. Android NDK
# --------------------------------------------------------------------------
NDK="$WORK/android-ndk-$NDK_VERSION"
if [[ ! -d "$NDK" ]]; then
  log "Fetching Android NDK $NDK_VERSION"
  curl -fL --retry 3 -o "ndk.zip" "$NDK_URL"
  unzip -q ndk.zip
  rm -f ndk.zip
fi
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
ANDROID_INCLUDE="$TOOLCHAIN/sysroot/usr/include"

# --------------------------------------------------------------------------
# 3. Boot JDK (Temurin, GPLv2+CE -- no proprietary download licence)
# --------------------------------------------------------------------------
BOOT_JDK="$WORK/$BOOT_JDK_DIR"
if [[ ! -d "$BOOT_JDK" ]]; then
  log "Fetching boot JDK $BOOT_JDK_VERSION"
  curl -fL --retry 3 -o boot-jdk.tar.gz "$BOOT_JDK_URL"
  tar xf boot-jdk.tar.gz
  rm -f boot-jdk.tar.gz
fi
[[ -x "$BOOT_JDK/bin/javac" ]] || { echo "boot JDK missing at $BOOT_JDK" >&2; exit 1; }

# --------------------------------------------------------------------------
# 4. Cross-compile toolchain environment
# --------------------------------------------------------------------------
export TARGET TARGET_JDK API="$ANDROID_API"
export ANDROID_NDK_ROOT="$NDK"
export thecc="$TOOLCHAIN/bin/${TARGET}${ANDROID_API}-clang"
export thecxx="$TOOLCHAIN/bin/${TARGET}${ANDROID_API}-clang++"
[[ -x "$thecc" ]] || { echo "NDK clang not found: $thecc" >&2; exit 1; }

export AR="$TOOLCHAIN/bin/llvm-ar"
export AS="$TOOLCHAIN/bin/llvm-as"
export LD="$TOOLCHAIN/bin/ld"
export OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
export CC="$HERE/toolchain/android-wrapped-clang"
export CXX="$HERE/toolchain/android-wrapped-clang++"
chmod +x "$CC" "$CXX"

export CPPFLAGS="-I$ANDROID_INCLUDE -I$ANDROID_INCLUDE/$TARGET"
export LDFLAGS="-lstdc++ -lc++abi"

# --------------------------------------------------------------------------
# 5. freetype + cups headers (java.desktop needs them even when headless)
# --------------------------------------------------------------------------
FREETYPE_SRC="$WORK/freetype-$FREETYPE_VERSION"
FREETYPE_DIR="$FREETYPE_SRC/build_android-$ARCH"
if [[ ! -d "$FREETYPE_DIR" ]]; then
  log "Building freetype $FREETYPE_VERSION for $TARGET"
  [[ -d "$FREETYPE_SRC" ]] || { curl -fL --retry 3 -o ft.tar.gz "$FREETYPE_URL"; tar xf ft.tar.gz; rm -f ft.tar.gz; }
  pushd "$FREETYPE_SRC" >/dev/null
  # Every optional dependency must be disabled explicitly. freetype's configure
  # probes the *host* via pkg-config, so on a machine that has libbz2-dev (any
  # GitHub runner, most desktops) it enables bzip2 support and then fails
  # compiling against the NDK sysroot, which has no bzlib.h. Blanking
  # PKG_CONFIG_LIBDIR stops host libraries being discovered at all.
  # OpenJDK only needs freetype's rasteriser; none of these matter to it.
  PATH="$TOOLCHAIN/bin:$PATH" PKG_CONFIG_LIBDIR="" ./configure \
    --host="$TARGET" --prefix="$FREETYPE_DIR" \
    --without-zlib --without-bzip2 --without-png \
    --without-harfbuzz --without-brotli
  CFLAGS=-fno-rtti CXXFLAGS=-fno-rtti make -j"$JOBS"
  make install
  popd >/dev/null
fi

CUPS_DIR="$WORK/cups-$CUPS_VERSION"
if [[ ! -d "$CUPS_DIR" ]]; then
  log "Fetching cups $CUPS_VERSION headers"
  curl -fL --retry 3 -o cups.tar.gz "$CUPS_URL"
  tar xf cups.tar.gz
  rm -f cups.tar.gz
fi

# The OpenJDK makefiles look for these inside the sysroot.
ln -sfn /usr/include/X11 "$ANDROID_INCLUDE/" || true
ln -sfn /usr/include/fontconfig "$ANDROID_INCLUDE/" || true
ln -sfn "$CUPS_DIR/cups" "$ANDROID_INCLUDE/" || true

# Bionic folds pthread/rt/thread_db into libc. Empty archives satisfy the
# OpenJDK makefiles' -lpthread/-lrt/-lthread_db without patching every rule.
mkdir -p "$WORK/dummy_libs"
for l in pthread rt thread_db; do
  [[ -f "$WORK/dummy_libs/lib$l.a" ]] || "$AR" cru "$WORK/dummy_libs/lib$l.a"
done
export LDFLAGS="$LDFLAGS -L$WORK/dummy_libs"

# --------------------------------------------------------------------------
# 6. OpenJDK source at the pinned tag
# --------------------------------------------------------------------------
if [[ ! -d "$WORK/openjdk/.git" ]]; then
  log "Cloning $JDK_REPO at $JDK_TAG"
  git clone --depth 1 --branch "$JDK_TAG" "$JDK_REPO" "$WORK/openjdk"
fi
cd "$WORK/openjdk"
ACTUAL_SHA="$(git rev-parse HEAD)"
log "OpenJDK source $JDK_TAG @ $ACTUAL_SHA"

log "Applying Android/Bionic port patches"
git reset --hard --quiet
git clean -fdq
# Strict on purpose -- no --reject anywhere. A partially applied port patch
# yields a JVM that compiles and then dies on device in ways that are
# miserable to diagnose, so a rejected hunk must stop the build here and now.
#
# libraries.m4 and os_linux.cpp drifted between jdk25u master (which 0001 was
# cut against) and the GA tag we pin, so 0002 carries the rebased versions of
# exactly those two files and 0001 skips them.
git apply --whitespace=fix \
  --exclude=make/autoconf/libraries.m4 \
  --exclude=src/hotspot/os/linux/os_linux.cpp \
  "$HERE/patches/0001-jdk25u-android-bionic.patch"
git apply --whitespace=fix "$HERE/patches/0002-adapt-to-jdk-25.0.4.1-ga.patch"

# --------------------------------------------------------------------------
# 7. Configure
# --------------------------------------------------------------------------
CFLAGS_EXTRA="-DLE_STANDALONE -O3 -DANDROID -D__ANDROID__=1 -Wno-int-conversion -Wno-error=implicit-function-declaration"
if [[ "$TARGET_JDK" == "arm" ]]; then
  CFLAGS_EXTRA="$CFLAGS_EXTRA -D__thumb__ -Dfseeko=fseek -Dftello=ftell"
fi

log "Configuring"
bash ./configure \
  --with-boot-jdk="$BOOT_JDK" \
  --openjdk-target="$TARGET" \
  --build=x86_64-unknown-linux-gnu \
  --with-toolchain-type=gcc \
  --with-devkit="$TOOLCHAIN" \
  --with-extra-cflags="$CFLAGS_EXTRA" \
  --with-extra-cxxflags="$CFLAGS_EXTRA" \
  --with-extra-ldflags="$LDFLAGS" \
  --with-freetype-include="$FREETYPE_DIR/include/freetype2" \
  --with-freetype-lib="$FREETYPE_DIR/lib" \
  --with-cups-include="$CUPS_DIR" \
  --with-fontconfig-include="$ANDROID_INCLUDE" \
  --x-includes="$ANDROID_INCLUDE/X11" \
  --x-libraries=/usr/lib \
  --disable-precompiled-headers \
  --disable-warnings-as-errors \
  --enable-option-checking=fatal \
  --enable-headless-only=yes \
  --with-jvm-variants="$JVM_VARIANTS" \
  --with-jvm-features=-dtrace,-zero,-vm-structs,-epsilongc \
  --with-native-debug-symbols=external \
  --with-debug-level="$JDK_DEBUG_LEVEL" \
  --with-vendor-name="$VENDOR_NAME" \
  --with-vendor-url="$VENDOR_URL" \
  --with-vendor-bug-url="$VENDOR_BUG_URL" \
  --with-vendor-vm-bug-url="$VENDOR_VM_BUG_URL" \
  --with-version-opt="$VERSION_OPT" \
  --with-version-build="$JDK_VERSION_BUILD" \
  --with-version-pre= \
  --with-vendor-version-string="$VENDOR_NAME" \
  --with-source-date="$SOURCE_DATE_MODE" \
  OBJCOPY="$OBJCOPY" AR="$AR" STRIP="$STRIP" \
  || { echo "--- configure failed, config.log follows ---"; cat config.log; exit 1; }

# --------------------------------------------------------------------------
# 8. Build
# --------------------------------------------------------------------------
# CONFIGURE_ONLY=1 is the cheap smoke test: it proves the toolchain, the
# patches and every configure flag are good in a few minutes, rather than
# discovering a typo an hour into a full build.
if [[ "${CONFIGURE_ONLY:-0}" == "1" ]]; then
  log "CONFIGURE_ONLY set -- stopping before make"
  exit 0
fi

log "Building images with JOBS=$JOBS (this takes a while)"
cd "build/$BUILD_CONF"
make JOBS="$JOBS" images

log "Build complete: $WORK/openjdk/build/$BUILD_CONF/images/jdk"
"$WORK/openjdk/build/$BUILD_CONF/buildjdk/jdk/bin/java" -version 2>&1 || true

# Record what actually went into this build for package.sh to stamp.
cat > "$WORK/build-inputs.env" <<EOF
JDK_TAG=$JDK_TAG
JDK_SHA=$ACTUAL_SHA
NDK_VERSION=$NDK_VERSION
BOOT_JDK_VERSION=$BOOT_JDK_VERSION
PATCH_0001_SHA256=$(sha256sum "$HERE/patches/0001-jdk25u-android-bionic.patch" | cut -d' ' -f1)
PATCH_0002_SHA256=$(sha256sum "$HERE/patches/0002-adapt-to-jdk-25.0.4.1-ga.patch" | cut -d' ' -f1)
SOURCE_DATE=$(grep -m1 "^SOURCE_DATE :=" "$WORK/openjdk/build/$BUILD_CONF/spec.gmk" | cut -d= -f2 | tr -d " ")
BUILD_CONF=$BUILD_CONF
ARCH=$ARCH
EOF
