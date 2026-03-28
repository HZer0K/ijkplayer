package tv.danmaku.ijk.media.example.util;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import tv.danmaku.ijk.media.example.BuildConfig;

public final class WhisperAsrEngine {
    private static final boolean sLoaded;
    private static final boolean sEnabled;
    private static final String TAG = "WhisperAsrEngine";

    static {
        boolean loaded;
        boolean enabled = false;
        Throwable loadError = null;
        try {
            System.loadLibrary("asrwhisper");
            loaded = true;
            try {
                enabled = nativeIsEnabled();
            } catch (Throwable ignored) {
                enabled = false;
            }
        } catch (Throwable ignored) {
            loaded = false;
            loadError = ignored;
        }
        sLoaded = loaded;
        sEnabled = loaded && enabled;
        try {
            Log.i(TAG, "init loaded=" + sLoaded + " enabled=" + sEnabled + " WHISPER_ENABLED=" + BuildConfig.WHISPER_ENABLED);
            if (loadError != null) {
                Log.e(TAG, "loadLibrary(asrwhisper) failed: " + loadError.getClass().getSimpleName() + ": " + loadError.getMessage());
            }
        } catch (Throwable ignored) {
        }
    }

    private WhisperAsrEngine() {
    }

    public static boolean isAvailable() {
        return sEnabled;
    }

    public static boolean isLoaded() {
        return sLoaded;
    }

    public static String debugStatus() {
        return "loaded=" + sLoaded + ", enabled=" + sEnabled;
    }

    public static boolean loadModel(String modelPath) {
        if (!sEnabled || TextUtils.isEmpty(modelPath)) {
            return false;
        }
        try {
            return nativeLoadModel(modelPath);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void release() {
        if (!sEnabled) {
            return;
        }
        try {
            nativeRelease();
        } catch (Throwable ignored) {
        }
    }

    public static String transcribePcm16Json(byte[] pcmData, int sampleRate, int channelCount, int startMs, int endMs, String language) {
        if (!sEnabled) {
            return "{\"error\":\"native_missing_or_disabled\"}";
        }
        try {
            String json = nativeTranscribePcm16(pcmData, sampleRate, channelCount, startMs, endMs, language);
            return !TextUtils.isEmpty(json) ? json : "{\"segments\":[]}";
        } catch (Throwable t) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("error", "exception");
                obj.put("message", String.valueOf(t.getClass().getSimpleName()));
                return obj.toString();
            } catch (Throwable ignored) {
                return "{\"error\":\"exception\"}";
            }
        }
    }

    private static native boolean nativeLoadModel(String modelPath);

    private static native boolean nativeIsEnabled();

    private static native void nativeRelease();

    private static native String nativeTranscribePcm16(byte[] pcmData, int sampleRate, int channelCount, int startMs, int endMs, String language);
}
