#! /usr/bin/env bash
#
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

# This script is based on projects below
# https://github.com/yixia/FFmpeg-Android
# http://git.videolan.org/?p=vlc-ports/android.git;a=summary

#----------
UNI_BUILD_ROOT=`pwd`
FF_TARGET=$1
FF_TARGET_EXTRA=$2
set -e
set +x

LOG_DIR="${IJK_LOG_DIR:-$UNI_BUILD_ROOT/build/logs}"
mkdir -p "$LOG_DIR"
LOG_TS="$(date +%Y%m%d_%H%M%S)"
LOG_FILE="${IJK_LOG_FILE:-$LOG_DIR/compile-ffmpeg_${FF_TARGET:-default}_${LOG_TS}.log}"
exec > >(tee -a "$LOG_FILE") 2>&1
echo "[*] log: $LOG_FILE"

FF_ACT_ARCHS_32=""
FF_ACT_ARCHS_64="arm64"
FF_ACT_ARCHS_ALL=$FF_ACT_ARCHS_64

maybe_build_openssl() {
    local ARCH="$1"
    if [ "${IJK_ENABLE_OPENSSL:-1}" != "1" ]; then
        return 0
    fi
    local SSL_LIB="./build/openssl-${ARCH}/output/lib/libssl.a"
    if [ -f "$SSL_LIB" ]; then
        return 0
    fi
    echo ""
    echo "--------------------"
    echo "[*] build openssl for $ARCH (HTTPS support)"
    echo "--------------------"
    bash compile-openssl.sh "$ARCH"
}

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
    echo "  compile-ffmpeg.sh arm64"
    echo "  compile-ffmpeg.sh all"
    echo "  compile-ffmpeg.sh clean"
    echo "  compile-ffmpeg.sh check"
    exit 1
}

echo_nextstep_help() {
    echo ""
    echo "--------------------"
    echo "[*] Finished"
    echo "--------------------"
    echo "# to continue to build ijkplayer, run script below,"
    echo "sh compile-ijk.sh "
}

#----------
case "$FF_TARGET" in
    "")
        echo_archs arm64
        maybe_build_openssl arm64
        bash tools/do-compile-ffmpeg.sh arm64
    ;;
    arm64)
        echo_archs $FF_TARGET $FF_TARGET_EXTRA
        maybe_build_openssl $FF_TARGET
        bash tools/do-compile-ffmpeg.sh $FF_TARGET $FF_TARGET_EXTRA
        echo_nextstep_help
    ;;
    all|all64)
        echo_archs $FF_ACT_ARCHS_64
        for ARCH in $FF_ACT_ARCHS_64
        do
            maybe_build_openssl $ARCH
            bash tools/do-compile-ffmpeg.sh $ARCH $FF_TARGET_EXTRA
        done
        echo_nextstep_help
    ;;
    clean)
        echo_archs FF_ACT_ARCHS_64
        for ARCH in $FF_ACT_ARCHS_ALL
        do
            if [ -d ffmpeg-$ARCH ]; then
                cd ffmpeg-$ARCH && git clean -xdf && cd -
            fi
        done
        rm -rf ./build/ffmpeg-*
    ;;
    check)
        echo_archs FF_ACT_ARCHS_ALL
    ;;
    *)
        echo_usage
        exit 1
    ;;
esac
