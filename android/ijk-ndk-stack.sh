#!/bin/sh

adb logcat | ndk-stack -sym ijkplayer/ijkplayer-arm64/src/main/obj/local/arm64-v8a
