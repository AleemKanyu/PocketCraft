#include <dlfcn.h>
#include <stdio.h>

int main() {
    void *handle = dlopen("/data/data/com.pocketcraft.server/files/lib-shims/libjnidispatch.so", RTLD_NOW);
    if (!handle) {
        printf("dlopen failed: %s\n", dlerror());
    } else {
        printf("dlopen success!\n");
        dlclose(handle);
    }
    return 0;
}
