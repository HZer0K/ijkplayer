#! /usr/bin/env bash
#
# Copyright (C) 2014 Miguel Botón <waninkoko@gmail.com>
# Copyright (C) 2014 Zhang Rui <bbcallen@gmail.com>
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

#--------------------
set -e

if [ -z "$ANDROID_NDK" ]; then
    echo "You must define ANDROID_NDK before starting."
    echo "They must point to your NDK directories."
    echo ""
    exit 1
fi

FF_ARCH=$1
if [ -z "$FF_ARCH" ]; then
    echo "You must specific an architecture 'arm64'."
    echo ""
    exit 1
fi

FF_BUILD_ROOT=$(pwd)

. ./tools/do-detect-env.sh

OPENSSL_VER=${OPENSSL_VER:-3.3.2}
OPENSSL_TAR="$FF_BUILD_ROOT/openssl-${OPENSSL_VER}.tar.gz"
OPENSSL_URL="https://www.openssl.org/source/openssl-${OPENSSL_VER}.tar.gz"
OPENSSL_SRC_DIR="$FF_BUILD_ROOT/openssl-${OPENSSL_VER}"

FF_BUILD_NAME="openssl-${FF_ARCH}"
FF_PREFIX="$FF_BUILD_ROOT/build/${FF_BUILD_NAME}/output"
FF_WORK_DIR="$FF_BUILD_ROOT/build/${FF_BUILD_NAME}/src"

API_LEVEL=${OPENSSL_API_LEVEL:-24}

OPENSSL_TARGET=
case "$FF_ARCH" in
    arm64)
        OPENSSL_TARGET="android-arm64"
    ;;
    *)
        echo "Unsupported architecture: $FF_ARCH (supported: arm64)"
        exit 1
    ;;
esac

mkdir -p "$FF_PREFIX"

if [ ! -d "$OPENSSL_SRC_DIR" ]; then
    echo ""
    echo "--------------------"
    echo "[*] fetch OpenSSL ${OPENSSL_VER} source"
    echo "--------------------"
    if command -v curl >/dev/null 2>&1; then
        curl -L "$OPENSSL_URL" -o "$OPENSSL_TAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$OPENSSL_TAR" "$OPENSSL_URL"
    else
        echo "No curl/wget found to download OpenSSL"
        exit 1
    fi
    tar -xzf "$OPENSSL_TAR" -C "$FF_BUILD_ROOT"
fi

rm -rf "$FF_WORK_DIR"
mkdir -p "$FF_WORK_DIR"
if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete "$OPENSSL_SRC_DIR/" "$FF_WORK_DIR/"
else
    cp -R "$OPENSSL_SRC_DIR/." "$FF_WORK_DIR/"
fi

export ANDROID_NDK_HOME="$ANDROID_NDK"
export ANDROID_NDK_ROOT="$ANDROID_NDK"
export PATH="$IJK_LLVM_BIN:$PATH"
export AR="$IJK_LLVM_BIN/llvm-ar"
export RANLIB="$IJK_LLVM_BIN/llvm-ranlib"
export STRIP="$IJK_LLVM_BIN/llvm-strip"

echo ""
echo "--------------------"
echo "[*] configurate openssl ($FF_ARCH)"
echo "--------------------"
cd "$FF_WORK_DIR"

if [ -f "./Makefile" ]; then
    make clean || true
fi

echo "./Configure $OPENSSL_TARGET -D__ANDROID_API__=$API_LEVEL no-shared no-tests --prefix=$FF_PREFIX --openssldir=$FF_PREFIX"
./Configure "$OPENSSL_TARGET" -D__ANDROID_API__="$API_LEVEL" no-shared no-tests --prefix="$FF_PREFIX" --openssldir="$FF_PREFIX"

echo ""
echo "--------------------"
echo "[*] compile openssl ($FF_ARCH)"
echo "--------------------"
if command -v nproc >/dev/null 2>&1; then
    make -j"$(nproc)"
else
    make -j4
fi
make install_sw

echo ""
echo "--------------------"
echo "[*] OpenSSL output: $FF_PREFIX"
echo "--------------------"
