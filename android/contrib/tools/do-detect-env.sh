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

#--------------------
set -e

UNAME_S=$(uname -s)
UNAME_SM=$(uname -sm)
echo "build on $UNAME_SM"

if [ -z "$ANDROID_NDK" ]; then
    ANDROID_NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
    export ANDROID_NDK
fi

echo "ANDROID_NDK=$ANDROID_NDK"

if [ -z "$ANDROID_NDK" ]; then
    echo "You must define ANDROID_NDK before starting."
    echo "They must point to your NDK directories."
    echo ""
    exit 1
fi

export ANDROID_NDK_ROOT="$ANDROID_NDK"

case "$UNAME_S" in
    CYGWIN_NT-*|MINGW64_NT*|MINGW32_NT*)
        if command -v cygpath >/dev/null 2>&1; then
            ANDROID_NDK="$(cygpath -u "$ANDROID_NDK")"
            export ANDROID_NDK
            export ANDROID_NDK_ROOT="$ANDROID_NDK"
            echo "ANDROID_NDK(normalized)=$ANDROID_NDK"
        else
            if cd "$ANDROID_NDK" >/dev/null 2>&1; then
                ANDROID_NDK="$(pwd -P)"
                export ANDROID_NDK
                export ANDROID_NDK_ROOT="$ANDROID_NDK"
                echo "ANDROID_NDK(normalized)=$ANDROID_NDK"
            fi
        fi
    ;;
esac


# make flags (parallel build)
export IJK_MAKE_FLAG=
case "$UNAME_S" in
    Darwin)
        export IJK_MAKE_FLAG=-j`sysctl -n machdep.cpu.thread_count`
    ;;
    Linux)
        if command -v nproc >/dev/null 2>&1; then
            export IJK_MAKE_FLAG=-j`nproc`
        fi
    ;;
    CYGWIN_NT-*)
        :
    ;;
esac

if command -v make >/dev/null 2>&1; then
    export IJK_MAKE=make
elif command -v gmake >/dev/null 2>&1; then
    export IJK_MAKE=gmake
elif command -v mingw32-make >/dev/null 2>&1; then
    export IJK_MAKE=mingw32-make
else
    echo "make not found in PATH."
    echo "Please install make and re-run."
    echo "Windows (recommended): install MSYS2 and run in MSYS2 MinGW64 shell, then: pacman -S --needed base-devel make"
    exit 1
fi
echo "IJK_MAKE=$IJK_MAKE"

# detect NDK (LLVM toolchain)
export IJK_NDK_REL=$(grep -o '^Pkg\.Revision.*=[0-9]*.*' "$ANDROID_NDK/source.properties" 2>/dev/null | sed 's/[[:space:]]*//g' | cut -d "=" -f 2)
echo "IJK_NDK_REL=$IJK_NDK_REL"

# host tag for prebuilt llvm
HOST_TAG=
case "$UNAME_S" in
    Darwin)
        HOST_TAG=darwin-x86_64
    ;;
    Linux)
        HOST_TAG=linux-x86_64
    ;;
    CYGWIN_NT-*)
        HOST_TAG=windows-x86_64
    ;;
    MINGW64_NT*|MINGW32_NT*)
        HOST_TAG=windows-x86_64
    ;;
    *)
        echo "Unknown host OS: $UNAME_S"
        exit 1
    ;;
esac

LLVM_PREBUILT="$ANDROID_NDK/toolchains/llvm/prebuilt/$HOST_TAG"
if [ ! -d "$LLVM_PREBUILT" ]; then
    echo "Can not find LLVM toolchain at: $LLVM_PREBUILT"
    echo "Please ensure ANDROID_NDK is correct and contains toolchains/llvm/prebuilt/$HOST_TAG"
    exit 1
fi

export IJK_LLVM_PREBUILT="$LLVM_PREBUILT"
export IJK_LLVM_BIN="$LLVM_PREBUILT/bin"
export IJK_LLVM_SYSROOT="$LLVM_PREBUILT/sysroot"

case "$UNAME_S" in
    CYGWIN_NT-*)
        IJK_WIN_TEMP="$(cygpath -am /tmp)"
        export TEMPDIR=$IJK_WIN_TEMP/
        echo "Cygwin temp prefix=$IJK_WIN_TEMP/"
    ;;
esac
