#include <android/api-level.h>
#include <android/log.h>
#include <errno.h>
#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <unistd.h>

#define TAG "PocketCraftWrap"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#ifndef PR_SET_TAGGED_ADDR_CTRL
#define PR_SET_TAGGED_ADDR_CTRL 55
#endif

#ifndef M_BIONIC_SET_HEAP_TAGGING_LEVEL
#define M_BIONIC_SET_HEAP_TAGGING_LEVEL (-201)
#endif

#ifndef M_HEAP_TAGGING_LEVEL_NONE
#define M_HEAP_TAGGING_LEVEL_NONE 0
#endif

static void disable_heap_tagging(void) {
#if defined(__aarch64__)
    if (prctl(PR_SET_TAGGED_ADDR_CTRL, 0, 0, 0, 0) != 0) {
        LOGE("prctl(PR_SET_TAGGED_ADDR_CTRL, 0) failed: %s", strerror(errno));
    } else {
        LOGI("Tagged address control disabled");
    }
#endif

    if (mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE) == 0) {
        LOGE("mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, NONE) failed: %s", strerror(errno));
    } else {
        LOGI("Heap tagging disabled");
    }
}

__attribute__((constructor)) static void on_preload_untag(void) {
    disable_heap_tagging();
}

int main(int argc, char** argv) {
    if (argc < 2) {
        LOGE("Usage: serverwrap <java-bin> [args...]");
        return 64;
    }

    disable_heap_tagging();

    execv(argv[1], &argv[1]);
    LOGE("execv failed for %s: %s", argv[1], strerror(errno));
    return 127;
}
