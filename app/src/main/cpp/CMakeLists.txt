cmake_minimum_required(VERSION 3.22.1)
project(fluxa_timing C)

# Hook timestamp untuk export: dipanggil di antara lockCanvas dan unlockCanvasAndPost.
add_library(fluxa_timing SHARED fluxa_timing.c)

find_library(android-lib android)
find_library(log-lib log)
find_library(dl-lib dl)
target_link_libraries(fluxa_timing ${android-lib} ${log-lib} ${dl-lib})
