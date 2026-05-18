#!/bin/bash
# compile-mnn.sh
# Compile MNN for Android

set -e

IJKPLAYER_ROOT=$(cd "$(dirname "$0")/../.."; pwd)
MNN_ROOT="$IJKPLAYER_ROOT/extra/MNN"
BUILD_ROOT="$IJKPLAYER_ROOT/android/contrib/build"

echo "IJKPLAYER_ROOT: $IJKPLAYER_ROOT"
echo "MNN_ROOT: $MNN_ROOT"

# Check if MNN exists
if [ ! -d "$MNN_ROOT" ]; then
    echo "Error: MNN not found. Run init-android-mnn.sh first."
    exit 1
fi

# Check if ANDROID_NDK is set
if [ -z "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK not set"
    exit 1
fi

echo "=== Compile MNN for Android ==="

ARCHS=("arm64")
ABI_MAP=("arm64-v8a")

for i in "${!ARCHS[@]}"; do
    ARCH="${ARCHS[$i]}"
    ABI="${ABI_MAP[$i]}"

    echo "=== Compile MNN for $ARCH ==="

    BUILD_DIR="$BUILD_ROOT/mnn-$ARCH"
    OUTPUT_DIR="$BUILD_DIR/output"

    mkdir -p "$BUILD_DIR"
    mkdir -p "$OUTPUT_DIR/lib"
    mkdir -p "$OUTPUT_DIR/include"

    cd "$BUILD_DIR"

    # CMake configuration
    cmake "$MNN_ROOT" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-24" \
        -DCMAKE_BUILD_TYPE=Release \
        -DMNN_BUILD_TRAIN=OFF \
        -DMNN_BUILD_DEMO=OFF \
        -DMNN_BUILD_TOOLS=OFF \
        -DMNN_BUILD_OPENCV=ON \
        -DMNN_OPENCL=ON \
        -DMNN_VULKAN=ON \
        -DMNN_SEPARATE_LIBS=OFF \
        -DMNN_USE_THREAD_POOL=ON \
        -DMNN_BUILD_SHARED_LIBS=ON \
        -DCMAKE_INSTALL_PREFIX="$OUTPUT_DIR"

    # Build
    cmake --build . --target MNN -j$(nproc)

    # Copy output artifacts
    echo "=== Copying MNN artifacts for $ARCH ==="

    # Copy headers
    cp -r "$MNN_ROOT/include/MNN" "$OUTPUT_DIR/include/"

    # Copy libraries
    find "$BUILD_DIR" -name "libMNN*" -type f | while read f; do
        cp "$f" "$OUTPUT_DIR/lib/"
        echo "  copied: $f"
    done

    echo "=== MNN for $ARCH compiled ==="
    echo "Output: $OUTPUT_DIR"
done

echo "=== All MNN builds completed ==="
