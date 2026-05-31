#!/bin/bash
# compile-llama.sh
# Compile llama.cpp for Android
#
# 默认只编译arm64架构. 设置 IJK_LLAMA_BUILD_ALL=1 编译所有架构:
#   IJK_LLAMA_BUILD_ALL=1 ./compile-llama.sh

set -e

IJKPLAYER_ROOT=$(cd "$(dirname "$0")/../.."; pwd)
LLAMA_ROOT="$IJKPLAYER_ROOT/extra/llama.cpp"
BUILD_ROOT="$IJKPLAYER_ROOT/android/contrib/build"

# Debug output
echo "IJKPLAYER_ROOT: $IJKPLAYER_ROOT"
echo "LLAMA_ROOT: $LLAMA_ROOT"

# Check if llama.cpp exists
if [ ! -d "$LLAMA_ROOT" ]; then
    echo "Error: llama.cpp not found. Run init-android-llama.sh first."
    exit 1
fi

# Check if ANDROID_NDK is set
if [ -z "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK not set"
    exit 1
fi

echo "=== Compile llama.cpp for Android ==="

if [ "${IJK_LLAMA_BUILD_ALL:-0}" = "1" ]; then
    # 全架构编译(通过环境变量IJK_LLAMA_BUILD_ALL=1启用)
    ARCHS=("arm64" "armv7a" "x86" "x86_64")
    ABI_MAP=(
        "arm64-v8a"
        "armeabi-v7a"
        "x86"
        "x86_64"
    )
else
    # 默认只编译arm64
    ARCHS=("arm64")
    ABI_MAP=("arm64-v8a")
fi

for i in "${!ARCHS[@]}"; do
    ARCH="${ARCHS[$i]}"
    ABI="${ABI_MAP[$i]}"
    
    echo "=== Compile llama.cpp for $ARCH ($ABI) ==="
    
    BUILD_DIR="$BUILD_ROOT/llama-$ARCH"
    OUTPUT_DIR="$BUILD_DIR/output"
    
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"
    
    # CMake configuration
    cmake "$LLAMA_ROOT" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-21" \
        -DCMAKE_BUILD_TYPE=Release \
        -DLLAMA_BUILD_TESTS=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF \
        -DLLAMA_ARM_NEON=ON \
        -DGGML_OPENMP=OFF \
        -DCMAKE_C_FLAGS="-D__USE_POSIX199309" \
        -DCMAKE_CXX_FLAGS="-D__USE_POSIX199309" \
        -DCMAKE_INSTALL_PREFIX="$OUTPUT_DIR"
    
    # Build
    make -j$(nproc)
    make install
    
    echo "=== llama.cpp for $ARCH compiled ==="
    echo "Output: $OUTPUT_DIR"
done

echo "=== All llama.cpp builds completed ==="
