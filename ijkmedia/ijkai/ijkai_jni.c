/*
 * ijkai_jni.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * JNI bridge for AI inference engine (ijkai).
 */

#include "ijkai.h"
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#define TAG "IJKAI_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 全局JVM引用
static JavaVM *g_jvm = NULL;
static jclass g_callback_class = NULL;
static jmethodID g_on_text_method = NULL;
static jmethodID g_on_error_method = NULL;

/**
 * 回调数据结构(用于JNI回调)
 */
typedef struct {
    JNIEnv *env;
    jobject callback_obj;
    jmethodID on_text;
    jmethodID on_error;
} jni_callback_data;

/**
 * JNI LLM回调
 */
static void jni_llm_callback(const char *text, bool is_complete, void *user_data) {
    jni_callback_data *cb = (jni_callback_data *)user_data;
    if (!cb || !cb->env || !cb->callback_obj) {
        return;
    }
    
    JNIEnv *env = cb->env;
    jobject callback_ref = cb->callback_obj;
    
    jstring jtext = (*env)->NewStringUTF(env, text ? text : "");
    (*env)->CallVoidMethod(env, callback_ref, cb->on_text, jtext, is_complete);
    (*env)->DeleteLocalRef(env, jtext);
    
    if (is_complete) {
        // 完成后释放全局引用
        (*env)->DeleteGlobalRef(env, callback_ref);
        free(cb);
    }
}

// ============ JNI 方法 ============

/**
 * 初始化AI引擎
 * jlong init(int type, String modelPath, int nThreads)
 */
static jlong JNICALL native_init(JNIEnv *env, jclass clazz,
    jint type, jstring model_path, jint n_threads)
{
    (void)clazz;
    
    const char *c_path = (*env)->GetStringUTFChars(env, model_path, NULL);
    if (!c_path) {
        return 0;
    }
    
    ijkai_context *ctx = ijkai_init((ijkai_type)type, c_path, (int)n_threads);
    (*env)->ReleaseStringUTFChars(env, model_path, c_path);
    
    LOGI("native_init: type=%d, ctx=%p", type, (void*)ctx);
    return (jlong)(intptr_t)ctx;
}

/**
 * 释放AI引擎
 * void release(long nativePtr)
 */
static void JNICALL native_release(JNIEnv *env, jclass clazz, jlong native_ptr)
{
    (void)env;
    (void)clazz;
    
    ijkai_context *ctx = (ijkai_context *)(intptr_t)native_ptr;
    if (ctx) {
        ijkai_release(&ctx);
        LOGI("native_release: done");
    }
}

/**
 * LLM推理(异步)
 * int llmPrompt(long nativePtr, String prompt, Object callback, int maxTokens)
 */
static jint JNICALL native_llm_prompt(JNIEnv *env, jclass clazz,
    jlong native_ptr, jstring prompt, jobject callback, jint max_tokens)
{
    (void)clazz;
    
    ijkai_context *ctx = (ijkai_context *)(intptr_t)native_ptr;
    if (!ctx || !prompt || !callback) {
        return -1;
    }
    
    const char *c_prompt = (*env)->GetStringUTFChars(env, prompt, NULL);
    if (!c_prompt) {
        return -1;
    }
    
    // 创建回调数据
    jni_callback_data *cb = (jni_callback_data *)malloc(sizeof(jni_callback_data));
    if (!cb) {
        (*env)->ReleaseStringUTFChars(env, prompt, c_prompt);
        return -1;
    }
    
    cb->env = env;
    cb->callback_obj = (*env)->NewGlobalRef(env, callback);
    
    jclass callback_cls = (*env)->GetObjectClass(env, callback);
    cb->on_text = (*env)->GetMethodID(env, callback_cls, "onText",
        "(Ljava/lang/String;Z)V");
    cb->on_error = (*env)->GetMethodID(env, callback_cls, "onError",
        "(Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, callback_cls);
    
    int ret = ijkai_llm_prompt(ctx, c_prompt, jni_llm_callback, cb, (int)max_tokens);
    
    (*env)->ReleaseStringUTFChars(env, prompt, c_prompt);
    
    return ret;
}

/**
 * 获取token数量
 * int getTokenCount(long nativePtr)
 */
static jint JNICALL native_get_token_count(JNIEnv *env, jclass clazz, jlong native_ptr)
{
    (void)env;
    (void)clazz;
    
    ijkai_context *ctx = (ijkai_context *)(intptr_t)native_ptr;
    if (!ctx) return 0;
    return ijkai_get_token_count(ctx);
}

/**
 * 获取已处理帧数
 * int getProcessedFrames(long nativePtr)
 */
static jint JNICALL native_get_processed_frames(JNIEnv *env, jclass clazz, jlong native_ptr)
{
    (void)env;
    (void)clazz;
    
    ijkai_context *ctx = (ijkai_context *)(intptr_t)native_ptr;
    if (!ctx) return 0;
    return ijkai_get_processed_frames(ctx);
}

// ============ JNI 注册(由ijkplayer_jni.c的JNI_OnLoad调用) ============

static JNINativeMethod g_methods[] = {
    {"nativeInit",            "(ILjava/lang/String;I)J",  (void *)native_init},
    {"nativeRelease",         "(J)V",                     (void *)native_release},
    {"nativeLLMPrompt",       "(JLjava/lang/String;Ljava/lang/Object;I)I", (void *)native_llm_prompt},
    {"nativeGetTokenCount",   "(J)I",                     (void *)native_get_token_count},
    {"nativeGetProcessedFrames","(J)I",                    (void *)native_get_processed_frames},
};

jint IJKAI_RegisterNatives(JNIEnv *env) {
    jclass clazz = (*env)->FindClass(env, "tv/danmaku/ijk/media/player/IjkAIEngine");
    if (!clazz) {
        LOGE("IJKAI_RegisterNatives: Failed to find IjkAIEngine class");
        return -1;
    }
    
    jint ret = (*env)->RegisterNatives(env, clazz, g_methods,
        sizeof(g_methods) / sizeof(g_methods[0]));
    if (ret != JNI_OK) {
        LOGE("IJKAI_RegisterNatives: Failed to register natives");
        return -1;
    }
    
    LOGI("IJKAI_RegisterNatives: registered %zu methods",
        sizeof(g_methods) / sizeof(g_methods[0]));
    
    return JNI_VERSION_1_6;
}
