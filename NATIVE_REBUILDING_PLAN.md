# Native Launcher Rebuilding Plan - Option 2 (UPDATED)

## CRITICAL FINDING
**liblauncher.so is only 46KB in size** - NOT the memory issue!

The actual problem: `launcher.c` uses `dlopen()` to dynamically load **JVM runtime libraries** at startup, which likely fail due to system memory constraints or missing runtime.

---

## Investigation Results

### Current Build Configuration
- **Language**: C (simple JNI wrapper)
- **Location**: `app/src/main/cpp/launcher.c` & `serverwrap.c`
- **Built by**: CMake with Android NDK
- **Current sizes**:
  - Debug: 46KB
  - Release: 55KB

### launcher.c Functionality
- Loads JVM via dlopen() to launch Minecraft server
- Sets up LD_LIBRARY_PATH for runtime libraries
- Manages process memory and heap allocation
- No debug symbols to strip (already minimal)

---

## REVISED PROBLEM ANALYSIS

The "Out of memory" error is NOT from liblauncher.so being too large, but from:

1. **JVM Runtime Not Extracting** - The runtime libraries referenced by launcher aren't available
2. **Insufficient Free RAM** - Device doesn't have enough free memory when launcher tries to load JVM
3. **RELRO Protection Overhead** - Release mode's stricter protection (GNU RELRO) requires more contiguous memory

---

## ACTUAL SOLUTION (REVISED)

### Option A: Pre-allocate Runtime Explicitly
Modify `launcher.c` to extract and cache JVM libraries before loading:
- Extract runtime at installation time (not startup)
- Pre-create memory spaces for libraries
- Avoids dynamic allocation failures

### Option B: Disable RELRO for liblauncher.so Only
Add linker flag to CMakeLists.txt:
```cmake
target_link_options(launcher PRIVATE "-Wl,-z,norelro")
```
This removes GNU RELRO protection just for the launcher, allowing it to load with less memory impact.

### Option C: Reduce Default JVM Heap
Modify `launcher.c` to request smaller initial heap:
```c
// Reduce from 512MB to 256MB or less
```

---

## RECOMMENDED ACTION

**Option B is best** - Just disable RELRO for launcher:

1. Edit: `app/src/main/cpp/CMakeLists.txt`
2. Add RELRO disable flag
3. Rebuild with: `./gradlew cleanBuildCache assembleRelease`
4. Test on device

---

## Updated CMakeLists.txt Change

**Current (line 9-10):**
```cmake
target_link_options(launcher PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
```

**New (add this):**
```cmake
target_link_options(launcher PRIVATE "-Wl,-z,norelro" "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
```

---

## Success Criteria
- ✅ Release APK builds
- ✅ Server process starts (no crash)
- ✅ JVM loads successfully
- ✅ Server accepts players

---

## Proceed?
This is a minimal change with low risk. Should fix the release build issue.

