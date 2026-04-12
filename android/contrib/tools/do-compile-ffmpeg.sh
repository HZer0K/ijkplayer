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
echo "===================="
echo "[*] check env $1"
echo "===================="
set -e


#--------------------
# common defines
FF_ARCH=$1
FF_BUILD_OPT=$2
echo "FF_ARCH=$FF_ARCH"
echo "FF_BUILD_OPT=$FF_BUILD_OPT"
if [ -z "$FF_ARCH" ]; then
    echo "You must specific an architecture 'arm, armv7a, x86, ...'."
    echo ""
    exit 1
fi


FF_BUILD_ROOT=`pwd`
FF_ANDROID_PLATFORM=android-9


FF_BUILD_NAME=
FF_SOURCE=
FF_CROSS_PREFIX=
FF_DEP_OPENSSL_INC=
FF_DEP_OPENSSL_LIB=

FF_DEP_LIBSOXR_INC=
FF_DEP_LIBSOXR_LIB=

FF_CFG_FLAGS=

FF_EXTRA_CFLAGS=
FF_EXTRA_LDFLAGS=
FF_DEP_LIBS=

FF_MODULE_DIRS="compat libavcodec libavfilter libavformat libavutil libswresample libswscale"
FF_ASSEMBLER_SUB_DIRS=


#--------------------
echo ""
echo "--------------------"
echo "[*] make NDK standalone toolchain"
echo "--------------------"
. ./tools/do-detect-env.sh
FF_MAKE_FLAGS=$IJK_MAKE_FLAG


#----- armv7a begin -----
if [ "$FF_ARCH" = "armv7a" ]; then
    FF_BUILD_NAME=ffmpeg-armv7a
    FF_BUILD_NAME_OPENSSL=openssl-armv7a
    FF_BUILD_NAME_LIBSOXR=libsoxr-armv7a
    FF_SOURCE=$FF_BUILD_ROOT/$FF_BUILD_NAME
    API_LEVEL=21
    TARGET_TRIPLE=armv7a-linux-androideabi${API_LEVEL}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=arm --cpu=cortex-a8"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-neon"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-thumb"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=armv7-a -mcpu=cortex-a8 -mfpu=vfpv3-d16 -mfloat-abi=softfp -mthumb"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS -Wl,--fix-cortex-a8"

    FF_ASSEMBLER_SUB_DIRS="arm"

elif [ "$FF_ARCH" = "armv5" ]; then
    echo "armv5 is not supported by modern NDK (r27)."
    echo "Please build armv7a instead."
    exit 1

elif [ "$FF_ARCH" = "x86" ]; then
    FF_BUILD_NAME=ffmpeg-x86
    FF_BUILD_NAME_OPENSSL=openssl-x86
    FF_BUILD_NAME_LIBSOXR=libsoxr-x86
    FF_SOURCE=$FF_BUILD_ROOT/$FF_BUILD_NAME
    API_LEVEL=21
    TARGET_TRIPLE=i686-linux-android${API_LEVEL}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=x86 --cpu=i686 --enable-yasm"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-x86asm --disable-asm"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=atom -msse3 -ffast-math -mfpmath=sse"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS=""

elif [ "$FF_ARCH" = "x86_64" ]; then
    API_LEVEL=21

    FF_BUILD_NAME=ffmpeg-x86_64
    FF_BUILD_NAME_OPENSSL=openssl-x86_64
    FF_BUILD_NAME_LIBSOXR=libsoxr-x86_64
    FF_SOURCE=$FF_BUILD_ROOT/$FF_BUILD_NAME
    TARGET_TRIPLE=x86_64-linux-android${API_LEVEL}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=x86_64 --enable-yasm"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-x86asm --disable-asm"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS=""

elif [ "$FF_ARCH" = "arm64" ]; then
    API_LEVEL=24

    FF_BUILD_NAME=ffmpeg-arm64
    FF_BUILD_NAME_OPENSSL=openssl-arm64
    FF_BUILD_NAME_LIBSOXR=libsoxr-arm64
    FF_SOURCE=$FF_BUILD_ROOT/$FF_BUILD_NAME
    TARGET_TRIPLE=aarch64-linux-android${API_LEVEL}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=aarch64"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-asm"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-x86asm"
    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS=""


else
    echo "unknown architecture $FF_ARCH";
    exit 1
fi

if [ ! -d $FF_SOURCE ]; then
    echo ""
    echo "[*] fetch FFmpeg 8.0 source"
    echo ""
    FF_VER=8.0
    FF_TAR=$FF_BUILD_ROOT/ffmpeg-$FF_VER.tar.gz
    FF_URL=https://ffmpeg.org/releases/ffmpeg-$FF_VER.tar.gz
    if command -v curl >/dev/null 2>&1; then
        curl -L "$FF_URL" -o "$FF_TAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$FF_TAR" "$FF_URL"
    else
        if [ "$UNAME_S" = "CYGWIN_NT-" ] || [[ "$UNAME_S" == MINGW* ]]; then
            powershell.exe -NoProfile -Command "Invoke-WebRequest -Uri '$FF_URL' -OutFile '$FF_TAR'"
        else
            echo "No curl/wget found to download FFmpeg"
            exit 1
        fi
    fi
    tar -xzf "$FF_TAR" -C "$FF_BUILD_ROOT"
    mv "$FF_BUILD_ROOT/ffmpeg-$FF_VER" "$FF_SOURCE"
fi

FF_TOOLCHAIN_PATH=$FF_BUILD_ROOT/build/$FF_BUILD_NAME/toolchain
FF_SYSROOT=$IJK_LLVM_SYSROOT
FF_PREFIX=$FF_BUILD_ROOT/build/$FF_BUILD_NAME/output
FF_DEP_OPENSSL_INC=$FF_BUILD_ROOT/build/$FF_BUILD_NAME_OPENSSL/output/include
FF_DEP_OPENSSL_LIB=$FF_BUILD_ROOT/build/$FF_BUILD_NAME_OPENSSL/output/lib
FF_DEP_LIBSOXR_INC=$FF_BUILD_ROOT/build/$FF_BUILD_NAME_LIBSOXR/output/include
FF_DEP_LIBSOXR_LIB=$FF_BUILD_ROOT/build/$FF_BUILD_NAME_LIBSOXR/output/lib

case "$UNAME_S" in
    CYGWIN_NT-*)
        FF_SYSROOT="$(cygpath -am $FF_SYSROOT)"
        FF_PREFIX="$(cygpath -am $FF_PREFIX)"
    ;;
esac


mkdir -p $FF_PREFIX


#--------------------
echo ""
echo "--------------------"
echo "[*] check ffmpeg env"
echo "--------------------"
export PATH=$IJK_LLVM_BIN:$PATH
export CC="$IJK_LLVM_BIN/clang --target=$TARGET_TRIPLE --sysroot=$FF_SYSROOT"
export CXX="$IJK_LLVM_BIN/clang++ --target=$TARGET_TRIPLE --sysroot=$FF_SYSROOT"
export LD="$IJK_LLVM_BIN/ld.lld"
export AR="$IJK_LLVM_BIN/llvm-ar"
export STRIP="$IJK_LLVM_BIN/llvm-strip"
export NM="$IJK_LLVM_BIN/llvm-nm"
export RANLIB="$IJK_LLVM_BIN/llvm-ranlib"
export ASFLAGS="--target=$TARGET_TRIPLE --sysroot=$FF_SYSROOT -fPIC -DPIC"
export CCAS="$CC"
export CCASFLAGS="--target=$TARGET_TRIPLE --sysroot=$FF_SYSROOT -fPIC -DPIC"
export AS="$CC"
export YASMFLAGS="-DPIC"

FF_CFLAGS="-O3 -Wall -pipe \
    -std=c99 \
    -ffast-math \
    -fstrict-aliasing -Werror=strict-aliasing \
    -Wno-psabi -Wa,--noexecstack \
    -DANDROID -DNDEBUG"
FF_CFLAGS="$FF_CFLAGS -fPIC"
FF_CFLAGS="$FF_CFLAGS -Wno-int-conversion -Wno-unused-variable -Wno-incompatible-function-pointer-types"
FF_CFLAGS="$FF_CFLAGS --target=$TARGET_TRIPLE --sysroot=$FF_SYSROOT"
FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS --target=$TARGET_TRIPLE --sysroot=$FF_SYSROOT -fuse-ld=lld"

# add API-specific library search (-B) to satisfy crtbegin/crtend
case "$FF_ARCH" in
    armv7a)
        FF_API_LIB_DIR="$FF_SYSROOT/usr/lib/arm-linux-androideabi/$API_LEVEL"
    ;;
    arm64)
        FF_API_LIB_DIR="$FF_SYSROOT/usr/lib/aarch64-linux-android/$API_LEVEL"
    ;;
    x86)
        FF_API_LIB_DIR="$FF_SYSROOT/usr/lib/i686-linux-android/$API_LEVEL"
    ;;
    x86_64)
        FF_API_LIB_DIR="$FF_SYSROOT/usr/lib/x86_64-linux-android/$API_LEVEL"
    ;;
esac
FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS -B$FF_API_LIB_DIR"

# cause av_strlcpy crash with gcc4.7, gcc4.8
# -fmodulo-sched -fmodulo-sched-allow-regmoves

# --enable-thumb is OK
#FF_CFLAGS="$FF_CFLAGS -mthumb"

# not necessary
#FF_CFLAGS="$FF_CFLAGS -finline-limit=300"

export COMMON_FF_CFG_FLAGS=

FF_ENABLE_VULKAN="${IJK_ENABLE_VULKAN:-1}"
FF_ENABLE_VULKAN_FILTERS="${IJK_ENABLE_VULKAN_FILTERS:-}"

FF_DEP_GLSLANG_INC="$FF_BUILD_ROOT/build/glslang-arm64/output/include"
FF_DEP_GLSLANG_LIB="$FF_BUILD_ROOT/build/glslang-arm64/output/lib"
# NOTE: Vulkan filters require spirv_compiler (libglslang), but FFmpeg configure's
# detection links against -lpthread/-lstdc++ which Android NDK does not provide.
# Keep filters disabled by default; set IJK_ENABLE_VULKAN_FILTERS=1 only if you
# have a host-compatible SPIRV-Tools build.
# (Previously auto-enabled here; removed to prevent configure failure)
. $FF_BUILD_ROOT/../../config/module.sh


#--------------------
# with openssl
if [ -f "${FF_DEP_OPENSSL_LIB}/libssl.a" ]; then
    echo "OpenSSL detected"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-nonfree"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-openssl"

    FF_CFLAGS="$FF_CFLAGS -I${FF_DEP_OPENSSL_INC}"
    FF_DEP_LIBS="$FF_DEP_LIBS -L${FF_DEP_OPENSSL_LIB} -lssl -lcrypto"
fi

if [ -f "${FF_DEP_LIBSOXR_LIB}/libsoxr.a" ]; then
    echo "libsoxr detected"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-libsoxr"

    FF_CFLAGS="$FF_CFLAGS -I${FF_DEP_LIBSOXR_INC}"
    FF_DEP_LIBS="$FF_DEP_LIBS -L${FF_DEP_LIBSOXR_LIB} -lsoxr"
fi

FF_CFG_FLAGS="$FF_CFG_FLAGS $COMMON_FF_CFG_FLAGS"

# Vulkan support (enable Vulkan filters; depends on spirv_compiler + Vulkan-Headers)
FF_ENABLE_VULKAN="${IJK_ENABLE_VULKAN:-1}"
if [ "$FF_ENABLE_VULKAN" = "1" ]; then
    # Ensure Vulkan-Headers are available even if NDK headers are missing vk_video/*
    VULKAN_HEADERS_VER=1.3.280
    VULKAN_HEADERS_ROOT="$FF_BUILD_ROOT/build/vulkan-headers"
    VULKAN_HEADERS_DIR="$VULKAN_HEADERS_ROOT/Vulkan-Headers-$VULKAN_HEADERS_VER"
    VH_TAR="$VULKAN_HEADERS_ROOT/Vulkan-Headers-$VULKAN_HEADERS_VER.tar.gz"
    VH_URL="https://github.com/KhronosGroup/Vulkan-Headers/archive/refs/tags/v$VULKAN_HEADERS_VER.tar.gz"

    NEED_VH=0
    if [ ! -f "$FF_SYSROOT/usr/include/vk_video/vulkan_video_codec_av1std.h" ]; then
        NEED_VH=1
    elif ! grep -q "VK_KHR_SHADER_SUBGROUP_ROTATE_EXTENSION_NAME" "$FF_SYSROOT/usr/include/vulkan/vulkan_core.h" 2>/dev/null; then
        NEED_VH=1
    fi

    if [ "$NEED_VH" = "1" ]; then
        echo "NDK Vulkan headers not sufficient; using vendored Vulkan-Headers v$VULKAN_HEADERS_VER"
        mkdir -p "$VULKAN_HEADERS_ROOT"
        if [ ! -d "$VULKAN_HEADERS_DIR/include" ]; then
            if [ ! -f "$VH_TAR" ]; then
                if command -v curl >/dev/null 2>&1; then
                    curl -L "$VH_URL" -o "$VH_TAR"
                elif command -v wget >/dev/null 2>&1; then
                    wget -O "$VH_TAR" "$VH_URL"
                else
                    echo "No curl/wget to download Vulkan-Headers"; exit 1
                fi
            fi
            tar -xzf "$VH_TAR" -C "$VULKAN_HEADERS_ROOT"
        fi
        if [ -d "$VULKAN_HEADERS_DIR/include" ]; then
            FF_CFLAGS="-I$VULKAN_HEADERS_DIR/include $FF_CFLAGS"
        fi
    fi

    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-vulkan"
    FF_DEP_LIBS="$FF_DEP_LIBS -lvulkan"
    FF_CFLAGS="$FF_CFLAGS -DVK_ENABLE_BETA_EXTENSIONS=1"
else
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-vulkan"
fi

# Enable Vulkan GLSL->SPIR-V compiler (required by *_vulkan filters)
# NOTE: FFmpeg configure's libglslang detection requires -lpthread/-lstdc++ which
# Android NDK does not provide as standalone libs. Vulkan filters are disabled to
# avoid configure failure. Vulkan device support (--enable-vulkan) is kept.
FF_ENABLE_VULKAN_FILTERS="${IJK_ENABLE_VULKAN_FILTERS:-0}"
FF_DEP_GLSLANG_INC="$FF_BUILD_ROOT/build/glslang-arm64/output/include"
FF_DEP_GLSLANG_LIB="$FF_BUILD_ROOT/build/glslang-arm64/output/lib"
if [ "$FF_ENABLE_VULKAN" = "1" ] && [ "$FF_ENABLE_VULKAN_FILTERS" = "1" ] && [ -f "${FF_DEP_GLSLANG_LIB}/libglslang.a" ]; then
    echo "glslang detected (spirv_compiler)"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-libglslang"
    FF_CFLAGS="$FF_CFLAGS -I${FF_DEP_GLSLANG_INC}"
    GLSLANG_LIBS="-L${FF_DEP_GLSLANG_LIB} -lglslang -lMachineIndependent -lOSDependent -lGenericCodeGen -lSPIRV -lSPVRemapper -lglslang-default-resource-limits"
    FF_DEP_LIBS="$FF_DEP_LIBS $GLSLANG_LIBS -lc++_shared -latomic"
    _DUMMY_SPIRV_DIR="$FF_BUILD_ROOT/build/glslang-arm64/output/lib"
    if [ ! -f "${_DUMMY_SPIRV_DIR}/libSPIRV-Tools.a" ]; then
        echo "Creating dummy libSPIRV-Tools.a for configure detection..."
        echo '' | "$AR" rcs "${_DUMMY_SPIRV_DIR}/libSPIRV-Tools.a"
    fi
    if [ ! -f "${_DUMMY_SPIRV_DIR}/libSPIRV-Tools-opt.a" ]; then
        echo "Creating dummy libSPIRV-Tools-opt.a for configure detection..."
        echo '' | "$AR" rcs "${_DUMMY_SPIRV_DIR}/libSPIRV-Tools-opt.a"
    fi
fi

# Android 不使用桌面/Windows 硬件解码加速，避免未定义宏导致编译失败（不影响 Vulkan 滤镜）
FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-hwaccels"
# 显式禁用 Vulkan video 编解码，保留 Vulkan 滤镜与设备
FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-decoder=av1_vulkan,h264_vulkan,hevc_vulkan"
FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-encoder=av1_vulkan,h264_vulkan,hevc_vulkan"
# 保险起见补充相关宏为0，规避上游宏缺省（仅在某些路径仍引用时有效）
FF_CFLAGS="$FF_CFLAGS -DCONFIG_HEVC_D3D12VA_HWACCEL=0 -DCONFIG_HEVC_VULKAN_HWACCEL=0 -DCONFIG_VULKAN_VERSION=1"

#--------------------
# Standard options:
FF_CFG_FLAGS="$FF_CFG_FLAGS --prefix=$FF_PREFIX"

# Advanced options (experts only):
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-cross-compile"
FF_CFG_FLAGS="$FF_CFG_FLAGS --target-os=android"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-pic"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-symver"

if [ "$FF_ARCH" = "x86" ] || [ "$FF_ARCH" = "arm64" ]; then
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-asm"
else
    # Optimization options (experts only):
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-asm"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-inline-asm"
fi

case "$FF_BUILD_OPT" in
    debug)
        FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-optimizations"
        FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-debug"
        FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-small"
    ;;
    *)
        FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-optimizations"
        FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-debug"
        FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-small"
    ;;
esac

#--------------------
echo ""
echo "--------------------"
echo "[*] configurate ffmpeg"
echo "--------------------"
cd $FF_SOURCE
if [ -f "./config.h" ]; then
    echo 'reuse configure'
else
    echo "CC=$CC"
    ./configure $FF_CFG_FLAGS \
        --target-os=android \
        --enable-cross-compile \
        --sysroot="$FF_SYSROOT" \
        --cc="$CC" --ar="$AR" --nm="$NM" --ranlib="$RANLIB" --strip="$STRIP" \
        --extra-cflags="$FF_CFLAGS $FF_EXTRA_CFLAGS" \
        --extra-ldflags="$FF_DEP_LIBS $FF_EXTRA_LDFLAGS"
    "$IJK_MAKE" clean
fi

#--------------------
echo ""
echo "--------------------"
echo "[*] compile ffmpeg"
echo "--------------------"
cp config.* $FF_PREFIX
"$IJK_MAKE" $FF_MAKE_FLAGS > /dev/null
"$IJK_MAKE" install
mkdir -p $FF_PREFIX/include/libffmpeg
cp -f config.h $FF_PREFIX/include/libffmpeg/config.h

#--------------------
echo ""
echo "--------------------"
echo "[*] link ffmpeg"
echo "--------------------"
echo $FF_EXTRA_LDFLAGS

# Prefer linking against static libs to include all subdir objects (FFmpeg 8 reorganized)
FF_LIB_DIR="$FF_PREFIX/lib"
AVCODEC_A="$FF_LIB_DIR/libavcodec.a"
AVFILTER_A="$FF_LIB_DIR/libavfilter.a"
AVFORMAT_A="$FF_LIB_DIR/libavformat.a"
AVUTIL_A="$FF_LIB_DIR/libavutil.a"
SWR_A="$FF_LIB_DIR/libswresample.a"
SWS_A="$FF_LIB_DIR/libswscale.a"

$CC -lm -lz -shared --sysroot=$FF_SYSROOT -Wl,--no-undefined -Wl,-z,noexecstack $FF_EXTRA_LDFLAGS \
    -Wl,-soname,libijkffmpeg.so \
    -Wl,--allow-multiple-definition \
    -Wl,--whole-archive \
    "$AVCODEC_A" "$AVFILTER_A" "$AVFORMAT_A" "$SWR_A" "$SWS_A" "$AVUTIL_A" \
    -Wl,--no-whole-archive \
    $FF_DEP_LIBS \
    -o $FF_PREFIX/libijkffmpeg.so

mysedi() {
    f=$1
    exp=$2
    n=`basename $f`
    cp $f /tmp/$n
    sed $exp /tmp/$n > $f
    rm /tmp/$n
}

echo ""
echo "--------------------"
echo "[*] create files for shared ffmpeg"
echo "--------------------"
rm -rf $FF_PREFIX/shared
mkdir -p $FF_PREFIX/shared/lib/pkgconfig
ln -s $FF_PREFIX/include $FF_PREFIX/shared/include
ln -s $FF_PREFIX/libijkffmpeg.so $FF_PREFIX/shared/lib/libijkffmpeg.so
cp $FF_PREFIX/lib/pkgconfig/*.pc $FF_PREFIX/shared/lib/pkgconfig
for f in $FF_PREFIX/lib/pkgconfig/*.pc; do
    # in case empty dir
    if [ ! -f $f ]; then
        continue
    fi
    cp $f $FF_PREFIX/shared/lib/pkgconfig
    f=$FF_PREFIX/shared/lib/pkgconfig/`basename $f`
    # OSX sed doesn't have in-place(-i)
    mysedi $f 's/\/output/\/output\/shared/g'
    mysedi $f 's/-lavcodec/-lijkffmpeg/g'
    mysedi $f 's/-lavfilter/-lijkffmpeg/g'
    mysedi $f 's/-lavformat/-lijkffmpeg/g'
    mysedi $f 's/-lavutil/-lijkffmpeg/g'
    mysedi $f 's/-lswresample/-lijkffmpeg/g'
    mysedi $f 's/-lswscale/-lijkffmpeg/g'
done
