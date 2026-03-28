#! /usr/bin/env bash
#
# Licensed under the Apache License, Version 2.0
#

set -e

ARCH=$1
if [ -z "$ARCH" ]; then
    echo "Usage: do-compile-glslang.sh arm64"
    exit 1
fi
if [ "$ARCH" != "arm64" ]; then
    echo "Unsupported arch: $ARCH"
    exit 1
fi

ROOT_DIR=`pwd`
. ./tools/do-detect-env.sh

API_LEVEL=24
ABI_DIR="arm64-v8a"

SRC_DIR="$ROOT_DIR/build/glslang-$ARCH/src"
BUILD_DIR="$ROOT_DIR/build/glslang-$ARCH/build"
PREFIX_DIR="$ROOT_DIR/build/glslang-$ARCH/output"

find_cmake_bin () {
    if [ -n "$ANDROID_SDK" ] && [ -d "$ANDROID_SDK/cmake" ]; then
        local LATEST
        LATEST=$(ls -1 "$ANDROID_SDK/cmake" | sort -V | tail -n 1)
        if [ -n "$LATEST" ] && [ -f "$ANDROID_SDK/cmake/$LATEST/bin/cmake" ]; then
            echo "$ANDROID_SDK/cmake/$LATEST/bin/cmake"
            return 0
        fi
        if [ -n "$LATEST" ] && [ -f "$ANDROID_SDK/cmake/$LATEST/bin/cmake.exe" ]; then
            echo "$ANDROID_SDK/cmake/$LATEST/bin/cmake.exe"
            return 0
        fi
    fi
    if command -v cmake >/dev/null 2>&1; then
        command -v cmake
        return 0
    fi
    if command -v cmake.exe >/dev/null 2>&1; then
        command -v cmake.exe
        return 0
    fi
    return 1
}

find_ninja_bin () {
    if [ -n "$ANDROID_SDK" ] && [ -d "$ANDROID_SDK/cmake" ]; then
        local LATEST
        LATEST=$(ls -1 "$ANDROID_SDK/cmake" | sort -V | tail -n 1)
        if [ -n "$LATEST" ] && [ -f "$ANDROID_SDK/cmake/$LATEST/bin/ninja" ]; then
            echo "$ANDROID_SDK/cmake/$LATEST/bin/ninja"
            return 0
        fi
        if [ -n "$LATEST" ] && [ -f "$ANDROID_SDK/cmake/$LATEST/bin/ninja.exe" ]; then
            echo "$ANDROID_SDK/cmake/$LATEST/bin/ninja.exe"
            return 0
        fi
    fi
    if command -v ninja >/dev/null 2>&1; then
        command -v ninja
        return 0
    fi
    if command -v ninja.exe >/dev/null 2>&1; then
        command -v ninja.exe
        return 0
    fi
    return 1
}

CMAKE_BIN="$(find_cmake_bin)" || { echo "cmake not found"; exit 1; }
GENERATOR="Unix Makefiles"
MAKE_PROGRAM_ARGS=()
if NINJA_BIN="$(find_ninja_bin)"; then
    GENERATOR="Ninja"
    MAKE_PROGRAM_ARGS=(-DCMAKE_MAKE_PROGRAM="$NINJA_BIN")
else
    case "$UNAME_S" in
        CYGWIN_NT-*|MINGW64_NT*|MINGW32_NT*)
            echo "ninja not found. Please install Android SDK CMake (includes ninja.exe) or install ninja and add it to PATH."
            echo "Android Studio -> SDK Manager -> SDK Tools -> CMake"
            exit 1
        ;;
    esac
fi
echo "[*] cmake: $CMAKE_BIN"
echo "[*] generator: $GENERATOR"
if [ "${#MAKE_PROGRAM_ARGS[@]}" -gt 0 ]; then
    echo "[*] make_program: $NINJA_BIN"
fi

mkdir -p "$SRC_DIR" "$BUILD_DIR" "$PREFIX_DIR"

if [ ! -d "$SRC_DIR/.git" ]; then
    echo "[*] fetch glslang (with submodules)"
    rm -rf "$SRC_DIR"
    mkdir -p "$SRC_DIR"
    git clone --depth 1 --recurse-submodules --shallow-submodules -b 15.0.0 https://github.com/KhronosGroup/glslang.git "$SRC_DIR"
fi

echo "[*] configure glslang"
"$CMAKE_BIN" -S "$SRC_DIR" -B "$BUILD_DIR" -G "$GENERATOR" \
    "${MAKE_PROGRAM_ARGS[@]}" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI_DIR" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$PREFIX_DIR" \
    -DBUILD_SHARED_LIBS=OFF \
    -DENABLE_GLSLANG_BINARIES=OFF \
    -DENABLE_HLSL=ON \
    -DGLSLANG_TESTS=OFF \
    -DENABLE_OPT=OFF

echo "[*] build+install glslang"
"$CMAKE_BIN" --build "$BUILD_DIR" --config Release --target install
