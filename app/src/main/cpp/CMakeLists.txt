cmake_minimum_required(VERSION 3.22.1)
project(pakkabaat_whisper)

# Whisper.cpp isn't vendored in this build (its source wasn't fetchable in the
# sandbox that produced this project). Add it yourself, from the repo root:
#
#   git submodule add https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper.cpp
#   git submodule update --init --recursive
#
# whisper.cpp ships its own CMakeLists.txt that builds the `whisper` static/shared
# target we link against below — nothing else to configure on that side.
add_subdirectory(whisper.cpp)

add_library(whisper_jni SHARED
    whisper_jni.cpp
)

target_include_directories(whisper_jni PRIVATE
    whisper.cpp/include
    whisper.cpp/ggml/include
)

target_link_libraries(whisper_jni
    whisper
    android
    log
)

target_compile_options(whisper_jni PRIVATE -O3)
