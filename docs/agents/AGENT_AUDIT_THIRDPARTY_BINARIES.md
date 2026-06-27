# AGENT_AUDIT_THIRDPARTY_BINARIES

## Objective

Identify any `.so`, `.jar`, or other compiled binary files in the codebase/assets that may have originated from ARM-MC's app rather than being built or sourced independently.

**Do not delete anything during this audit.** Flag findings for manual review first (same approach as the JRE `IMPLEMENTOR` check: confirm match → fix → respond).

---

## Context

- **App package:** `com.pocketcraft.server`
- **Active JRE path:** `app/src/main/assets/jre-runtime/` (extracted by `JreExtractor.kt`)
- **Native launcher:** `app/src/main/cpp/launcher.c`, `serverwrap.c` → `liblauncher.so`, `libserverwrap.so`
- **ARM-MC reference APK (extracted):** `/home/aleemkanyu/Downloads/arm-mc_1.4.2`
- **Known ARM-MC fingerprint:** JRE `release` file contains `IMPLEMENTOR="ARM-MC.com"` and Java 25 builds
- **Known PocketCraft-clean JRE fingerprint:** `IMPLEMENTOR="N/A"`, Java 21 Pojav/OpenJDK adhoc builds (`SOURCE=".:git:060c4f7589e7+"`)

---

## Task 1 — Inventory all binary/compiled files

Search these directories first:

```
app/src/main/assets/
app/src/main/jniLibs/
app/src/main/lib/
app/libs/
assets/java/          (if present)
```

File types:

```
*.so
*.jar
*.dex
*.aar
*.tar.xz              (JRE bundles — extract before comparing .so inside)
```

For each file, record:

| Field | How |
|-------|-----|
| Path | Relative to repo root |
| Size | `stat -c%s` |
| SHA-256 | `sha256sum` |
| Git add date | `git log --diff-filter=A --format='%ai %s' -1 -- <path>` |
| In git? | `git ls-files -- <path>` |

Also scan build outputs **for reference only** (do not treat as shipped):

```
app/build/intermediates/**/lib*.so
```

---

## Task 2 — For each `.jar` file

### 2a. Known public dependency check

Cross-reference against:

- `app/build.gradle.kts` `dependencies { }` block
- Bundled plugin policy in `PluginManager.kt` / `BundledPluginInstaller.kt`
- In-repo builds (`companion-plugin/build/libs/`)

**Expected known bundles:**

| File | Expected origin |
|------|-----------------|
| `plugins/Geyser-Spigot.jar` | GeyserMC upstream |
| `plugins/floodgate-spigot.jar` | GeyserMC upstream |
| `plugins/ViaVersion.jar` | ViaVersion upstream |
| `default_plugins/PocketCraftCompanion.jar` | Built from `companion-plugin/` |
| `default_plugins/PocketCraftChunkLoader.jar` | PocketCraft-authored plugin |

### 2b. Unexplained JAR deep inspection

For anything **not** in the table above:

```bash
unzip -p <file.jar> META-INF/MANIFEST.MF | head -30
jar tf <file.jar> | head -40
strings <file.jar> | grep -iE 'ARM-MC|implementor|arm.mc|minekube|pojav' | head
sha256sum <file.jar>
```

### 2c. Compare against ARM-MC APK

ARM-MC ships almost no loose `.jar` files (logic is in `classes.dex`). Still compare hashes:

```bash
find /home/aleemkanyu/Downloads/arm-mc_1.4.2 -name '*.jar' -exec sha256sum {} \;
```

If hashes match exactly → **confirmed ARM-MC match**.

---

## Task 3 — For each `.so` file

### 3a. Classify by role

| Category | Examples | Expected action |
|----------|----------|-----------------|
| PocketCraft-built | `liblauncher.so`, `libserverwrap.so` | Keep — verify built from `app/src/main/cpp/` |
| JNA dispatch | `libjnidispatch.so` | Keep if from standard JNA / jniLibs — compare to ARM but different hash is OK |
| NDK C++ runtime | `libc++_shared.so` | Keep — standard LLVM NDK |
| JRE runtime | `libjvm.so`, `libjava.so`, … | Must **not** match ARM-MC JRE 25 hashes; check `release` file |
| ARM-MC exclusive | `libpumpkin.so`, `libjrelauncher.so`, `libfrp.so`, `libcrashguard.so`, `libstatx_shim.so` | Must be **absent** from PocketCraft |

### 3b. String / metadata scan

```bash
strings <file.so> | grep -iE 'ARM-MC|implementor|arm.mc|pumpkin|jrelauncher|frp' | head -20
```

For JRE libs, extract and read `release`:

```bash
tar -xJf app/src/main/assets/jre-runtime/bin-arm64.tar.xz -C /tmp/jre-check
cat /tmp/jre-check/**/release
```

**Red flag:** `IMPLEMENTOR="ARM-MC.com"` or `JAVA_VERSION="25.x"` when PocketCraft intends Java 21.

### 3c. Hash compare against ARM-MC

```bash
# Build hash sets and intersect
find app/src/main -name '*.so' -exec sha256sum {} \;
find /home/aleemkanyu/Downloads/arm-mc_1.4.2 -name '*.so' -exec sha256sum {} \;
```

Exact hash match → flag as **confirmed ARM-MC match**.

---

## Task 4 — JRE tarball audit (`.tar.xz`)

PocketCraft may store JRE as compressed archives. For **each** of:

```
app/src/main/assets/jre-runtime/
app/src/main/assets/components/jre/   # legacy duplicate layout — verify still needed
```

Check:

1. `version` file hash
2. Extracted `release` metadata per ABI
3. `lib/server/libjvm.so` SHA-256 per ABI
4. Whether `JreExtractor.kt` still references the directory (`assetDir = "jre-runtime"`)

---

## Task 5 — Cross-reference git timing

```bash
git log --diff-filter=A --name-only --pretty=format:'%ai | %s' -- '*.so' '*.jar' '*.dex' '*.aar'
```

Note commits that mention JRE, ARM, native, or runtime bundling. Compare dates to when ARM-MC APK was used as agent reference material.

**Important:** A commit *message* saying "JRE 25" does not prove ARM-MC origin — always verify `release` file contents and hashes.

---

## Task 6 — Output report

Write findings to:

```
docs/reports/THIRDPARTY_BINARIES_AUDIT_REPORT_<YYYY-MM-DD>.md
```

Use this table for every binary:

| Path | Size | SHA-256 (short) | Git added | Origin | ARM-MC match? | Recommended action |
|------|------|-----------------|-----------|--------|---------------|-------------------|
| … | … | `abc123…` | 2026-04-02 | Pojav Java 21 JRE | No | Keep |

**Origin values:**

- `known dependency` — public upstream (Geyser, ViaVersion, Gradle, NDK, JNA)
- `built in-repo` — companion plugin, CMake native code
- `independently sourced` — different hash/metadata from ARM-MC, documented third-party source
- `unknown` — needs manual review
- `confirmed ARM-MC match` — exact hash or `IMPLEMENTOR="ARM-MC.com"` or ARM-MC-exclusive library name present

**Recommended action values:**

- `keep` — legitimate, documented
- `replace` — replace with independently-sourced build (e.g. fresh Pojav JRE 21, upstream plugin re-download)
- `remove` — duplicate, orphaned, or confirmed ARM-MC copy not needed
- `review` — unclear purpose; do not delete until owner confirms

---

## Task 7 — Quick automated script (optional)

Run from repo root:

```bash
ARM="/home/aleemkanyu/Downloads/arm-mc_1.4.2"
PC="."

echo "=== Exact hash matches (PC app/src/main vs ARM-MC) ==="
arm_hashes=$(mktemp)
find "$ARM" -type f \( -name '*.so' -o -name '*.jar' \) -exec sha256sum {} \; | awk '{print $1}' | sort -u > "$arm_hashes"
find "$PC/app/src/main" -type f \( -name '*.so' -o -name '*.jar' \) -exec sha256sum {} \; | while read h path; do
  grep -qx "$h" "$arm_hashes" && echo "MATCH: $path"
done
rm -f "$arm_hashes"

echo "=== ARM-MC exclusive libs in PocketCraft? ==="
for lib in libpumpkin.so libjrelauncher.so libfrp.so libcrashguard.so libstatx_shim.so; do
  find "$PC/app/src/main" -name "$lib" || echo "absent: $lib"
done

echo "=== JRE release check ==="
tar -xJf "$PC/app/src/main/assets/jre-runtime/bin-arm64.tar.xz" -O --wildcards '*/release' 2>/dev/null | grep IMPLEMENTOR
```

---

## Strict rules

1. **Never delete or replace binaries during the audit pass.**
2. **Never assume** a file is clean because it is gitignored — scan the working tree on disk.
3. **Always extract** JRE tarballs before comparing `.so` files inside.
4. **Distinguish** same *type* of library (e.g. `libjnidispatch.so`) from exact ARM-MC *copy* (hash match).
5. Update `docs/reports/THIRDPARTY_BINARIES_AUDIT_REPORT_<date>.md` after each audit run.

---

## Checklist

- [ ] All `app/src/main/assets/**/*.jar` catalogued
- [ ] All `app/src/main/jniLibs/**/*.so` catalogued
- [ ] All `app/src/main/lib/**/*.so` catalogued (flag if stale/unused)
- [ ] JRE `release` files checked for `IMPLEMENTOR="ARM-MC.com"`
- [ ] ARM-MC exclusive natives confirmed absent
- [ ] Zero exact SHA-256 matches OR each match documented with action
- [ ] Report written to `docs/reports/`
- [ ] No files deleted
