#!/bin/sh

adb logcat | ndk-stack -sym ijkplayer/ijkplayer-arm64/build/cmake/arm64-v8a
