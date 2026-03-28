#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeIsEnabled(JNIEnv *, jclass) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeLoadModel(JNIEnv *, jclass, jstring) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeRelease(JNIEnv *, jclass) {
}

extern "C" JNIEXPORT jstring JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeTranscribePcm16(JNIEnv *env, jclass,
                                                                             jbyteArray,
                                                                             jint, jint, jint, jint,
                                                                             jstring) {
    return env->NewStringUTF("{\"error\":\"whisper_disabled\"}");
}

