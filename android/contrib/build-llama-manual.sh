#!/bin/bash
# build-llama-manual.sh
# 手动编译llama.cpp的脚本

set -e

# 设置NDK路径
export ANDROID_NDK=$HOME/tool/NDK/android-ndk-r27

echo "=== 检查环境 ==="
echo "ANDROID_NDK: $ANDROID_NDK"

# 检查NDK是否存在
if [ ! -d "$ANDROID_NDK" ]; then
    echo "错误: NDK路径不存在: $ANDROID_NDK"
    exit 1
fi

# 检查llama.cpp是否存在
LLAMA_ROOT="$HOME/project/IJKPLAYER/ijkplayer/extra/llama.cpp"
if [ ! -d "$LLAMA_ROOT" ]; then
    echo "错误: llama.cpp目录不存在: $LLAMA_ROOT"
    echo "请先运行: ./init-android-llama.sh"
    exit 1
fi

echo "=== 开始编译llama.cpp ==="

cd "$HOME/project/IJKPLAYER/ijkplayer/android/contrib"
bash ./compile-llama.sh

echo "=== 编译完成 ==="
