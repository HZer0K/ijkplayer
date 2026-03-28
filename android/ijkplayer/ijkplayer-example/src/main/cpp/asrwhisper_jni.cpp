#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>

#include "whisper.h"

static std::mutex g_mutex;
static whisper_context *g_ctx = nullptr;
static std::string g_model_path;

static void loge(const char *msg) {
    __android_log_print(ANDROID_LOG_ERROR, "asrwhisper", "%s", msg);
}

static std::vector<float> pcm16_to_f32_mono_16k(const int16_t *pcm, int samples, int sample_rate, int channels) {
    if (!pcm || samples <= 0) return {};
    int ch = channels > 0 ? channels : 1;
    int sr = sample_rate > 0 ? sample_rate : 16000;

    int frames = samples / ch;
    std::vector<float> mono(frames);
    for (int i = 0; i < frames; i++) {
        int32_t acc = 0;
        for (int c = 0; c < ch; c++) {
            acc += pcm[i * ch + c];
        }
        float v = (float) acc / (float) ch;
        mono[i] = v / 32768.0f;
    }

    if (sr == 16000) {
        return mono;
    }

    const double ratio = 16000.0 / (double) sr;
    int out_frames = (int) (frames * ratio);
    if (out_frames < 1) out_frames = 1;
    std::vector<float> out(out_frames);
    for (int i = 0; i < out_frames; i++) {
        double src = i / ratio;
        int i0 = (int) src;
        int i1 = i0 + 1;
        if (i0 < 0) i0 = 0;
        if (i1 >= frames) i1 = frames - 1;
        double t = src - i0;
        out[i] = (float) ((1.0 - t) * mono[i0] + t * mono[i1]);
    }
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeLoadModel(JNIEnv *env, jclass, jstring modelPath) {
    const char *p = modelPath ? env->GetStringUTFChars(modelPath, nullptr) : nullptr;
    std::string path = p ? p : "";
    if (p) env->ReleaseStringUTFChars(modelPath, p);
    if (path.empty()) return JNI_FALSE;

    std::lock_guard<std::mutex> lk(g_mutex);
    if (g_ctx && g_model_path == path) {
        return JNI_TRUE;
    }
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        g_model_path.clear();
    }
    g_ctx = whisper_init_from_file(path.c_str());
    if (!g_ctx) {
        loge("whisper_init_from_file failed");
        return JNI_FALSE;
    }
    g_model_path = path;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeIsEnabled(JNIEnv *, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeRelease(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        g_model_path.clear();
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_tv_danmaku_ijk_media_example_util_WhisperAsrEngine_nativeTranscribePcm16(JNIEnv *env, jclass,
                                                                             jbyteArray pcmData,
                                                                             jint sampleRate,
                                                                             jint channelCount,
                                                                             jint startMs,
                                                                             jint endMs,
                                                                             jstring language) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (!g_ctx) {
        return env->NewStringUTF("{\"error\":\"model_not_loaded\"}");
    }
    if (!pcmData) {
        return env->NewStringUTF("{\"segments\":[]}");
    }
    jsize len = env->GetArrayLength(pcmData);
    if (len <= 0) {
        return env->NewStringUTF("{\"segments\":[]}");
    }

    std::vector<uint8_t> bytes((size_t) len);
    env->GetByteArrayRegion(pcmData, 0, len, reinterpret_cast<jbyte *>(bytes.data()));

    const int16_t *pcm16 = reinterpret_cast<const int16_t *>(bytes.data());
    int samples = (int) (bytes.size() / 2);
    auto audio = pcm16_to_f32_mono_16k(pcm16, samples, (int) sampleRate, (int) channelCount);
    if (audio.empty()) {
        return env->NewStringUTF("{\"segments\":[]}");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.max_tokens = 0;
    params.temperature = 0.0f;
    params.n_threads = 4;

    const char *lang = language ? env->GetStringUTFChars(language, nullptr) : nullptr;
    std::string lang_s = lang ? lang : "";
    if (lang) env->ReleaseStringUTFChars(language, lang);
    if (!lang_s.empty()) {
        params.language = lang_s.c_str();
    }

    int ret = whisper_full(g_ctx, params, audio.data(), (int) audio.size());
    if (ret != 0) {
        return env->NewStringUTF("{\"error\":\"transcribe_failed\"}");
    }

    int n = whisper_full_n_segments(g_ctx);
    std::string out = "{\"partial\":false,\"segments\":[";
    bool first = true;
    for (int i = 0; i < n; i++) {
        const char *txt = whisper_full_get_segment_text(g_ctx, i);
        if (!txt) continue;
        std::string t = txt;
        while (!t.empty() && (t[0] == ' ' || t[0] == '\n' || t[0] == '\t')) t.erase(t.begin());
        if (t.empty()) continue;
        int t0 = whisper_full_get_segment_t0(g_ctx, i) * 10;
        int t1 = whisper_full_get_segment_t1(g_ctx, i) * 10;
        int s0 = (int) startMs + t0;
        int s1 = (int) startMs + t1;
        if (s1 <= s0) s1 = s0 + 200;
        if (s1 > endMs) s1 = endMs;
        if (!first) out += ",";
        first = false;
        out += "{\"startMs\":" + std::to_string(s0) + ",\"endMs\":" + std::to_string(s1) + ",\"text\":\"";
        for (char c : t) {
            if (c == '\\\\' || c == '\"') {
                out.push_back('\\\\');
                out.push_back(c);
            } else if (c == '\n' || c == '\r') {
                out += "\\\\n";
            } else {
                out.push_back(c);
            }
        }
        out += "\"}";
    }
    out += "]}";
    return env->NewStringUTF(out.c_str());
}
