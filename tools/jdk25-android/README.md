# PocketCraft OpenJDK 25 for Android

Builds the Java 25 runtime that PocketCraft ships for Minecraft 1.26+ servers,
from OpenJDK source, with PocketCraft's own vendor identity compiled in.

## Why this exists

PocketCraft previously shipped `assets/java/jre25/` that was not built here.
It was the Anvil-MC (formerly ARM-MC) JDK 25 build: 130 of its 131 files were
byte-identical to that project's, `lib/server/libjvm.so` hashed to
`2138beda82c1d50dfb2984237a536b9a5dccc1b555e0424d32b896137d2ee0f5` in both
apps, and the binary carried the build stamp
`25.0.3-internal-adhoc.root.openjdk` from a machine that was not ours. The only
file that had been changed was the plain-text `release` descriptor, hand-edited
to claim `IMPLEMENTOR="The OpenJDK Community"` with the `SOURCE=` line deleted.

Editing a text file does not change the binary it describes. This pipeline is
the actual fix: we compile our own, and the identity is set at `configure`
time so it is baked into `libjvm.so` where anyone can read it.

## Usage

The canonical build runs in CI — see
`.github/workflows/build-jdk25-android.yml`. Trigger it from the Actions tab
("Build OpenJDK 25 for Android"), pick an architecture, and download the
`jre25-<arch>` artifact. Its public log is the provenance record.

To reproduce locally you need ~16 GB of free disk and a Debian/Ubuntu host:

```bash
tools/jdk25-android/build.sh arm64
tools/jdk25-android/package.sh arm64
```

Or in the pinned container, which needs no host packages:

```bash
docker build -t pocketcraft-jdk25 tools/jdk25-android
docker run --rm -v "$PWD/out:/out" pocketcraft-jdk25 arm64
```

Expect roughly 2–4 hours on 8 cores.

## Installing the result

Copy into the app module, replacing what is there:

```bash
cp jre25-out/universal.tar.xz  app/src/main/assets/java/jre25/
cp jre25-out/bin-arm64.tar.xz  app/src/main/assets/java/jre25/
cp jre25-out/release           app/src/main/assets/java/jre25/
rm -rf app/src/main/assets/java/jre25/legal
cp -r jre25-out/legal          app/src/main/assets/java/jre25/legal
```

`JreExtractor.kt` reads exactly this layout: `universal.tar.xz` holds the
architecture-independent half, `bin-<abi>.tar.xz` the native half, and it
picks the ABI archive from `Build.SUPPORTED_ABIS` at runtime.

## What is pinned

Everything, in `versions.conf`. Floating any of it defeats the purpose.

| Input | Pin |
|---|---|
| OpenJDK source | `openjdk/jdk25u` @ `jdk-25.0.4.1-ga` |
| Android NDK | r28 (first NDK with 16 KB page alignment by default) |
| Min Android API | 21 |
| Boot JDK | Temurin 24.0.2+12 (GPLv2+CE, not Oracle's licence) |
| freetype | 2.10.0 |
| Build timestamp | `--with-source-date=version`, i.e. the tag's own release date |

The build passes `--with-source-date=version`, which is how JDK 25 drives
reproducibility (there is no `--enable-reproducible-build` option in 25). That
resolves to the pinned tag's own `DEFAULT_VERSION_DATE`, so the timestamp baked
into `libjvm.so` always matches the source it was built from.

**This is verified, not asserted.** Building on an Arch Linux workstation and
on a GitHub `ubuntu-24.04` runner produced `libjvm.so` differing by exactly
four bytes in 19,362,616 -- the trailing CRC32 of `.gnu_debuglink`, computed
over debug files that are not shipped. With that dead section removed (which
`package.sh` now does), both machines produce:

    06dec528720f9a1679f1304a52400445d3426b2c72db9aee40e2a0d10811f666

Rebuild it yourself and compare. That is the point: it is checkable by anyone,
without trusting us.

## Build identity

Set via `configure`, never by editing `release` afterwards:

```
--with-vendor-name="PocketCraft"
--with-version-opt="pocketcraft"
--with-vendor-version-string="PocketCraft"
--with-vendor-bug-url=...
```

So `java -version` and the string inside `libjvm.so` both say PocketCraft, and
`release` carries `IMPLEMENTOR="PocketCraft"` plus `SOURCE=`, `OPENJDK_TAG=`
and `BUILD_SOURCE=` lines pointing back at the upstream commit and at this
directory.

## What is deliberately not shipped

- **`libawt_xawt.so`** — the X11 AWT backend. It cannot load on Android under
  any circumstances, and the copy circulating in these packaging trees is a
  checked-in prebuilt blob rather than a build output. `--enable-headless-only`
  means we never produce it, and `package.sh` deletes it if it appears.
- **`lib/fonts/Lucida*.ttf`** — licensed from Bigelow & Holmes, removed from
  upstream OpenJDK years ago, and never rasterised by a headless server.

Paper/Spigot's actual `java.desktop` usage (map rendering, `BufferedImage`,
server icons) is served by `libawt_headless.so`, which we do build.

## Licensing

The output is OpenJDK, so it is **GPLv2 with the Classpath Exception**. Two
obligations follow, and both are met in-tree:

1. The licence text ships with the runtime — `package.sh` copies OpenJDK's
   `legal/` tree into the asset directory.
2. Corresponding source must be offered. The pin table above plus
   `patches/` is that offer: it names the exact upstream commit and every
   modification, and this directory reproduces the binary from them.

See `patches/PROVENANCE.md` for where the Bionic port patch came from and who
deserves credit for it — that work is not ours.
