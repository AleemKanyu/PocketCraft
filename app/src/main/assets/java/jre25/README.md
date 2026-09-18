# Java 25 runtime assets

**This directory is intentionally empty of binaries.** It is filled by the
build pipeline in [`tools/jdk25-android/`](../../../../../../tools/jdk25-android),
not by hand and not from another project's release.

## Why it is empty

What used to sit here was not built by PocketCraft. It was the Anvil-MC
(formerly ARM-MC) JDK 25 runtime: 130 of its 131 files were byte-identical to
that project's, `lib/server/libjvm.so` hashed to
`2138beda82c1d50dfb2984237a536b9a5dccc1b555e0424d32b896137d2ee0f5` in both
apps, and the binary carried the build stamp
`25.0.3-internal-adhoc.root.openjdk` from a build made on someone else's
machine on 2026-03-31. The only file that differed was the plain-text
`release` descriptor, which had been edited by hand to read
`IMPLEMENTOR="The OpenJDK Community"` with the `SOURCE=` line removed.

Editing a text file does not change the binary it describes. The binaries have
been removed rather than re-labelled.

## How to fill it

Run the **Build OpenJDK 25 for Android** workflow, download the `jre25-arm64`
artifact, and drop its contents in here:

```
universal.tar.xz   architecture-independent half
bin-arm64.tar.xz   native half (bin/, lib/*.so, lib/server/libjvm.so, release)
release            loose copy for inspection
legal/             OpenJDK licence texts (GPLv2+CE requires these ship)
```

`JreExtractor.kt` reads exactly that layout and picks the `bin-<abi>` archive
from `Build.SUPPORTED_ABIS` at runtime.

## Until then

`JreExtractor.extractIfNeeded` sees an empty asset directory and falls back to
the Java 21 runtime, and `ServerLauncher.ensureLaunchableRuntime` walks its
candidate list rather than crashing. Minecraft 1.26+ servers will not start on
Java 21 — they will report an unsupported class-file version — but everything
on 1.21 and below is unaffected.
