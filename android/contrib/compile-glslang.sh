#! /usr/bin/env bash
#
# Licensed under the Apache License, Version 2.0
#

UNI_BUILD_ROOT=`pwd`
FF_TARGET=$1
set -e
set +x

if [ -z "$ANDROID_NDK" ]; then
    ANDROID_NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
    export ANDROID_NDK
fi

LOG_DIR="${IJK_LOG_DIR:-$UNI_BUILD_ROOT/build/logs}"
mkdir -p "$LOG_DIR"
LOG_TS="$(date +%Y%m%d_%H%M%S)"
LOG_FILE="${IJK_LOG_FILE:-$LOG_DIR/compile-glslang_${FF_TARGET:-default}_${LOG_TS}.log}"
exec > >(tee -a "$LOG_FILE") 2>&1
echo "[*] log: $LOG_FILE"

FF_ACT_ARCHS_64="arm64"
FF_ACT_ARCHS_ALL=$FF_ACT_ARCHS_64

echo_archs() {
    echo "===================="
    echo "[*] check archs"
    echo "===================="
    echo "FF_ALL_ARCHS = $FF_ACT_ARCHS_ALL"
    echo "FF_ACT_ARCHS = $*"
    echo ""
}

echo_usage() {
    echo "Usage:"
    echo "  compile-glslang.sh arm64"
    echo "  compile-glslang.sh all|all64"
    echo "  compile-glslang.sh clean"
    echo ""
    echo "  Note: glslang is only needed when IJK_ENABLE_VULKAN_FILTERS=1."
    echo "        compile-ffmpeg.sh will call this automatically when that flag is set."
    exit 1
}

echo_nextstep_help() {
    echo ""
    echo "--------------------"
    echo "[*] Finished"
    echo "--------------------"
    echo "# to continue to build ffmpeg (with Vulkan filters), run from android/contrib/ directory:"
    echo "#   IJK_ENABLE_VULKAN_FILTERS=1 ./compile-ffmpeg.sh"
}

case "$FF_TARGET" in
    "")
        echo_archs arm64
        bash tools/do-compile-glslang.sh arm64
        echo_nextstep_help
    ;;
    arm64)
        echo_archs $FF_TARGET
        bash tools/do-compile-glslang.sh $FF_TARGET
        echo_nextstep_help
    ;;
    all|all64)
        echo_archs $FF_ACT_ARCHS_64
        for ARCH in $FF_ACT_ARCHS_64
        do
            bash tools/do-compile-glslang.sh $ARCH
        done
        echo_nextstep_help
    ;;
    clean)
        echo_archs FF_ACT_ARCHS_ALL
        rm -rf ./build/glslang-*
        rm -rf ./glslang-*/
        echo "[*] glslang build output cleaned"
    ;;
    *)
        echo_usage
        exit 1
    ;;
esac

