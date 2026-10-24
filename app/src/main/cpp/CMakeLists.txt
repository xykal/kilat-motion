cmake_minimum_required(VERSION 3.22.1)
project(fluxa_native C CXX)

find_library(android-lib android)
find_library(log-lib log)
find_library(dl-lib dl)

# Timestamp untuk export: dipanggil di antara lockCanvas dan unlockCanvasAndPost.
add_library(fluxa_timing SHARED fluxa_timing.c)
target_link_libraries(fluxa_timing ${android-lib} ${log-lib} ${dl-lib})

# Ubah suara: SoundTouch (LGPL-2.1, lihat soundtouch/LICENSE.txt) + jembatan JNI.
file(GLOB SOUNDTOUCH_SRC ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/src/*.cpp)
add_library(fluxa_voice SHARED fluxa_voice.cpp ${SOUNDTOUCH_SRC})
target_include_directories(fluxa_voice PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/include
    ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/src)
target_compile_definitions(fluxa_voice PRIVATE SOUNDTOUCH_FLOAT_SAMPLES=1 SOUNDTOUCH_DISABLE_X86_OPTIMIZATIONS=1)
target_compile_options(fluxa_voice PRIVATE -O2 -fvisibility=hidden)
target_link_libraries(fluxa_voice ${log-lib})
