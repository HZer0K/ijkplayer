/*
 * ffmpeg_api_jni.c
 *
 * Copyright (c) 2014 Bilibili
 * Copyright (c) 2014 Zhang Rui <bbcallen@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include "ffmpeg_api_jni.h"

#include <assert.h>
#include <string.h>
#include <jni.h>
#include "../ff_ffinc.h"
#include "libavformat/avio.h"
#include "libavfilter/avfilter.h"
#include "../ijkavformat/cJSON.h"
#include "ijksdl/ijksdl_log.h"
#include "ijksdl/android/ijksdl_android_jni.h"

#define JNI_CLASS_FFMPEG_API "tv/danmaku/ijk/media/player/ffmpeg/FFmpegApi"

typedef struct ffmpeg_api_fields_t {
    jclass clazz;
} ffmpeg_api_fields_t;
static ffmpeg_api_fields_t g_clazz;

int g_ijkplayer_diag_enabled = 0;

static jstring
FFmpegApi_av_base64_encode(JNIEnv *env, jclass clazz, jbyteArray in)
{
    jstring ret_string = NULL;
    char*   out_buffer = 0;
    int     out_size   = 0;
    jbyte*  in_buffer  = 0;
    jsize   in_size    = (*env)->GetArrayLength(env, in);
    if (in_size <= 0)
        goto fail;

    in_buffer = (*env)->GetByteArrayElements(env, in, NULL);
    if (!in_buffer)
        goto fail;

    out_size = AV_BASE64_SIZE(in_size);
    out_buffer = malloc(out_size + 1);
    if (!out_buffer)
        goto fail;
    out_buffer[out_size] = 0;

    if (!av_base64_encode(out_buffer, out_size, (const uint8_t *)in_buffer, in_size))
        goto fail;

    ret_string = (*env)->NewStringUTF(env, out_buffer);
fail:
    if (in_buffer) {
        (*env)->ReleaseByteArrayElements(env, in, in_buffer, JNI_ABORT);
        in_buffer = NULL;
    }
    if (out_buffer) {
        free(out_buffer);
        out_buffer = NULL;
    }
    return ret_string;
}

static jstring
FFmpegApi_getCapabilitiesJson(JNIEnv *env, jclass clazz)
{
    (void)clazz;
    cJSON *root = cJSON_CreateObject();
    if (!root) {
        return NULL;
    }

    cJSON_AddNumberToObject(root, "avformat_version", (double)avformat_version());
    cJSON_AddStringToObject(root, "avformat_configuration", avformat_configuration());
    cJSON_AddNumberToObject(root, "avcodec_version", (double)avcodec_version());
    cJSON_AddStringToObject(root, "avcodec_configuration", avcodec_configuration());
    cJSON_AddNumberToObject(root, "avutil_version", (double)avutil_version());
    cJSON_AddStringToObject(root, "avutil_configuration", avutil_configuration());
    cJSON_AddNumberToObject(root, "avfilter_version", (double)avfilter_version());
    cJSON_AddStringToObject(root, "avfilter_configuration", avfilter_configuration());

    /* Build-time capability flags: reflect what was compiled into this binary.
     * IJK_VULKAN_ENABLED     = --enable-vulkan was passed to FFmpeg configure.
     * IJK_VULKAN_FILTERS_ENABLED = --enable-libglslang was also passed (Vulkan GPU filters).
     * These help distinguish "filter not found at runtime" from "disabled at build time". */
#if CONFIG_VULKAN
    cJSON_AddBoolToObject(root, "build_vulkan_enabled", 1);
#else
    cJSON_AddBoolToObject(root, "build_vulkan_enabled", 0);
#endif
#if CONFIG_LIBGLSLANG
    cJSON_AddBoolToObject(root, "build_vulkan_filters_enabled", 1);
#else
    cJSON_AddBoolToObject(root, "build_vulkan_filters_enabled", 0);
#endif

    cJSON *protocols_in = cJSON_CreateArray();
    cJSON *protocols_out = cJSON_CreateArray();
    if (protocols_in) {
        cJSON_AddItemToObject(root, "protocols_in", protocols_in);
    }
    if (protocols_out) {
        cJSON_AddItemToObject(root, "protocols_out", protocols_out);
    }
    if (protocols_in && protocols_out) {
        void *opaque_in = NULL;
        const char *name_in = NULL;
        while ((name_in = avio_enum_protocols(&opaque_in, 0)) != NULL) {
            cJSON_AddItemToArray(protocols_in, cJSON_CreateString(name_in));
        }

        void *opaque_out = NULL;
        const char *name_out = NULL;
        while ((name_out = avio_enum_protocols(&opaque_out, 1)) != NULL) {
            cJSON_AddItemToArray(protocols_out, cJSON_CreateString(name_out));
        }
    }

    const char *filters_to_check[] = {
            "drawbox",
            "hflip",
            "vflip",
            "transpose",
            "scale",
            "format",
            "hwupload",
            "hwdownload",
            "scale_vulkan",
            "hflip_vulkan",
            "vflip_vulkan",
            "transpose_vulkan",
            "gblur_vulkan",
            "avgblur_vulkan",
            "chromaber_vulkan",
            NULL
    };

    cJSON *filter_presence = cJSON_CreateObject();
    if (filter_presence) {
        cJSON_AddItemToObject(root, "filter_presence", filter_presence);
    }
    int filter_count = 0;
    void *it = NULL;
    while (av_filter_iterate(&it) != NULL) {
        filter_count++;
    }
    cJSON_AddNumberToObject(root, "filters_count", filter_count);

    if (filter_presence) {
        for (int i = 0; filters_to_check[i] != NULL; i++) {
            const char *fname = filters_to_check[i];
            const AVFilter *f = avfilter_get_by_name(fname);
            cJSON_AddBoolToObject(filter_presence, fname, f != NULL);
        }
    }

    cJSON_AddBoolToObject(root, "diagnostics_enabled", g_ijkplayer_diag_enabled ? 1 : 0);

    char *out = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (!out) {
        return NULL;
    }
    jstring ret = (*env)->NewStringUTF(env, out);
    cJSON_free(out);
    return ret;
}

static void
FFmpegApi_setDiagnosticsEnabled(JNIEnv *env, jclass clazz, jboolean enabled)
{
    (void)env;
    (void)clazz;
    g_ijkplayer_diag_enabled = enabled ? 1 : 0;
    if (g_ijkplayer_diag_enabled) {
        ALOGI("diagnostics_enabled=1");
    } else {
        ALOGI("diagnostics_enabled=0");
    }
}

static jboolean
FFmpegApi_isDiagnosticsEnabled(JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;
    return g_ijkplayer_diag_enabled ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod g_methods[] = {
    {"av_base64_encode", "([B)Ljava/lang/String;", (void *) FFmpegApi_av_base64_encode},
    {"getCapabilitiesJson", "()Ljava/lang/String;", (void *) FFmpegApi_getCapabilitiesJson},
    {"setDiagnosticsEnabled", "(Z)V", (void *) FFmpegApi_setDiagnosticsEnabled},
    {"isDiagnosticsEnabled", "()Z", (void *) FFmpegApi_isDiagnosticsEnabled},
};

int FFmpegApi_global_init(JNIEnv *env)
{
    int ret = 0;

    IJK_FIND_JAVA_CLASS(env, g_clazz.clazz, JNI_CLASS_FFMPEG_API);
    (*env)->RegisterNatives(env, g_clazz.clazz, g_methods, NELEM(g_methods));

    return ret;
}
