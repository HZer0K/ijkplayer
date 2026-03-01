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

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_NDK" ]; then
    echo "You must define ANDROID_NDK, ANDROID_SDK before starting."
    echo "They must point to your NDK and SDK directories.\n"
    exit 1
fi

REQUEST_TARGET=$1
REQUEST_SUB_CMD=$2
ACT_ABI_32=""
ACT_ABI_64="arm64"
ACT_ABI_ALL=$ACT_ABI_64
UNAME_S=$(uname -s)

FF_MAKEFLAGS=
if which nproc >/dev/null
then
    FF_MAKEFLAGS=-j`nproc`
elif [ "$UNAME_S" = "Darwin" ] && which sysctl >/dev/null
then
    FF_MAKEFLAGS=-j`sysctl -n machdep.cpu.thread_count`
fi

do_sub_cmd () {
    SUB_CMD=$1
    rm -rf android-ndk-prof

    if [ "$PARAM_SUB_CMD" = 'prof' ]; then
        echo 'profiler build: YES';
        mkdir -p android-ndk-prof
        cp -r ../../../../../../ijkprof/android-ndk-profiler/jni/* android-ndk-prof/
    else
        echo 'profiler build: NO';
        mkdir -p android-ndk-prof
        cp -r ../../../../../../ijkprof/android-ndk-profiler-dummy/jni/* android-ndk-prof/
    fi

    # ensure ndk-build outputs to src/main/obj and src/main/libs
    NDK_OUT_DIR="$(pwd)/../obj"
    NDK_LIBS_DIR="$(pwd)/../libs"

    NDK_OUT_ARG="$NDK_OUT_DIR"
    NDK_LIBS_ARG="$NDK_LIBS_DIR"
    USE_CMD=0
    if [ -x "$ANDROID_NDK/ndk-build" ]; then
        NDK_BUILD_SH="$ANDROID_NDK/ndk-build"
    elif [ -f "$ANDROID_NDK/ndk-build.cmd" ]; then
        USE_CMD=1
        if command -v cygpath >/dev/null 2>&1; then
            NDK_BUILD_WIN="$(cygpath -am "$ANDROID_NDK/ndk-build.cmd")"
            NDK_OUT_ARG="$(cygpath -am "$NDK_OUT_DIR")"
            NDK_LIBS_ARG="$(cygpath -am "$NDK_LIBS_DIR")"
        else
            NDK_BUILD_WIN="$ANDROID_NDK/ndk-build.cmd"
        fi
    else
        echo "Cannot find ndk-build in ANDROID_NDK. Please ensure ANDROID_NDK is correct."
        exit 1
    fi

    case $SUB_CMD in
        prof)
            if [ $USE_CMD -eq 1 ]; then
                WIN_PWSH="powershell.exe -NoProfile -Command"
                CMD_STR="& '$NDK_BUILD_WIN' NDK_OUT='$NDK_OUT_ARG' NDK_LIBS_OUT='$NDK_LIBS_ARG' $FF_MAKEFLAGS"
                $WIN_PWSH "$CMD_STR"
            else
                "$NDK_BUILD_SH" NDK_OUT="$NDK_OUT_ARG" NDK_LIBS_OUT="$NDK_LIBS_ARG" $FF_MAKEFLAGS
            fi
        ;;
        clean)
            if [ $USE_CMD -eq 1 ]; then
                WIN_PWSH="powershell.exe -NoProfile -Command"
                CMD_STR="& '$NDK_BUILD_WIN' NDK_OUT='$NDK_OUT_ARG' NDK_LIBS_OUT='$NDK_LIBS_ARG' clean"
                $WIN_PWSH "$CMD_STR"
            else
                "$NDK_BUILD_SH" NDK_OUT="$NDK_OUT_ARG" NDK_LIBS_OUT="$NDK_LIBS_ARG" clean
            fi
        ;;
        rebuild)
            if [ $USE_CMD -eq 1 ]; then
                WIN_PWSH="powershell.exe -NoProfile -Command"
                CMD_STR="& '$NDK_BUILD_WIN' NDK_OUT='$NDK_OUT_ARG' NDK_LIBS_OUT='$NDK_LIBS_ARG' clean"
                $WIN_PWSH "$CMD_STR"
                CMD_STR="& '$NDK_BUILD_WIN' NDK_OUT='$NDK_OUT_ARG' NDK_LIBS_OUT='$NDK_LIBS_ARG' $FF_MAKEFLAGS"
                $WIN_PWSH "$CMD_STR"
            else
                "$NDK_BUILD_SH" NDK_OUT="$NDK_OUT_ARG" NDK_LIBS_OUT="$NDK_LIBS_ARG" clean
                "$NDK_BUILD_SH" NDK_OUT="$NDK_OUT_ARG" NDK_LIBS_OUT="$NDK_LIBS_ARG" $FF_MAKEFLAGS
            fi
        ;;
        *)
            if [ $USE_CMD -eq 1 ]; then
                WIN_PWSH="powershell.exe -NoProfile -Command"
                CMD_STR="& '$NDK_BUILD_WIN' NDK_OUT='$NDK_OUT_ARG' NDK_LIBS_OUT='$NDK_LIBS_ARG' $FF_MAKEFLAGS"
                $WIN_PWSH "$CMD_STR"
            else
                "$NDK_BUILD_SH" NDK_OUT="$NDK_OUT_ARG" NDK_LIBS_OUT="$NDK_LIBS_ARG" $FF_MAKEFLAGS
            fi
        ;;
    esac
}

do_ndk_build () {
    PARAM_TARGET=$1
    PARAM_SUB_CMD=$2
    case "$PARAM_TARGET" in
        arm64)
            cd "ijkplayer/ijkplayer-$PARAM_TARGET/src/main/jni"
            if [ "$PARAM_SUB_CMD" = 'prof' ]; then PARAM_SUB_CMD=''; fi
            do_sub_cmd $PARAM_SUB_CMD
            ABI_DIR="arm64-v8a"
            OBJ_LIB_DIR="$(pwd)/../obj/local/$ABI_DIR"
            OUT_LIB_DIR="$(pwd)/../libs/$ABI_DIR"
            mkdir -p "$OUT_LIB_DIR"
            if ls "$OBJ_LIB_DIR"/lib*.so 1> /dev/null 2>&1; then
                cp -f "$OBJ_LIB_DIR"/lib*.so "$OUT_LIB_DIR"/
            fi
            cd -
        ;;
    esac
}


case "$REQUEST_TARGET" in
    "")
        do_ndk_build arm64;
    ;;
    arm64)
        do_ndk_build $REQUEST_TARGET $REQUEST_SUB_CMD;
    ;;
    all|all64)
        for ABI in $ACT_ABI_64
        do
            do_ndk_build "$ABI" $REQUEST_SUB_CMD;
        done
    ;;
    clean)
        for ABI in $ACT_ABI_ALL
        do
            do_ndk_build "$ABI" clean;
        done
    ;;
    *)
        echo "Usage:"
        echo "  compile-ijk.sh arm64"
        echo "  compile-ijk.sh all"
        echo "  compile-ijk.sh clean"
    ;;
esac

