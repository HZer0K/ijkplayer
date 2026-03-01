LOCAL_PATH := $(call my-dir)

MY_APP_JNI_ROOT := $(realpath $(LOCAL_PATH))
MY_APP_PRJ_ROOT := $(realpath $(MY_APP_JNI_ROOT)/..)
MY_APP_ANDROID_ROOT := $(realpath $(MY_APP_PRJ_ROOT)/../../../..)

MY_APP_FFMPEG_OUTPUT_PATH := $(realpath $(MY_APP_ANDROID_ROOT)/contrib/build/ffmpeg-arm64/output)
MY_APP_FFMPEG_INCLUDE_PATH := $(realpath $(MY_APP_FFMPEG_OUTPUT_PATH)/include)
MY_APP_FFMPEG_SOURCE_PATH := $(realpath $(MY_APP_ANDROID_ROOT)/contrib/ffmpeg-arm64)

include $(call all-subdir-makefiles)

MY_IJKMEDIA_DIR := $(realpath $(MY_APP_ANDROID_ROOT)/../ijkmedia)
include $(MY_IJKMEDIA_DIR)/Android.mk
