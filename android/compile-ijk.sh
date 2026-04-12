#! /usr/bin/env bash
#
# Copyright (C) 2013-2014 Bilibili
# Copyright (C) 2013-2014 Zhang Rui <bbcallen@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

REQUEST_TARGET=$1
REQUEST_SUB_CMD=$2
ACT_ABI_64="arm64"
ACT_ABI_ALL=$ACT_ABI_64

ANDROID_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Detect host platform for NDK prebuilt path
UNAME_S=$(uname -s 2>/dev/null || echo "Linux")
case "$UNAME_S" in
    CYGWIN*|MINGW*|MSYS*) HOST_TAG="windows-x86_64" ;;
    Darwin*)              HOST_TAG="darwin-x86_64" ;;
    *)                    HOST_TAG="linux-x86_64" ;;
esac
LOG_DIR="${IJK_LOG_DIR:-$ANDROID_ROOT/build/logs}"
mkdir -p "$LOG_DIR"
LOG_TS="$(date +%Y%m%d_%H%M%S)"
LOG_FILE="${IJK_LOG_FILE:-$LOG_DIR/compile-ijk_${REQUEST_TARGET:-default}_${LOG_TS}.log}"
echo "" > "$LOG_FILE"
echo "[*] log: $LOG_FILE" | tee -a "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1
echo "[*] bash: ${BASH_VERSION:-unknown}"

if [ -z "$ANDROID_NDK" ]; then
    ANDROID_NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
    export ANDROID_NDK
fi

if [ -z "$ANDROID_SDK" ]; then
    ANDROID_SDK="${ANDROID_HOME:-}"
    export ANDROID_SDK
fi

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
    echo "You must define ANDROID_NDK, ANDROID_SDK before starting."
    echo "They must point to your NDK and SDK directories.\n"
    exit 1
fi

find_cmake_bin () {
    if [ -n "$ANDROID_SDK" ] && [ -d "$ANDROID_SDK/cmake" ]; then
        local LATEST
        LATEST=$(ls -1 "$ANDROID_SDK/cmake" | sort -V | tail -n 1)
        if [ -n "$LATEST" ] && [ -x "$ANDROID_SDK/cmake/$LATEST/bin/cmake" ]; then
            echo "$ANDROID_SDK/cmake/$LATEST/bin/cmake"
            return 0
        fi
    fi
    if command -v cmake >/dev/null 2>&1; then
        command -v cmake
        return 0
    fi
    return 1
}

find_ninja_bin () {
    if [ -n "$ANDROID_SDK" ] && [ -d "$ANDROID_SDK/cmake" ]; then
        local LATEST
        LATEST=$(ls -1 "$ANDROID_SDK/cmake" | sort -V | tail -n 1)
        if [ -n "$LATEST" ] && [ -x "$ANDROID_SDK/cmake/$LATEST/bin/ninja" ]; then
            echo "$ANDROID_SDK/cmake/$LATEST/bin/ninja"
            return 0
        fi
    fi
    if command -v ninja >/dev/null 2>&1; then
        command -v ninja
        return 0
    fi
    return 1
}

find_make_bin () {
    if command -v make >/dev/null 2>&1; then
        command -v make
        return 0
    fi
    if command -v gmake >/dev/null 2>&1; then
        command -v gmake
        return 0
    fi
    if command -v mingw32-make >/dev/null 2>&1; then
        command -v mingw32-make
        return 0
    fi
    return 1
}

do_cmake_build () {
    local ABI_NAME=$1
    local SUB_CMD=$2

    if [ "$ABI_NAME" != "arm64" ]; then
        echo "[!] Skipping unsupported ABI: $ABI_NAME (only arm64 is currently supported)"
        return 0
    fi

    local ABI_DIR="arm64-v8a"
    local REPO_ROOT
    REPO_ROOT="$(cd "$ANDROID_ROOT/.." && pwd)"
    local MODULE_DIR="$ANDROID_ROOT/ijkplayer/ijkplayer-arm64"
    local CMAKE_DIR="$MODULE_DIR/src/main/cpp"
    local BUILD_DIR="$MODULE_DIR/build/cmake/$ABI_DIR"
    local OUT_LIB_DIR="$MODULE_DIR/src/main/libs/$ABI_DIR"

    local FFMPEG_SOURCE_DIR="$ANDROID_ROOT/contrib/ffmpeg-arm64"
    local FFMPEG_OUTPUT_DIR="$ANDROID_ROOT/contrib/build/ffmpeg-arm64/output"

    if [ "$SUB_CMD" = "clean" ]; then
        rm -rf "$BUILD_DIR"
        # Also remove compiled .so files so stale binaries don't end up in the APK
        rm -f "$OUT_LIB_DIR/libijkplayer.so" "$OUT_LIB_DIR/libijksdl.so" "$OUT_LIB_DIR/libc++_shared.so"
        echo "[*] cleaned: $BUILD_DIR and $OUT_LIB_DIR/*.so"
        return 0
    fi

    if [ ! -d "$FFMPEG_SOURCE_DIR" ] || [ ! -d "$FFMPEG_OUTPUT_DIR/include" ]; then
        echo "FFmpeg source/output not found."
        echo "Please run: cd android/contrib && ./compile-ffmpeg.sh arm64"
        exit 1
    fi

    local CMAKE_BIN
    CMAKE_BIN="$(find_cmake_bin)" || { echo "cmake not found"; exit 1; }
    local GENERATOR
    local MAKE_BIN
    local MAKE_PROGRAM_ARGS=()
    local NINJA_BIN
    if NINJA_BIN="$(find_ninja_bin)"; then
        GENERATOR="Ninja"
        MAKE_PROGRAM_ARGS=(-DCMAKE_MAKE_PROGRAM="$NINJA_BIN")
    else
        MAKE_BIN="$(find_make_bin)" || { echo "ninja not found and make not found"; exit 1; }
        case "$(basename "$MAKE_BIN")" in
            mingw32-make*)
                GENERATOR="MinGW Makefiles"
            ;;
            *)
                GENERATOR="Unix Makefiles"
            ;;
        esac
        MAKE_PROGRAM_ARGS=(-DCMAKE_MAKE_PROGRAM="$MAKE_BIN")
        echo "[*] ninja not found, fallback to generator: $GENERATOR ($MAKE_BIN)"
    fi

    mkdir -p "$BUILD_DIR"
    mkdir -p "$OUT_LIB_DIR"

    if [ -f "$BUILD_DIR/CMakeCache.txt" ]; then
        # CMake < 3.15 uses CMAKE_HOME_DIRECTORY, newer versions use CMAKE_SOURCE_DIR
        local CACHE_SRC_OK=0
        grep -q "^CMAKE_HOME_DIRECTORY:INTERNAL=$CMAKE_DIR$" "$BUILD_DIR/CMakeCache.txt" 2>/dev/null && CACHE_SRC_OK=1
        grep -q "^CMAKE_SOURCE_DIR:STATIC=$CMAKE_DIR$"      "$BUILD_DIR/CMakeCache.txt" 2>/dev/null && CACHE_SRC_OK=1
        # Also invalidate cache if ANDROID_STL changed
        grep -q "ANDROID_STL.*c++_shared" "$BUILD_DIR/CMakeCache.txt" 2>/dev/null || CACHE_SRC_OK=0
        if [ "$CACHE_SRC_OK" = "0" ]; then
            echo "[*] CMake cache stale (path or STL mismatch), cleaning: $BUILD_DIR"
            rm -rf "$BUILD_DIR"
            mkdir -p "$BUILD_DIR"
        fi
    fi

    "$CMAKE_BIN" -S "$CMAKE_DIR" -B "$BUILD_DIR" -G "$GENERATOR" \
        "${MAKE_PROGRAM_ARGS[@]}" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI_DIR" \
        -DANDROID_PLATFORM=android-21 \
        -DANDROID_STL=c++_shared \
        -DIJK_FFMPEG_SOURCE_DIR="$FFMPEG_SOURCE_DIR" \
        -DIJK_FFMPEG_OUTPUT_DIR="$FFMPEG_OUTPUT_DIR" \
        -DCMAKE_BUILD_TYPE=Release

    "$CMAKE_BIN" --build "$BUILD_DIR" --target ijksdl ijkplayer

    if [ -f "$BUILD_DIR/libijksdl.so" ]; then
        cp -f "$BUILD_DIR/libijksdl.so" "$OUT_LIB_DIR/"
    elif [ -f "$BUILD_DIR/lib/libijksdl.so" ]; then
        cp -f "$BUILD_DIR/lib/libijksdl.so" "$OUT_LIB_DIR/"
    fi

    if [ -f "$BUILD_DIR/libijkplayer.so" ]; then
        cp -f "$BUILD_DIR/libijkplayer.so" "$OUT_LIB_DIR/"
    elif [ -f "$BUILD_DIR/lib/libijkplayer.so" ]; then
        cp -f "$BUILD_DIR/lib/libijkplayer.so" "$OUT_LIB_DIR/"
    fi

    # Copy libc++_shared.so so the APK can find it at runtime.
    # HOST_TAG is detected from uname at the top of this script.
    local LIBCXX_SHARED="$ANDROID_NDK/toolchains/llvm/prebuilt/${HOST_TAG}/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
    if [ -f "$LIBCXX_SHARED" ]; then
        cp -f "$LIBCXX_SHARED" "$OUT_LIB_DIR/"
        echo "[*] copied libc++_shared.so ($HOST_TAG) -> $OUT_LIB_DIR/"
    else
        echo "[!] WARNING: libc++_shared.so not found at $LIBCXX_SHARED"
        echo "[!]          APK may crash at runtime with UnsatisfiedLinkError"
    fi
}


case "$REQUEST_TARGET" in
    "")
        do_cmake_build arm64;
    ;;
    arm64)
        do_cmake_build $REQUEST_TARGET $REQUEST_SUB_CMD;
    ;;
    all|all64)
        for ABI in $ACT_ABI_64; do
            do_cmake_build "$ABI" $REQUEST_SUB_CMD;
        done
    ;;
    clean)
        for ABI in $ACT_ABI_ALL; do
            do_cmake_build "$ABI" clean;
        done
    ;;
    *)
        echo "Usage:"
        echo "  compile-ijk.sh arm64"
        echo "  compile-ijk.sh all"
        echo "  compile-ijk.sh clean"
    ;;
esac
