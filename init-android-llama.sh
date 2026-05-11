#!/bin/bash
# init-android-llama.sh
# Initialize llama.cpp for Android

set -e

IJKPLAYER_ROOT=$(cd "$(dirname "$0")"; pwd)
EXTRA_ROOT="$IJKPLAYER_ROOT/extra"

echo "=== Initialize llama.cpp for Android ==="

cd "$EXTRA_ROOT"

# Download llama.cpp
if [ ! -d "llama.cpp" ]; then
    echo "Cloning llama.cpp repository..."
    git clone https://github.com/ggerganov/llama.cpp.git
    cd llama.cpp
    # Use a stable version
    git checkout b3763
else
    echo "llama.cpp already exists"
    cd llama.cpp
fi

echo "=== llama.cpp initialized ==="
echo "Next step: run android/contrib/compile-llama.sh"
