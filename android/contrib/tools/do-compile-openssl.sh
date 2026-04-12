#!/usr/bin/env bash
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

set -e

if [ -z "$ANDROID_NDK" ]; then
    ANDROID_NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
    export ANDROID_NDK
fi

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

PERL5LIB_SEP=":"
case "$(uname -s)" in
    CYGWIN_NT-*|MINGW64_NT*|MINGW32_NT*)
        PERL5LIB_SEP=";"
    ;;
esac
export PERL5LIB="$FF_WORK_DIR/util/perl${PERL5LIB:+${PERL5LIB_SEP}$PERL5LIB}"
export PERL5OPT="-I$FF_WORK_DIR/util/perl${PERL5OPT:+ $PERL5OPT}"

if ! perl -MLocale::Maketext::Simple=Style,gettext,loc -e 1 >/dev/null 2>&1; then
    mkdir -p "$FF_WORK_DIR/util/perl/Locale/Maketext"
    cat > "$FF_WORK_DIR/util/perl/Locale/Maketext/Simple.pm" <<'EOF'
package Locale::Maketext::Simple;
use strict;
use warnings;
use Exporter 'import';
our @EXPORT = qw(loc gettext Style);
our %EXPORT_TAGS = (all => \@EXPORT);
our @EXPORT_OK = @EXPORT;
sub loc { return @_ ? $_[0] : '' }
sub gettext { return @_ ? $_[0] : '' }
sub Style { return }
1;
EOF
fi

if ! perl -MExtUtils::MakeMaker -e 'exit(MM->can("maybe_command") ? 0 : 1)' >/dev/null 2>&1; then
    mkdir -p "$FF_WORK_DIR/util/perl/ExtUtils"
    cat > "$FF_WORK_DIR/util/perl/ExtUtils/MakeMaker.pm" <<'EOF'
package ExtUtils::MakeMaker;
use strict;
use warnings;
use Exporter 'import';
our $VERSION = '0.00';
our @EXPORT = qw(prompt);
our %EXPORT_TAGS = (all => \@EXPORT);
our @EXPORT_OK = @EXPORT;
sub prompt {
    my ($mess, $def) = @_;
    return defined $def ? $def : '';
}
1;

package MM;
use strict;
use warnings;
sub maybe_command {
    my ($class, $cmd) = @_;
    return unless defined $cmd && length($cmd);
    if ($cmd =~ m{[\\/]} && -x $cmd) {
        return $cmd;
    }
    for my $dir (split(/:/, $ENV{PATH} || '')) {
        my $path = "$dir/$cmd";
        return $path if -x $path;
    }
    return;
}
1;
EOF
fi

mkdir -p "$FF_WORK_DIR/util/perl/Pod"
cat > "$FF_WORK_DIR/util/perl/Pod/Usage.pm" <<'EOF'
package Pod::Usage;
use strict;
use warnings;
use Exporter 'import';
our @EXPORT = qw(pod2usage);
our %EXPORT_TAGS = (all => \@EXPORT);
our @EXPORT_OK = @EXPORT;
sub pod2usage {
    return 1;
}
1;
EOF

export ANDROID_NDK_HOME="$ANDROID_NDK"
export ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_NDK}"
export PATH="$IJK_LLVM_BIN:$PATH"
export CC="$IJK_LLVM_BIN/aarch64-linux-android${API_LEVEL}-clang"
export CXX="$IJK_LLVM_BIN/aarch64-linux-android${API_LEVEL}-clang++"
if [ ! -x "$CC" ]; then
    echo "Missing clang wrapper: $CC"
    echo "Please ensure ANDROID_NDK points to an NDK that provides aarch64-linux-android${API_LEVEL}-clang (NDK r23+)."
    exit 1
fi

SHIM_RECORD="$FF_WORK_DIR/ndk-toolchain-shim.created"
rm -f "$SHIM_RECORD"

create_ndk_bin_shim () {
    local name="$1"
    local target="$2"
    local out="$IJK_LLVM_BIN/$name"
    if [ -e "$out" ]; then
        return 0
    fi
    cat > "$out" <<EOF
#!/usr/bin/env sh
exec "$target" "\$@"
EOF
    chmod +x "$out"
    echo "$out" >> "$SHIM_RECORD"
}

create_ndk_bin_shim "aarch64-linux-android-gcc" "$CC"
create_ndk_bin_shim "aarch64-linux-android-g++" "$CXX"
create_ndk_bin_shim "aarch64-linux-android-ar" "$IJK_LLVM_BIN/llvm-ar"
create_ndk_bin_shim "aarch64-linux-android-ranlib" "$IJK_LLVM_BIN/llvm-ranlib"
create_ndk_bin_shim "aarch64-linux-android-strip" "$IJK_LLVM_BIN/llvm-strip"

cleanup_ndk_bin_shims () {
    if [ -f "$SHIM_RECORD" ]; then
        while IFS= read -r f; do
            rm -f "$f" || true
        done < "$SHIM_RECORD"
        rm -f "$SHIM_RECORD" || true
    fi
}
trap cleanup_ndk_bin_shims EXIT

export AR="$IJK_LLVM_BIN/llvm-ar"
export RANLIB="$IJK_LLVM_BIN/llvm-ranlib"
export STRIP="$IJK_LLVM_BIN/llvm-strip"

echo "PATH(head)=$IJK_LLVM_BIN"
echo "CC=$CC"
echo "ANDROID_NDK_ROOT=$ANDROID_NDK_ROOT"
command -v clang || true
command -v aarch64-linux-android-gcc || true

echo ""
echo "--------------------"
echo "[*] configurate openssl ($FF_ARCH)"
echo "--------------------"
cd "$FF_WORK_DIR"

if [ -f "./Makefile" ]; then
    "$IJK_MAKE" clean || true
fi

echo "./Configure $OPENSSL_TARGET -D__ANDROID_API__=$API_LEVEL no-shared no-tests --prefix=$FF_PREFIX --openssldir=$FF_PREFIX"
./Configure "$OPENSSL_TARGET" -D__ANDROID_API__="$API_LEVEL" no-shared no-tests --prefix="$FF_PREFIX" --openssldir="$FF_PREFIX"

echo ""
echo "--------------------"
echo "[*] compile openssl ($FF_ARCH)"
echo "--------------------"
# IJK_MAKE_FLAG is set by do-detect-env.sh (e.g. -j$(nproc) on Linux, -j$(sysctl) on macOS)
"$IJK_MAKE" $IJK_MAKE_FLAG
"$IJK_MAKE" install_sw

echo ""
echo "--------------------"
echo "[*] OpenSSL output: $FF_PREFIX"
echo "--------------------"
