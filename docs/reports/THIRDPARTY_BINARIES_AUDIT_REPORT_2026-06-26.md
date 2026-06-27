# Third-Party Binaries Audit Report

**Date:** 2026-06-26  
**Auditor:** Cursor agent (automated pass)  
**ARM-MC reference:** `/home/aleemkanyu/Downloads/arm-mc_1.4.2`  
**Scope:** `app/src/main/assets/`, `app/src/main/jniLibs/`, `app/src/main/lib/`, `app/libs/`

---

## Executive summary

| Result | Count |
|--------|-------|
| Files inventoried | 43 binaries in `app/src/main` (+ JRE tarballs) |
| **Confirmed ARM-MC exact hash matches** | **0** |
| ARM-MC exclusive libraries found in PocketCraft | **0** |
| JRE `IMPLEMENTOR="ARM-MC.com"` in active runtime | **No** (active runtime is Java 21 / `IMPLEMENTOR="N/A"`) |
| Items flagged for manual review | **3** |

**Conclusion:** No compiled binary in the current PocketCraft tree is an exact byte-for-byte copy of an ARM-MC APK file. The active bundled JRE is Java 21 (Pojav-style OpenJDK build), not ARM-MC's Java 25 runtime. Three non-matching items still deserve cleanup or documentation.

---

## ARM-MC reference fingerprint (for comparison)

From `arm-mc_1.4.2/assets/java/jre25/bin-arm64/release`:

```
IMPLEMENTOR="ARM-MC.com"
JAVA_VERSION="25.0.3"
JAVA_RUNTIME_VERSION="25.0.3-internal-adhoc.root.openjdk"
```

ARM-MC exclusive native libraries (must **not** appear in PocketCraft):

- `libpumpkin.so` (~33 MB)
- `libjrelauncher.so`
- `libfrp.so` (~15 MB)
- `libcrashguard.so`
- `libstatx_shim.so`
- `libdatastore_shared_counter.so`

**Status:** All absent from PocketCraft `app/src/main`.

---

## Active PocketCraft JRE fingerprint

From `app/src/main/assets/jre-runtime/bin-arm64.tar.xz` → `release`:

```
IMPLEMENTOR="N/A"
JAVA_VERSION="21.0.1"
JAVA_RUNTIME_VERSION="21.0.1-internal-adhoc.runner.openjdk-21"
SOURCE=".:git:060c4f7589e7+"
```

Loaded at runtime by `JreExtractor.kt` → `assetDir = "jre-runtime"`.

`libjvm.so` (arm64, extracted from active tarball):  
`c823eeec…` — **differs** from ARM-MC JRE 25 `libjvm.so` (`2138beda…`).

---

## Inventory and findings

### A. `app/src/main/jniLibs/` — native libraries

| Path | Size | SHA-256 (prefix) | Git added | Origin | ARM-MC? | Action |
|------|------|------------------|-----------|--------|---------|--------|
| `arm64-v8a/libjnidispatch.so` | 176,520 | `abc26e99…` | 2026-06-10 | JNA `linux-aarch64` dispatch | No (hash differs from ARM `fe9c603f…`) | **Keep** |
| `armeabi-v7a/libjnidispatch.so` | 126,496 | `9652282e…` | 2026-06-10 | JNA | No | **Keep** |
| `x86/libjnidispatch.so` | 124,380 | `d10fcc75…` | 2026-06-10 | JNA | No | **Keep** |
| `x86_64/libjnidispatch.so` | 126,912 | `3809247e…` | 2026-06-10 | JNA | No | **Keep** |
| `arm64-v8a/libc++_shared.so` | 1,794,776 | `46b51d66…` | 2026-05-18 | LLVM NDK C++ runtime | No (ARM `c4c2fe5c…`) | **Keep** |
| `armeabi-v7a/libc++_shared.so` | 1,301,936 | `0ce8906c…` | 2026-05-18 | LLVM NDK C++ runtime | No | **Keep** |

Also built at compile time (not stored in `jniLibs/`, shipped in APK from CMake):

| Path (build output) | Origin | ARM-MC? | Action |
|---------------------|--------|---------|--------|
| `liblauncher.so` | `app/src/main/cpp/launcher.c` — PocketCraft JNI | No (ARM uses `libjrelauncher.so` instead) | **Keep** |
| `libserverwrap.so` | `app/src/main/cpp/serverwrap.c` | No | **Keep** |

---

### B. `app/src/main/assets/` — JAR files

| Path | Size | SHA-256 (prefix) | Origin | ARM-MC? | Action |
|------|------|------------------|--------|---------|--------|
| `plugins/Geyser-Spigot.jar` | 19,143,817 | `43dda133…` | GeyserMC public plugin | No | **Keep** — re-download from upstream periodically |
| `plugins/floodgate-spigot.jar` | 11,560,850 | `651df57a…` | GeyserMC Floodgate | No | **Keep** |
| `plugins/ViaVersion.jar` | 6,434,343 | `e5a63f86…` | ViaVersion public plugin | No | **Keep** |
| `default_plugins/PocketCraftCompanion.jar` | 6,625 | `238834d0…` | Built in `companion-plugin/` (hash matches `build/libs/`) | No | **Keep** |
| `default_plugins/PocketCraftChunkLoader.jar` | 2,054 | `5b3b40f7…` | PocketCraft plugin (deployed by `ServerFileManager`) | No | **Keep** |
| `connect-spigot.jar` | 26,440,017 | `8272fc2c…` | Minekube Connect (`com/minekube/connect/…`) | No exact hash match | **Review** — not referenced in Kotlin; only listed in `CODEBASE_REPORT.md`. Confirm whether still needed or remove. |

ARM-MC APK contains **no loose `.jar` files** (app code is in `classes.dex`).

---

### C. `app/src/main/assets/` — JRE archives

| Path | Size | Notes | ARM-MC? | Action |
|------|------|-------|---------|--------|
| `jre-runtime/bin-arm64.tar.xz` | 5.4 MB | **Active** runtime; Java 21 / `IMPLEMENTOR="N/A"` | No | **Keep** |
| `jre-runtime/bin-arm.tar.xz` | 4.3 MB | Active layout | No | **Keep** |
| `jre-runtime/bin-x86.tar.xz` | 5.6 MB | Active layout | No | **Keep** |
| `jre-runtime/bin-x86_64.tar.xz` | 6.4 MB | Active layout | No | **Keep** |
| `jre-runtime/universal.tar.xz` | 24 MB | Active layout | No | **Keep** |
| `jre-runtime/version` | 41 B | Hash `655141c7…` | No | **Keep** |
| `components/jre/*.tar.xz` | Same ABIs | **Legacy duplicate** of Pojav-style Java 21 tarballs (older timestamps Apr 20 vs May 4) | No | **Review** — remove duplicate tree if `JreExtractor` no longer needs `components/jre` fallback |
| `components/jre/version` | 41 B | Hash `196da32d…` (differs from `jre-runtime/version`) | No | **Review** with above |

Compared all 31 JRE `.so` names under ARM-MC `jre25/bin-arm64/lib/` against `app/src/main/lib/`: **all hashes differ** (different Java major version / build).

---

### D. `app/src/main/lib/` — stale extracted JRE tree

| Path | Size | Git added | Notes | Action |
|------|------|-----------|-------|--------|
| `lib/server/libjvm.so` | — | 2026-04-02 | Old extracted JRE layout; **not** referenced in `build.gradle.kts` | **Review** — remove from repo if unused (31 `.so` files on disk; only `libjvm.so` + `libjsig.so` tracked in git) |
| `lib/**/*.so` (29 others) | — | Mostly untracked / gitignored | Appear to be leftover extracted JRE libs from Apr 2026 init | **Review** — likely safe to delete from working tree |

Active runtime extraction targets `filesDir/jre-runtime/`, not `app/src/main/lib/`.

---

### E. Other asset files (non-binary or non-compiled)

| Path | Notes |
|------|-------|
| `adi-registration.properties` | Obfuscated ad-network key string — unrelated to ARM-MC |
| `THIRD_PARTY_LICENSES.txt` | License attribution — keep updated |

---

## Git timing notes

| Date | Commit / event | Binaries added |
|------|----------------|----------------|
| 2026-04-02 | Reinitialize repository with LFS for runtime module | `components/jre/version`, `jre-runtime/version`, `app/src/main/lib/server/libjvm.so` + full JRE `.so` tree |
| 2026-05-18 | "bundle JRE 25 runtime assets…" (message) | `libc++_shared.so` — **note:** actual bundled JRE content is Java 21, not ARM-MC Java 25 |
| 2026-06-10 | Save progress before ping/Geyser fix | `libjnidispatch.so` (all ABIs) |

No git-added binary had an exact SHA-256 match to ARM-MC.

---

## Recommended actions (manual review queue)

| Priority | Item | Action |
|----------|------|--------|
| 1 | `connect-spigot.jar` | Confirm purpose. No Kotlin references found. If unused → **remove**; if needed → document Minekube Connect source/version in `THIRD_PARTY_LICENSES.txt` |
| 2 | `app/src/main/assets/components/jre/` | Duplicate of `jre-runtime/`. If `JreExtractor` fallback no longer needed → **remove** duplicate tarballs to avoid confusion |
| 3 | `app/src/main/lib/` | Stale extracted JRE `.so` tree not used by build → **remove** from repo working tree and add to `.gitignore` if recreated locally |
| 4 | `memory.md` | Still mentions "JRE 25 / assets/java/jre25" — **update docs** to reflect Java 21 `jre-runtime` |

**No immediate replace/remove required for confirmed ARM-MC copies** — none found in current tree.

---

## Re-run instructions

See `docs/agents/AGENT_AUDIT_THIRDPARTY_BINARIES.md` for the full repeatable procedure and shell script.
