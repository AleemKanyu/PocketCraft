# PocketCraft — Manual JRE Runtime Setup

## Why a bundled runtime?

PocketCraft loads the JVM in-process through a native launcher.
Android does not ship a Java runtime, so we bundle one inside the APK as assets.

---

## Step-by-step: Adding the JRE

### 1. Download the latest PojavLauncher APK

Recommended source:
- https://github.com/PojavLauncherTeam/PojavLauncher/releases

### 2. Rename the APK and extract it

```bash
mv PojavLauncher.apk PojavLauncher.zip
unzip PojavLauncher.zip
```

### 3. Copy the bundled runtime

Inside the extracted APK, locate:

```
assets/components/jre-21/
```

Copy the **contents** of that folder into:

```
app/src/main/assets/jre-runtime/
```

The directory structure must look like:

```
app/src/main/assets/jre-runtime/
├── bin/
│   └── java
├── lib/
│   ├── libjli.so
│   ├── libjava.so
│   ├── server/
│   │   └── libjvm.so
│   └── ...
├── conf/
└── release
```

PocketCraft copies everything from `assets/jre-runtime/` into `codeCacheDir/jre-runtime/`
on first launch, then applies recursive permission fixes so the runtime can be loaded with
`dlopen()`.

### 4. Verify the critical shared libraries

Before building, verify these files exist:

```bash
ls app/src/main/assets/jre-runtime/lib/libjli.so
ls app/src/main/assets/jre-runtime/lib/server/libjvm.so
```

### 5. Build the APK

```bash
./gradlew assembleDebug
```

## Troubleshooting

| Problem | Fix |
|---|---|
| Runtime preparation fails at launch | Verify `app/src/main/assets/jre-runtime/` contains the extracted `jre-21` contents before building |
| `libjli.so` or `libjvm.so` missing | Re-copy the PojavLauncher `assets/components/jre-21/` contents exactly into `assets/jre-runtime/` |
| `Permission denied` when starting server | Clear app data and relaunch so `JreExtractor` re-copies the runtime and reapplies permissions |
| Server crashes immediately | Check Console screen for `UnsatisfiedLinkError` or native linker errors; the copied runtime may not be Android arm64-compatible |
| Port already in use | Change port in Settings, or stop whatever else is using port 25565 |
| Out of memory | Lower max players and view distance in Settings |
