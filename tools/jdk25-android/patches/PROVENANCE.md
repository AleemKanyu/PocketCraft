# Patch provenance

Two patches are applied, in order, to a clean checkout of
`openjdk/jdk25u` at tag `jdk-25.0.4.1-ga`.

## 0001-jdk25u-android-bionic.patch

The Android/Bionic port of OpenJDK 25. **This is not PocketCraft's work.**

It comes from the [FoldCraftLauncher](https://github.com/FCL-Team)
project's build repository,
[`FCL-Team/Android-OpenJDK-Build`](https://github.com/FCL-Team/Android-OpenJDK-Build),
branch `Build_JRE_25`, file `patches/jdk25u_android.diff`, taken verbatim
(sha256 `c9aa3e953f9abcf1f4b3b3ac2af92d1a33fca9349d5cca6c38c4b75db450119b`).
That patch set in turn descends from the long-running Android OpenJDK porting
work in
[`PojavLauncherTeam/android-openjdk-build-multiarch`](https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch).

It is a derivative work of OpenJDK and therefore carries OpenJDK's licence,
**GPLv2 with the Classpath Exception**. PocketCraft applies it to OpenJDK
source and compiles the result; PocketCraft does not redistribute any binary
produced by the Pojav or FCL projects.

What it does, broadly:

- teaches `configure` that `*-linux-android*` is a valid target that maps to
  `OPENJDK_TARGET_OS=linux`
- replaces glibc-only calls in HotSpot's Linux backend that Bionic does not
  provide (`gnu_get_libc_version`, RT-signal helpers, `statx`, parts of
  `os_perf_linux`)
- adds a small bundled `libtinyiconv`, because Bionic has no `iconv` below
  API 28 and `java.base` needs one
- adds `posix_spawn` shims and ELF-header fixups that Bionic's loader needs

Credit for that work belongs to the Pojav and FCL contributors.

## 0002-adapt-to-jdk-25.0.4.1-ga.patch

PocketCraft's own, and small.

0001 was cut against `jdk25u` **master** (25.0.5-dev). We pin the **GA** tag
instead, because a moving branch cannot be reproduced. Two files drifted
between the two trees, so 0001 skips them and this patch carries them rebased:

- **`make/autoconf/libraries.m4`** — upstream reworked X11 detection so that
  `--enable-headless-only` already disables it, making 0001's X11 hunk
  redundant. The part that still matters is forcing `NEEDS_LIB_ALSA=false`:
  Android reports `OPENJDK_TARGET_OS=linux` but has no ALSA.
- **`src/hotspot/os/linux/os_linux.cpp`** — same content as 0001's hunks, plus
  `SYS_gettid` definitions for `aarch64` (178) and `arm` (224). The GA tree
  has a hard `#error` for architectures it does not list, and 0001's version
  of that hunk no longer matched the surrounding context.

## Verifying

    git clone --depth 1 --branch jdk-25.0.4.1-ga https://github.com/openjdk/jdk25u.git
    cd jdk25u
    git apply --whitespace=fix \
      --exclude=make/autoconf/libraries.m4 \
      --exclude=src/hotspot/os/linux/os_linux.cpp \
      ../patches/0001-jdk25u-android-bionic.patch
    git apply --whitespace=fix ../patches/0002-adapt-to-jdk-25.0.4.1-ga.patch

Both apply strictly, with no rejected hunks. `build.sh` deliberately uses no
`--reject`: a half-applied port patch builds fine and then fails on device.
