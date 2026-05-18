#!/bin/bash
# init-android-mnn.sh
# Initialize MNN (Mobile Neural Network) for Android

set -e

IJKPLAYER_ROOT=$(cd "$(dirname "$0")"; pwd)
EXTRA_ROOT="$IJKPLAYER_ROOT/extra"

echo "=== Initialize MNN for Android ==="

cd "$EXTRA_ROOT"

# Download MNN
if [ ! -d "MNN" ]; then
    echo "Cloning MNN repository..."
    git clone https://github.com/alibaba/MNN.git
    cd MNN
    # Use a stable version
    git checkout 2.9.0
else
    echo "MNN already exists"
    cd MNN
fi

echo "=== MNN initialized ==="
echo "Next step: run android/contrib/compile-mnn.sh"
