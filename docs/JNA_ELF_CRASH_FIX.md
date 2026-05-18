# JNA Server Startup Crash Loop (UnsatisfiedLinkError) Fix

## Symptoms
- The server gets stuck in a 2-minute auto-restart loop because it crashes immediately on startup.
- The logcat shows an `UnsatisfiedLinkError` for `libjnidispatch.so` complaining that it cannot find `libc.so.6` or `libm.so.6` or `libutil.so.1`.
- Another variant shows `Exception java.lang.NoClassDefFoundError: Could not initialize class com.sun.jna.Native`.
- JNA keeps trying to unpack its unpatched `.tmp` file into the `runtime-tmp` directory because it refuses the patched library.

## Root Cause
1. **Wrong Library Path:** The JNA jar was being extracted by Paperclip into the `servers/worlds/<world>/libraries/` folder, not the `versions/` folder. If `ServerLauncher.kt` looks in the wrong folder, it won't find the `jna-*.jar` to patch it, falling back to an unpatched version.
2. **Unsupported ELF Tags (`DT_VERNEED`):** Even after patching the `libc.so.6` dependency string, the Android Bionic dynamic linker strictly rejects libraries containing `DT_VERNEED` (symbol versioning) tags. JNA's native library is compiled for GNU/Linux which includes these tags.
3. **JNA Cache:** JNA unpacks its native library to `lib-shims`. If a broken library is already there, it won't be re-patched.
4. **Incorrect JVM Args:** If `-Djna.nosys=true` is used, JNA completely ignores our patched system library and tries to unpack its own broken one.
5. **C++ Signal Tampering:** If `launcher.c` calls `sigaction` to ignore or default all signals (e.g., a `reset_signal_handlers()` function), it will delete Android ART's internal signal handlers (like Signal 34 for garbage collection). This causes the entire app to instantly force-close with `Process exited due to signal 34`.

## The Permanent Fix

### 1. C++ Launcher (`launcher.c`)
- **NEVER** use `reset_signal_handlers()` or tamper with global signals (`SIGHUP` to `NSIG`). Android needs these.
- Ensure the JVM is launched with the correct JNA arguments:
  ```c
  "-Djna.nosys=false",
  "-Djna.nounpack=true",
  ```

### 2. Kotlin Patcher (`ServerLauncher.kt`)
- Ensure `extractAndPatchJnaLibrary` looks in `File(serverDir, "libraries")` to find the `jna-*.jar` extracted by Paper.
- In `patchElfDtNeeded`, after replacing the library name strings (e.g., `libc.so.6` -> `libc.so\0`), **you must completely neutralize the ELF versioning tags** by replacing their tags with `DT_RPATH` (15) and setting their values to `0`. 
  ```kotlin
  val tagsToReplace = listOf(
      byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x6f.toByte(), 0, 0, 0, 0), // DT_VERNEEDNUM
      byteArrayOf(0xfe.toByte(), 0xff.toByte(), 0xff.toByte(), 0x6f.toByte(), 0, 0, 0, 0), // DT_VERNEED
      byteArrayOf(0xf0.toByte(), 0xff.toByte(), 0xff.toByte(), 0x6f.toByte(), 0, 0, 0, 0)  // DT_VERSYM
  )
  for (tag in tagsToReplace) {
      // Find the tag, replace first byte with 15 (DT_RPATH), zero the rest of the tag, and zero the 8-byte value.
  }
  ```

### 3. Clear the Cache!
If you ever update the patcher, you **must** delete the old patched files so it regenerates them:
```bash
adb shell "run-as com.pocketcraft.server rm -rf files/lib-shims/* files/runtime-tmp/*"
```