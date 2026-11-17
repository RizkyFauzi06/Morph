# Morph native layer: a small JNI H.264 encoder loop on top of x264 (NEON).
# FFmpeg (FFmpegKit) still does all decoding, filtering and muxing; see MorphNative.kt.
cmake_minimum_required(VERSION 3.22.1)
project(morph_native C CXX ASM)

set(X264_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/x264)

# x264 is GPL source kept outside git (third_party/ is gitignored). Without it nothing is built,
# MorphNative.isAvailable stays false and Morph keeps using libopenh264.
if(NOT EXISTS ${X264_DIR}/x264.h)
    message(WARNING "x264 source not found in ${X264_DIR}: building without morph-native")
    return()
endif()

# --- libx264 (8-bit only), mirroring x264's Makefile for SYS_ARCH=AARCH64 ---
set(X264_COMMON_SRCS
    common/osdep.c common/base.c common/cpu.c common/tables.c encoder/api.c)
set(X264_BITDEPTH_SRCS
    common/mc.c common/predict.c common/pixel.c common/macroblock.c
    common/frame.c common/dct.c common/cabac.c common/common.c common/rectangle.c
    common/set.c common/quant.c common/deblock.c common/vlc.c common/mvpred.c
    common/bitstream.c common/threadpool.c
    encoder/analyse.c encoder/me.c encoder/ratecontrol.c encoder/set.c
    encoder/macroblock.c encoder/cabac.c encoder/cavlc.c encoder/encoder.c encoder/lookahead.c
    common/aarch64/asm-offsets.c common/aarch64/mc-c.c common/aarch64/predict-c.c)
set(X264_ASM_SRCS
    common/aarch64/bitstream-a.S common/aarch64/cabac-a.S common/aarch64/dct-a.S
    common/aarch64/deblock-a.S common/aarch64/mc-a.S common/aarch64/pixel-a.S
    common/aarch64/predict-a.S common/aarch64/quant-a.S)
list(TRANSFORM X264_COMMON_SRCS PREPEND ${X264_DIR}/)
list(TRANSFORM X264_BITDEPTH_SRCS PREPEND ${X264_DIR}/)
list(TRANSFORM X264_ASM_SRCS PREPEND ${X264_DIR}/)

add_library(x264 STATIC ${X264_COMMON_SRCS} ${X264_BITDEPTH_SRCS} ${X264_ASM_SRCS})
# x264_build/ first: it holds our config.h / x264_config.h instead of configure's output
target_include_directories(x264 PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/x264_build ${X264_DIR})
target_compile_definitions(x264 PRIVATE _GNU_SOURCE)
# The library ends up in a shared object: x264's asm needs -DPIC (configure adds it with --enable-pic)
set_source_files_properties(${X264_ASM_SRCS} PROPERTIES COMPILE_OPTIONS "-DPIC")
# Same per-bit-depth flags as x264's "%-8.o" rules (common sources are built without them)
set_source_files_properties(${X264_BITDEPTH_SRCS} ${X264_ASM_SRCS}
    PROPERTIES COMPILE_DEFINITIONS "HIGH_BIT_DEPTH=0;BIT_DEPTH=8")
# x264's own release flags; -O3 also applies to debug APKs so device timings are meaningful
# -fvisibility=hidden (as configure does): the asm addresses x264's tables PC-relative, which
# the linker only allows for symbols that can't be preempted from outside the .so
target_compile_options(x264 PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-std=gnu99 -O3 -ffast-math -fomit-frame-pointer -fno-tree-vectorize -fvisibility=hidden -w>)

# --- JNI library ---
add_library(morph-native SHARED morph_native.cpp)
target_compile_features(morph-native PRIVATE cxx_std_17)
target_compile_options(morph-native PRIVATE -O3 -Wall -Wextra)
target_link_libraries(morph-native PRIVATE x264 log)
# Android 15+ devices with 16 KB memory pages; export only the JNI functions (not x264's asm
# symbols or the static libc++), which keeps the dynamic symbol table and load time small
target_link_options(morph-native PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL")
