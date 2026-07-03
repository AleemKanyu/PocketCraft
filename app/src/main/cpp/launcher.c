#include <android/api-level.h>
#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <malloc.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <pthread.h>
#include <fcntl.h>

#define TAG "PocketCraft"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define FULL_VERSION "21-internal"
#define DOT_VERSION "21"

#ifndef PR_SET_TAGGED_ADDR_CTRL
#define PR_SET_TAGGED_ADDR_CTRL 55
#endif

typedef jint JLI_Launch_fn(int argc, char **argv, int jargc, const char **jargv,
                           int appclassc, const char **appclassv,
                           const char *fullversion, const char *dotversion,
                           const char *pname, const char *lname,
                           jboolean javaargs, jboolean cpwildcard,
                           jboolean javaw, jint ergo);

typedef void (*android_update_LD_LIBRARY_PATH_fn)(const char *);

static int pfd[2];
static pthread_t thr;
static const char *jvm_log_tag = "PocketCraftJVM";

static void *logger_thread(void *arg) {
    (void)arg;
    ssize_t r;
    char buf[1024];
    while ((r = read(pfd[0], buf, sizeof(buf) - 1)) > 0) {
        buf[r] = 0;
        // Trim trailing newline to avoid double spacing in logcat
        if (r > 0 && buf[r-1] == '\n') buf[r-1] = 0;
        __android_log_write(ANDROID_LOG_INFO, jvm_log_tag, buf);
    }
    return NULL;
}

static void start_logger() {
    setvbuf(stdout, NULL, _IOLBF, 0);
    setvbuf(stderr, NULL, _IOLBF, 0);
    if (pipe(pfd) != 0) return;
    dup2(pfd[1], STDOUT_FILENO);
    dup2(pfd[1], STDERR_FILENO);
    if (pthread_create(&thr, NULL, logger_thread, NULL) == 0) {
        pthread_detach(thr);
    }
}

static bool path_exists(const char *path) { return access(path, F_OK) == 0; }

static bool is_directory(const char *path) {
  struct stat st;
  return stat(path, &st) == 0 && S_ISDIR(st.st_mode);
}

static bool has_suffix(const char *value, const char *suffix) {
  size_t value_len = strlen(value);
  size_t suffix_len = strlen(suffix);
  return value_len >= suffix_len &&
         strcmp(value + value_len - suffix_len, suffix) == 0;
}

static void disable_heap_tagging(void) {
#if defined(__aarch64__)
  if (prctl(PR_SET_TAGGED_ADDR_CTRL, 0, 0, 0, 0) != 0) {
    LOGI("prctl(PR_SET_TAGGED_ADDR_CTRL, 0) failed: %s", strerror(errno));
  } else {
    LOGI("Tagged address control disabled");
  }
#endif

#if defined(M_BIONIC_SET_HEAP_TAGGING_LEVEL) && defined(__ANDROID_API__) &&    \
    (__ANDROID_API__ >= 30)
  if (mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE) ==
      0) {
    LOGI("mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, NONE) failed");
  } else {
    LOGI("Heap tagging disabled");
  }
#endif
}

static void update_ld_library_path(const char *ld_library_path) {
  void *handle = dlopen("libdl.so", RTLD_LAZY);
  if (!handle) {
    LOGI("libdl.so dlopen failed: %s", dlerror());
    return;
  }

  void *symbol = dlsym(handle, "android_update_LD_LIBRARY_PATH");
  if (!symbol) {
    symbol = dlsym(handle, "__loader_android_update_LD_LIBRARY_PATH");
  }
  if (!symbol) {
    LOGI("android_update_LD_LIBRARY_PATH not found: %s", dlerror());
    return;
  }

  ((android_update_LD_LIBRARY_PATH_fn)symbol)(ld_library_path);
}

static void reset_signal_handlers(void) {
  struct sigaction clean_action;
  memset(&clean_action, 0, sizeof(clean_action));

  for (int signal_id = SIGHUP; signal_id < NSIG; signal_id++) {
    // Skip real-time signals (signals >= 32, like 34, 35) used by Android ART for GC and thread suspension.
    // Resetting them causes Dalvik/ART GC pauses or thread suspensions to kill the process with signal 34.
    if (signal_id >= 32) continue;
    if (signal_id == SIGKILL || signal_id == SIGSTOP) continue;

    clean_action.sa_handler = (signal_id == SIGSEGV) ? SIG_IGN : SIG_DFL;
    sigaction(signal_id, &clean_action, NULL);
  }
}

static void preload_shims(const char *shim_dir) {
  DIR *dir = opendir(shim_dir);
  if (!dir) {
    LOGI("preload_shims: cannot open %s", shim_dir);
    return;
  }

  struct dirent *entry;
  while ((entry = readdir(dir)) != NULL) {
    if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;

    if (entry->d_type != DT_LNK && entry->d_type != DT_REG && entry->d_type != DT_UNKNOWN) continue;

    char shim_path[1024];
    snprintf(shim_path, sizeof(shim_path), "%s/%s", shim_dir, entry->d_name);

    struct stat st;
    if (entry->d_type == DT_UNKNOWN) {
      if (stat(shim_path, &st) != 0) continue;
      if (!S_ISREG(st.st_mode) && !S_ISLNK(st.st_mode)) continue;
    }

    void *handle = dlopen(shim_path, RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
      LOGI("preloaded shim: %s", entry->d_name);
    } else {
      LOGI("preload shim %s: %s", entry->d_name, dlerror());
    }
  }

  closedir(dir);
}

static void preload_library(const char *path, const char *label) {
  void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
  if (!handle) {
    LOGI("preload_library: %s absolute path failed, trying default loader path...", label);
    handle = dlopen(label, RTLD_NOW | RTLD_GLOBAL);
  }
  if (!handle) {
    LOGI("%s skipped: %s", label, dlerror());
    return;
  }
  LOGI("%s loaded", label);
}

static void preload_runtime_tree(const char *root_path) {
  DIR *dir = opendir(root_path);
  if (!dir) {
    return;
  }

  struct dirent *entry;
  while ((entry = readdir(dir)) != NULL) {
    if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
      continue;
    }

    char child_path[1024];
    snprintf(child_path, sizeof(child_path), "%s/%s", root_path, entry->d_name);

    if (is_directory(child_path)) {
      preload_runtime_tree(child_path);
      continue;
    }

    if (has_suffix(entry->d_name, ".so")) {
      preload_library(child_path, entry->d_name);
    }
  }

  closedir(dir);
}

static void detect_runtime_paths(const char *jre_path, char *runtime_lib_dir,
                                 size_t runtime_lib_dir_size,
                                 char *runtime_jli_dir,
                                 size_t runtime_jli_dir_size,
                                 char *runtime_jvm_dir,
                                 size_t runtime_jvm_dir_size) {
  const char *candidates[] = {"lib/aarch64", "lib/arm64", "lib/amd64",
                               "lib/i386",    "lib",       NULL};

  runtime_lib_dir[0] = '\0';

  for (int i = 0; candidates[i] != NULL; i++) {
    char candidate_path[512];
    snprintf(candidate_path, sizeof(candidate_path), "%s/%s", jre_path,
             candidates[i]);
    if (is_directory(candidate_path)) {
      snprintf(runtime_lib_dir, runtime_lib_dir_size, "%s", candidate_path);
      break;
    }
  }

  if (runtime_lib_dir[0] == '\0') {
    snprintf(runtime_lib_dir, runtime_lib_dir_size, "%s/lib", jre_path);
  }

  snprintf(runtime_jli_dir, runtime_jli_dir_size, "%s/jli", runtime_lib_dir);
  if (!is_directory(runtime_jli_dir)) {
    snprintf(runtime_jli_dir, runtime_jli_dir_size, "%s", runtime_lib_dir);
  }

  snprintf(runtime_jvm_dir, runtime_jvm_dir_size, "%s/server", runtime_lib_dir);
  if (!is_directory(runtime_jvm_dir)) {
    snprintf(runtime_jvm_dir, runtime_jvm_dir_size, "%s/lib/server", jre_path);
  }
}

JNIEXPORT jint JNICALL Java_com_pocketcraft_server_NativeLauncher_launchJVM(
    JNIEnv *env, jobject thiz, jstring jJrePath, jstring jJarPath,
    jstring jServerDir, jstring jTmpDir, jstring jNativeLibDir,
    jstring jShimDir, jint minRamMb, jint maxRamMb, jstring jServerType, jint port) {
  (void)thiz;
  start_logger();
  LOGI("NativeLauncher starting...");

  jint result = 0;
  const char *jre_path = (*env)->GetStringUTFChars(env, jJrePath, NULL);
  const char *jar_path = (*env)->GetStringUTFChars(env, jJarPath, NULL);
  const char *server_dir = (*env)->GetStringUTFChars(env, jServerDir, NULL);
  const char *tmp_dir = (*env)->GetStringUTFChars(env, jTmpDir, NULL);
  const char *native_lib_dir = (*env)->GetStringUTFChars(env, jNativeLibDir, NULL);
  const char *shim_dir = (*env)->GetStringUTFChars(env, jShimDir, NULL);
  const char *server_type = (*env)->GetStringUTFChars(env, jServerType, NULL);

  char runtime_lib_dir[512];
  char runtime_jli_dir[512];
  char runtime_jvm_dir[512];
  char libjli_path[512];
  char libjvm_path[512];
  char libverify_path[512];
  char libjava_path[512];
  char libnet_path[512];
  char libnio_path[512];
  char libawt_path[512];
  char libawt_headless_path[512];
  char libfreetype_path[512];
  char libfontmanager_path[512];
  char xmx_opt[64];
  char xms_opt[64];
  char java_home_opt[1024];
  char tmp_dir_opt[1024];
  char jna_tmp_opt[1024];
  char jansi_tmp_opt[1024];
  char netty_tmp_opt[1024];
  char jna_boot_opt[1024];
  char jna_library_opt[1024];
  char user_home_opt[1024];
  char language_opt[128];
  char timezone_opt[256];
  char os_name_opt[64];
  char os_version_opt[128];
  char error_file_opt[1024];
  char lib_path_opt[3072];
  char base_ld_library_path[3072];
  char internal_ld_library_path[4096];
  char path_env[4096];
  char port_str[16];

  LOGI("JAR:    %s", jar_path);
  LOGI("JRE:    %s", jre_path);
  LOGI("Type:   %s", server_type);
  LOGI("Port:   %d", port);

  detect_runtime_paths(jre_path, runtime_lib_dir, sizeof(runtime_lib_dir),
                       runtime_jli_dir, sizeof(runtime_jli_dir),
                       runtime_jvm_dir, sizeof(runtime_jvm_dir));

  snprintf(libjli_path, sizeof(libjli_path), "%s/libjli.so", runtime_lib_dir);
  snprintf(libjvm_path, sizeof(libjvm_path), "%s/libjvm.so", runtime_jvm_dir);
  snprintf(libverify_path, sizeof(libverify_path), "%s/libverify.so",
           runtime_lib_dir);
  snprintf(libjava_path, sizeof(libjava_path), "%s/libjava.so",
           runtime_lib_dir);
  snprintf(libnet_path, sizeof(libnet_path), "%s/libnet.so", runtime_lib_dir);
  snprintf(libnio_path, sizeof(libnio_path), "%s/libnio.so", runtime_lib_dir);
  snprintf(libawt_path, sizeof(libawt_path), "%s/libawt.so", runtime_lib_dir);
  snprintf(libawt_headless_path, sizeof(libawt_headless_path),
           "%s/libawt_headless.so", runtime_lib_dir);
  snprintf(libfreetype_path, sizeof(libfreetype_path), "%s/libfreetype.so",
           runtime_lib_dir);
  snprintf(libfontmanager_path, sizeof(libfontmanager_path),
           "%s/libfontmanager.so", runtime_lib_dir);

  snprintf(base_ld_library_path, sizeof(base_ld_library_path),
           "%s:%s:%s:%s:/system/lib64:/vendor/lib64:/vendor/lib64/hw:%s", shim_dir, tmp_dir,
           runtime_jli_dir, runtime_lib_dir, native_lib_dir);
  snprintf(internal_ld_library_path, sizeof(internal_ld_library_path), "%s:%s",
           runtime_jvm_dir, base_ld_library_path);
  snprintf(path_env, sizeof(path_env), "%s/bin:%s", jre_path,
           getenv("PATH") ? getenv("PATH") : "");

  setenv("POJAV_NATIVEDIR", native_lib_dir, 1);
  setenv("JAVA_HOME", jre_path, 1);
  setenv("HOME", server_dir, 1);
  setenv("TMPDIR", tmp_dir, 1);
  setenv("LD_LIBRARY_PATH", base_ld_library_path, 1);
  setenv("PATH", path_env, 1);
  setenv("BIONIC_DISABLE_PTR_TAGGING", "1", 1);

  update_ld_library_path(internal_ld_library_path);
  disable_heap_tagging();

  LOGI("Internal LD_LIBRARY_PATH: %s", internal_ld_library_path);

  preload_shims(shim_dir);

  LOGI("dlopen: %s", libjli_path);
  void *jli_handle = dlopen(libjli_path, RTLD_LAZY | RTLD_GLOBAL);
  if (!jli_handle) {
    LOGI("libjli.so absolute path failed: %s. Trying default loader path...", dlerror());
    jli_handle = dlopen("libjli.so", RTLD_LAZY | RTLD_GLOBAL);
  }
  if (!jli_handle) {
    LOGE("libjli.so FAILED: %s", dlerror());
    result = -1;
    goto cleanup;
  }
  LOGI("libjli.so loaded");

  void *jvm_handle = dlopen("libjvm.so", RTLD_NOW | RTLD_GLOBAL);
  if (!jvm_handle) {
    LOGI("libjvm.so via loader path failed: %s", dlerror());
    LOGI("dlopen: %s", libjvm_path);
    jvm_handle = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
  }
  if (!jvm_handle) {
    LOGE("libjvm.so FAILED: %s", dlerror());
    result = -2;
    goto cleanup;
  }
  LOGI("libjvm.so loaded");

  preload_library(libverify_path, "libverify.so");
  preload_library(libjava_path, "libjava.so");
  preload_library(libnet_path, "libnet.so");
  preload_library(libnio_path, "libnio.so");
  preload_library(libawt_path, "libawt.so");
  preload_library(libawt_headless_path, "libawt_headless.so");
  preload_library(libfreetype_path, "libfreetype.so");
  preload_library(libfontmanager_path, "libfontmanager.so");
  preload_runtime_tree(runtime_lib_dir);

  JLI_Launch_fn *launch = (JLI_Launch_fn *)dlsym(jli_handle, "JLI_Launch");
  if (!launch) {
    LOGE("JLI_Launch not found: %s", dlerror());
    result = -3;
    goto cleanup;
  }

  if (chdir(server_dir) != 0) {
    LOGE("chdir failed for %s: %s", server_dir, strerror(errno));
    result = -4;
    goto cleanup;
  }

  int selected_max_mb = maxRamMb;
  if (selected_max_mb <= 0) {
    long pages = sysconf(_SC_PHYS_PAGES);
    long page_size = sysconf(_SC_PAGE_SIZE);
    long long total_bytes = (pages > 0 && page_size > 0)
                               ? (long long)pages * (long long)page_size
                               : 0;
    long long total_mb = total_bytes / (1024LL * 1024LL);
    long long default_mb = (long long)(total_mb * 0.50);
    if (default_mb < 512) default_mb = 512;
    if (default_mb > 4096) default_mb = 4096;
    selected_max_mb = (int)default_mb;
  }

  int selected_min_mb = 256;
  snprintf(xmx_opt, sizeof(xmx_opt), "-Xmx%dm", selected_max_mb);
  snprintf(xms_opt, sizeof(xms_opt), "-Xms%dm", selected_min_mb);
  snprintf(java_home_opt, sizeof(java_home_opt), "-Djava.home=%s", jre_path);
  snprintf(tmp_dir_opt, sizeof(tmp_dir_opt), "-Djava.io.tmpdir=%s", tmp_dir);
  snprintf(jna_tmp_opt, sizeof(jna_tmp_opt), "-Djna.tmpdir=%s", tmp_dir);
  snprintf(jansi_tmp_opt, sizeof(jansi_tmp_opt), "-Djansi.tmpdir=%s", tmp_dir);
  snprintf(netty_tmp_opt, sizeof(netty_tmp_opt), "-Dio.netty.native.workdir=%s", tmp_dir);
  char jna_path[1024];
  snprintf(jna_path, sizeof(jna_path), "%s/libjnidispatch.so", native_lib_dir);
  const char *jna_dir = path_exists(jna_path) ? native_lib_dir : shim_dir;
  long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
  if (cpu_count < 1) cpu_count = 4;
  long netty_threads = cpu_count / 2;
  if (netty_threads < 2) netty_threads = 2;
  if (netty_threads > 4) netty_threads = 4;

  snprintf(jna_boot_opt, sizeof(jna_boot_opt), "-Djna.boot.library.path=%s", jna_dir);
  snprintf(jna_library_opt, sizeof(jna_library_opt), "-Djna.library.path=%s", jna_dir);
  snprintf(user_home_opt, sizeof(user_home_opt), "-Duser.home=%s", server_dir);
  snprintf(language_opt, sizeof(language_opt), "-Duser.language=en");
  snprintf(timezone_opt, sizeof(timezone_opt), "-Duser.timezone=UTC");
  snprintf(os_name_opt, sizeof(os_name_opt), "-Dos.name=Linux");
  snprintf(os_version_opt, sizeof(os_version_opt), "-Dos.version=Android");
  snprintf(error_file_opt, sizeof(error_file_opt), "-XX:ErrorFile=%s/hs_err_pid%%p.log", server_dir);
  char netty_threads_opt[64];
  snprintf(netty_threads_opt, sizeof(netty_threads_opt), "-Dio.netty.eventLoopThreads=%ld", netty_threads);
  snprintf(lib_path_opt, sizeof(lib_path_opt),
           "-Djava.library.path=%s/lib/server:%s:%s:%s/lib:%s/lib:/system/lib64:/vendor/lib64:/vendor/lib64/hw:%s/lib/arm64",
           jre_path, shim_dir, tmp_dir, jre_path, jre_path, native_lib_dir);
  snprintf(port_str, sizeof(port_str), "%d", port);

  char *argv[] = {"java",
                  xmx_opt,
                  xms_opt,
                  java_home_opt,
                  tmp_dir_opt,
                  jna_tmp_opt,
                  jansi_tmp_opt,
                  netty_tmp_opt,
                  jna_boot_opt,
                  jna_library_opt,
                  user_home_opt,
                  language_opt,
                  timezone_opt,
                  os_name_opt,
                  os_version_opt,
                  "-Djava.net.preferIPv4Stack=true",
                  "-Djava.net.preferIPv6Addresses=false",
                  netty_threads_opt,
                  "-Dfile.encoding=UTF-8",
                  "-Dusing.aikars.flags=https://mcflags.emc.gs",
                  "-Dpaper.playerconnection.keepalive=90",
                  "-Dorg.jline.terminal.jna=false",
                  "-Dorg.jline.terminal.jni=false",
                  "-Dorg.jline.terminal.dumb=true",
                  "-Djava.awt.headless=true",
                  "-Djdk.lang.Process.launchMechanism=FORK",
                  lib_path_opt,
                  "-DPaper.IgnoreJavaVersion=true",
                  "-Dpaper.disable-update-check=true",
                  "-Dpaper.disable-plugin-update-check=true",
                  "-Dsun.net.client.defaultConnectTimeout=5000",
                  "-Dsun.net.client.defaultReadTimeout=5000",
                  "-Dsun.zip.disableMemoryMapping=true",
                  "-Djdk.attach.allowAttachSelf=true",
                  "-Djna.nosys=true",
                  "-Djna.nounpack=true",
                  "-Djline.terminal=none",
                  "-Xshare:off",
                  "-XX:+UnlockExperimentalVMOptions",
                  "-XX:+UnlockDiagnosticVMOptions",
                  "-XX:+UseStringDeduplication",
                  "-XX:+UseG1GC",
                  "-XX:+ParallelRefProcEnabled",
                  "-XX:MaxGCPauseMillis=80",
                  "-XX:+DisableExplicitGC",
                  "-XX:G1NewSizePercent=30",
                  "-XX:G1MaxNewSizePercent=40",
                  "-XX:G1HeapRegionSize=8m",
                  "-XX:G1ReservePercent=20",
                  "-XX:G1HeapWastePercent=5",
                  "-XX:G1MixedGCCountTarget=4",
                  "-XX:InitiatingHeapOccupancyPercent=15",
                  "-XX:G1MixedGCLiveThresholdPercent=90",
                  "-XX:G1RSetUpdatingPauseTimePercent=5",
                  "-XX:SurvivorRatio=32",
                  "-XX:MaxTenuringThreshold=1",
                  "-XX:+PerfDisableSharedMem",
                  "-Dio.netty.recycler.maxCapacity=262144",
                  "-Dio.netty.recycler.maxCapacityPerThread=1024",
                  "-Dio.netty.allocator.maxOrder=9",
                  "-Dio.netty.recycler.linkCapacity=1024",
                  "-Dio.netty.allocator.type=pooled",
                  "-Dio.netty.leakDetection.level=disabled",
                  "-Dio.netty.noPreferDirect=false",
                  "-Dio.netty.noUnsafe=false",
                  "-XX:-UseContainerSupport",
                  error_file_opt,
                  "-Xrs",
                  "-XX:+DisableAttachMechanism",
                  "-jar",
                  (char *)jar_path,
                  "--nogui",
                  "--port",
                  port_str};

  int argc = (int)(sizeof(argv) / sizeof(argv[0]));
  for (int i = 0; i < argc; i++) {
    LOGI("argv[%d] = %s", i, argv[i]);
  }

  // On Android 12+ (especially 13), debuggerd holds signal handler locks during
  // process startup. JLI_Launch internally installs SIGSEGV/SIGBUS/SIGILL/SIGFPE
  // handlers which deadlock waiting for those same locks. We reset all signal
  // handlers to SIG_DFL immediately before calling JLI_Launch so the JVM writes
  // into a clean handler table without contention.
  reset_signal_handlers();

  LOGI("Calling JLI_Launch...");
  result = launch(argc, argv, 0, NULL, 0, NULL, FULL_VERSION, DOT_VERSION,
                  argv[0], argv[0], JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
  LOGI("JLI_Launch returned: %d", result);

cleanup:
  if (server_type) (*env)->ReleaseStringUTFChars(env, jServerType, server_type);
  if (shim_dir) (*env)->ReleaseStringUTFChars(env, jShimDir, shim_dir);
  if (native_lib_dir) (*env)->ReleaseStringUTFChars(env, jNativeLibDir, native_lib_dir);
  if (tmp_dir) (*env)->ReleaseStringUTFChars(env, jTmpDir, tmp_dir);
  if (server_dir) (*env)->ReleaseStringUTFChars(env, jServerDir, server_dir);
  if (jar_path) (*env)->ReleaseStringUTFChars(env, jJarPath, jar_path);
  if (jre_path) (*env)->ReleaseStringUTFChars(env, jJrePath, jre_path);
  return result;
}
