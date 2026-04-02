cmake_minimum_required(VERSION 3.22.1)
project("pocketcraft")

add_library(launcher SHARED launcher.c)
add_executable(serverwrap serverwrap.c)
set_target_properties(serverwrap PROPERTIES OUTPUT_NAME "serverwrap" PREFIX "lib" SUFFIX ".so")

# Android 15+ on some devices uses 16KB pages; align ELF LOAD segments accordingly.
target_link_options(launcher PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
target_link_options(serverwrap PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

find_library(log-lib log)
find_library(dl-lib  dl)

target_link_libraries(launcher ${log-lib} ${dl-lib})
target_link_libraries(serverwrap ${log-lib})
