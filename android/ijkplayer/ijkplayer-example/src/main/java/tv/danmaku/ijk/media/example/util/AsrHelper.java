/*
 * Copyright (C) 2024 Bilibili
 * Copyright (C) 2024 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.example.util;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.danmaku.ijk.media.example.BuildConfig;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;

/**
 * Manages all ASR (Automatic Speech Recognition) modes:
 * - System SpeechRecognizer
 * - Remote mic-based ASR via HTTP endpoint
 * - Remote track-based ASR (audio extraction + HTTP)
 * - Whisper offline track ASR via native engine
 */
public class AsrHelper {
    private static final String TAG = "AsrHelper";
    private static final int REQ_RECORD_AUDIO = 2201;

    public interface Callback {
        /** Called when ASR partial/final text should be shown in subtitle overlay. */
        void onAsrPartialText(@Nullable String text);
        /** Called when a subtitle cue should be added to the overlay list. */
        void addSubtitleCue(int startMs, String text);
        /** Called when a subtitle cue with explicit end time should be added. */
        void addSubtitleCueExplicit(int startMs, int endMs, String text);
        /** Show a toast-like message via the media controller. */
        void showToastText(String text);
        /** Invalidate the options menu to sync check states. */
        void invalidateOptionsMenu();
        /** Get current playback position in milliseconds. */
        int getCurrentPosition();
        /** Get media duration in milliseconds. */
        int getDuration();
        /** Get the current data source URL/path. */
        @Nullable String getDataSource();
        /** Get the video path set by the activity. */
        @Nullable String getVideoPath();
        /** Get the video URI set by the activity. */
        @Nullable Uri getVideoUri();
        /** Get external files directory for the given type. */
        @Nullable File getExternalFilesDir(String type);
        /** Get the application's files directory. */
        File getFilesDir();
    }

    private final Context mContext;
    private final Settings mSettings;
    private final Handler mMainHandler;
    private final Callback mCallback;

    // --- System ASR ---
    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechIntent;
    private boolean mAsrEnabled;
    private boolean mAsrPendingStart;
    private String mAsrPartialText;
    private String mAsrLastFinalText;
    private long mAsrLastFinalAtMs;
    private int mAsrServiceCount;

    // --- Remote mic ASR ---
    private AudioRecord mAsrAudioRecord;
    private Thread mAsrRemoteThread;
    private boolean mAsrRemoteRunning;
    private long mAsrRemoteChunkStartPlayerMs = -1;
    private int mAsrRemoteChunkBytes = 0;
    private final java.io.ByteArrayOutputStream mAsrRemoteChunkBuffer =
            new java.io.ByteArrayOutputStream();
    private final RemoteAsrClient mRemoteAsrClient = new RemoteAsrClient();
    private ExecutorService mAsrRemoteExecutor;
    private boolean mAsrRemoteChunkHasVoice;
    private long mAsrRemoteLastVoiceAtMs = -1;
    private String mAsrRemoteCommittedTail = "";

    // --- Track ASR ---
    private AsrAudioTrackDecoder mAsrTrackDecoder;
    private int mAsrTrackChunkStartMs = -1;
    private int mAsrTrackChunkBytes = 0;
    private boolean mAsrTrackChunkHasVoice;
    private int mAsrTrackLastVoiceMs = -1;
    private int mAsrTrackLastEndMs = -1;
    private int mAsrTrackSampleRate = 16000;
    private int mAsrTrackChannelCount = 1;
    private String mAsrTrackAudioFormat = "pcm_s16le";
    private final java.io.ByteArrayOutputStream mAsrTrackChunkBuffer =
            new java.io.ByteArrayOutputStream();
    private boolean mAsrTrackUseWhisper;
    private String mAsrWhisperModelPath;
    private long mAsrTrackLastVoiceDebugAtMs;
    private long mAsrTrackLastResultDebugAtMs;

    // --- Download state ---
    private long mAsrTrackDownloadId = -1L;
    private File mAsrTrackDownloadedFile;
    private BroadcastReceiver mAsrTrackDownloadReceiver;
    private String mAsrTrackDownloadUrl;

    // --- Whisper model download ---
    private File mWhisperModelDownloadedFile;
    private String mWhisperModelDownloadUrl;
    private HttpFileDownloader mWhisperModelDownloader;
    private Thread mWhisperModelDownloadThread;

    public AsrHelper(Context context, Settings settings, Callback callback) {
        mContext = context.getApplicationContext();
        mSettings = settings;
        mMainHandler = new Handler(Looper.getMainLooper());
        mCallback = callback;
    }

    /** Whether ASR is enabled. */
    public boolean isEnabled() {
        return mAsrEnabled;
    }

    /** Get current ASR partial text (may be null). */
    @Nullable
    public String getPartialText() {
        return mAsrPartialText;
    }

    // ================ Lifecycle ================

    /** Call from Activity.onStart(). */
    public void onStart() {
        if (mAsrEnabled) {
            startAsrByMode();
        }
    }

    /** Call from Activity.onPause(). */
    public void onPause() {
        stopAsrListening();
        stopTrackAsr();
        stopRemoteAsr();
    }

    /** Call from Activity.onStop(). */
    public void onStop() {
        // No-op for now
    }

    /** Call from Activity.onDestroy() — unregister receivers and release resources. */
    public void onDestroy() {
        stopAsrListening();
        unregisterTrackDownloadReceiver();
        cancelWhisperModelDownload();
    }

    /** Call from Activity.onRequestPermissionsResult(). */
    public void onRequestPermissionsResult(boolean granted) {
        if (granted && mAsrPendingStart) {
            mAsrPendingStart = false;
            startAsrByMode();
        } else if (!granted) {
            mAsrPendingStart = false;
            mAsrEnabled = false;
            mAsrPartialText = null;
            mCallback.onAsrPartialText(null);
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_permission_denied));
            mCallback.invalidateOptionsMenu();
        }
    }

    // ================ Public API for Activity ================

    /**
     * Toggle ASR on/off. When turning on, starts the appropriate ASR mode.
     * @return true if ASR is now enabled, false otherwise
     */
    public boolean toggle() {
        boolean next = !mAsrEnabled;
        if (next) {
            mAsrEnabled = true;
            startAsrByMode();
        } else {
            mAsrEnabled = false;
            stopAsrListening();
            stopTrackAsr();
            stopRemoteAsr();
            mAsrPartialText = null;
            mCallback.onAsrPartialText(null);
        }
        mCallback.invalidateOptionsMenu();
        return mAsrEnabled;
    }

    /**
     * Set ASR enabled state externally (e.g., when mode selection fails).
     */
    public void setEnabled(boolean enabled) {
        mAsrEnabled = enabled;
    }

    /** Query the number of ASR services available on the device. */
    public int getServiceCount() {
        return mAsrServiceCount;
    }

    /** Check if ASR is available (system recognizer or remote endpoint configured). */
    public boolean isAvailable() {
        boolean availableFlag = false;
        try {
            availableFlag = SpeechRecognizer.isRecognitionAvailable(mContext);
        } catch (Throwable ignored) {
        }
        int services = queryAsrServiceCount();
        if (availableFlag || services > 0) {
            return true;
        }
        return mSettings != null && !TextUtils.isEmpty(mSettings.getAsrRemoteEndpoint());
    }

    // ================ ASR Mode Dispatch ================

    private void startAsrByMode() {
        if (!mAsrEnabled) {
            return;
        }
        String mode = mSettings != null ? mSettings.getAsrMode() : "system";
        if ("whisper_track".equalsIgnoreCase(mode)) {
            startWhisperTrackAsrIfPossible();
        } else if ("remote_track".equalsIgnoreCase(mode)) {
            startTrackAsrIfPossible();
        } else if ("remote".equalsIgnoreCase(mode)) {
            startRemoteAsrIfPossible();
        } else {
            startSystemAsrIfPossible();
        }
    }

    // ================ System SpeechRecognizer ASR ================

    private void startSystemAsrIfPossible() {
        if (!mAsrEnabled) {
            return;
        }
        boolean availableFlag = false;
        try {
            availableFlag = SpeechRecognizer.isRecognitionAvailable(mContext);
        } catch (Throwable ignored) {
        }
        int services = queryAsrServiceCount();
        if (!availableFlag && services <= 0) {
            String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
            if (!TextUtils.isEmpty(endpoint)) {
                startRemoteAsrIfPossible();
                return;
            }
            mAsrEnabled = false;
            mAsrPartialText = null;
            mCallback.onAsrPartialText(null);
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_unavailable));
            mCallback.invalidateOptionsMenu();
            return;
        }
        android.content.pm.PackageManager pm = mContext.getPackageManager();
        if (pm != null &&
                ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.RECORD_AUDIO)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mAsrPendingStart = true;
            // We can't call requestPermissions directly here, so signal to Activity
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_permission_denied));
            return;
        }
        startAsrListening();
    }

    private void startAsrListening() {
        if (!mAsrEnabled) {
            return;
        }
        try {
            if (mSpeechRecognizer == null) {
                mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
                mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}

                    @Override
                    public void onError(int error) {
                        if (!mAsrEnabled) return;
                        Log.d(TAG, "ASR.onError=" + errorToText(error));
                        mMainHandler.postDelayed(() -> {
                            if (mAsrEnabled) restartAsr();
                        }, 600);
                    }

                    @Override
                    public void onResults(Bundle results) {
                        handleAsrResults(results, false);
                        if (mAsrEnabled) {
                            mMainHandler.postDelayed(() -> {
                                if (mAsrEnabled) restartAsr();
                            }, 200);
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        handleAsrResults(partialResults, true);
                    }

                    @Override public void onEvent(int eventType, Bundle params) {}
                });
            }
            if (mSpeechIntent == null) {
                mSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                        mContext.getPackageName());
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            }
            mSpeechRecognizer.startListening(mSpeechIntent);
        } catch (Throwable t) {
            mAsrEnabled = false;
            mAsrPartialText = null;
            mCallback.onAsrPartialText(null);
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_start_failed));
            mCallback.invalidateOptionsMenu();
        }
    }

    private void restartAsr() {
        try {
            if (mSpeechRecognizer != null) {
                mSpeechRecognizer.cancel();
            }
        } catch (Throwable ignored) {
        }
        startAsrListening();
    }

    private void stopAsrListening() {
        try {
            if (mSpeechRecognizer != null) {
                mSpeechRecognizer.cancel();
                mSpeechRecognizer.destroy();
                mSpeechRecognizer = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleAsrResults(Bundle bundle, boolean partial) {
        if (!mAsrEnabled) return;
        try {
            ArrayList<String> list = bundle != null
                    ? bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
            String text = (list != null && !list.isEmpty()) ? list.get(0) : null;
            if (TextUtils.isEmpty(text)) {
                if (partial) {
                    mAsrPartialText = null;
                    mCallback.onAsrPartialText(null);
                }
                return;
            }
            text = text.trim();
            if (TextUtils.isEmpty(text)) return;

            if (partial) {
                mAsrPartialText = text;
                mCallback.onAsrPartialText(text);
                return;
            }

            long now = System.currentTimeMillis();
            if (text.equals(mAsrLastFinalText) && (now - mAsrLastFinalAtMs) < 800) {
                mAsrPartialText = null;
                mCallback.onAsrPartialText(null);
                return;
            }
            mAsrLastFinalText = text;
            mAsrLastFinalAtMs = now;
            mAsrPartialText = null;

            int startMs = mCallback.getCurrentPosition();
            mCallback.addSubtitleCue(startMs, text);
        } catch (Throwable ignored) {
            Log.w(TAG, "handleAsrResults failed", ignored);
        }
    }

    private int queryAsrServiceCount() {
        try {
            android.content.pm.PackageManager pm = mContext.getPackageManager();
            if (pm == null) return 0;
            List<android.content.pm.ResolveInfo> infos =
                    pm.queryIntentServices(
                            new Intent("android.speech.RecognitionService"), 0);
            mAsrServiceCount = infos != null ? infos.size() : 0;
            return mAsrServiceCount;
        } catch (Throwable ignored) {
            mAsrServiceCount = 0;
            return 0;
        }
    }

    private String errorToText(int error) {
        if (error == SpeechRecognizer.ERROR_AUDIO) return "ERROR_AUDIO";
        if (error == SpeechRecognizer.ERROR_CLIENT) return "ERROR_CLIENT";
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return "ERROR_INSUFFICIENT_PERMISSIONS";
        if (error == SpeechRecognizer.ERROR_NETWORK) return "ERROR_NETWORK";
        if (error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) return "ERROR_NETWORK_TIMEOUT";
        if (error == SpeechRecognizer.ERROR_NO_MATCH) return "ERROR_NO_MATCH";
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return "ERROR_RECOGNIZER_BUSY";
        if (error == SpeechRecognizer.ERROR_SERVER) return "ERROR_SERVER";
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return "ERROR_SPEECH_TIMEOUT";
        return "ERROR_" + error;
    }

    // ================ Remote Mic ASR ================

    private void startRemoteAsrIfPossible() {
        if (!mAsrEnabled) return;
        String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        if (TextUtils.isEmpty(endpoint)) {
            mAsrEnabled = false;
            mAsrPartialText = null;
            mCallback.onAsrPartialText(null);
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_remote_missing_endpoint));
            mCallback.invalidateOptionsMenu();
            return;
        }
        android.content.pm.PackageManager pm = mContext.getPackageManager();
        if (pm != null &&
                ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.RECORD_AUDIO)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mAsrPendingStart = true;
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_permission_denied));
            return;
        }
        startRemoteAsr();
    }

    private void startRemoteAsr() {
        stopRemoteAsr();
        stopTrackAsr();
        stopAsrListening();

        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        int bufferSize = Math.max(minBuffer, sampleRate * 2);
        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                channelConfig, audioFormat, bufferSize);
        try {
            record.startRecording();
        } catch (Throwable t) {
            try { record.release(); } catch (Throwable ignored) {}
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_remote_failed));
            mCallback.invalidateOptionsMenu();
            return;
        }
        mAsrAudioRecord = record;
        mAsrRemoteRunning = true;
        mAsrRemoteChunkStartPlayerMs = -1;
        mAsrRemoteChunkBytes = 0;
        mAsrRemoteChunkHasVoice = false;
        mAsrRemoteLastVoiceAtMs = -1;
        mAsrRemoteChunkBuffer.reset();
        mAsrRemoteCommittedTail = "";
        shutdownExecutor();
        mAsrRemoteExecutor = Executors.newSingleThreadExecutor();

        mCallback.showToastText(mContext.getString(R.string.subtitle_asr_remote_start));

        mAsrRemoteThread = new Thread(() -> remoteAsrLoop(sampleRate), "asr-remote");
        mAsrRemoteThread.start();
    }

    private void stopRemoteAsr() {
        mAsrRemoteRunning = false;
        AudioRecord r = mAsrAudioRecord;
        mAsrAudioRecord = null;
        if (r != null) {
            try { r.stop(); } catch (Throwable ignored) {}
            try { r.release(); } catch (Throwable ignored) {}
        }
        Thread t = mAsrRemoteThread;
        mAsrRemoteThread = null;
        if (t != null) { try { t.interrupt(); } catch (Throwable ignored) {} }
        shutdownExecutor();
    }

    private void remoteAsrLoop(int sampleRate) {
        int bytesPerSecond = sampleRate * 2;
        int minChunkMs = 900;
        int maxChunkMs = 1800;
        int overlapMs = 450;
        int minChunkBytes = bytesPerSecond * minChunkMs / 1000;
        int maxChunkBytes = bytesPerSecond * maxChunkMs / 1000;
        int overlapBytes = bytesPerSecond * overlapMs / 1000;
        int silenceFlushMs = 650;
        byte[] buf = new byte[8 * 1024];

        while (mAsrRemoteRunning) {
            AudioRecord r = mAsrAudioRecord;
            if (r == null) break;
            int n;
            try {
                n = r.read(buf, 0, buf.length);
            } catch (Throwable t) {
                break;
            }
            if (n <= 0) continue;

            boolean voice = isVoicePcm16leMono(buf, n);
            long now = System.currentTimeMillis();
            if (voice) mAsrRemoteLastVoiceAtMs = now;

            if (mAsrRemoteChunkStartPlayerMs < 0) {
                mAsrRemoteChunkStartPlayerMs = mCallback.getCurrentPosition();
            }

            synchronized (mAsrRemoteChunkBuffer) {
                mAsrRemoteChunkBuffer.write(buf, 0, n);
                mAsrRemoteChunkBytes += n;
                if (voice) mAsrRemoteChunkHasVoice = true;

                int currentPlayerMs = mCallback.getCurrentPosition();
                if (currentPlayerMs <= 0) {
                    currentPlayerMs = (int) mAsrRemoteChunkStartPlayerMs + maxChunkMs;
                }

                if (mAsrRemoteChunkBytes >= maxChunkBytes) {
                    flushRemoteAsrChunkLocked(currentPlayerMs, overlapMs, overlapBytes);
                } else if (mAsrRemoteChunkHasVoice && mAsrRemoteChunkBytes >= minChunkBytes
                        && mAsrRemoteLastVoiceAtMs > 0
                        && (now - mAsrRemoteLastVoiceAtMs) >= silenceFlushMs) {
                    flushRemoteAsrChunkLocked(currentPlayerMs, overlapMs, overlapBytes);
                }
            }
        }

        synchronized (mAsrRemoteChunkBuffer) {
            if (mAsrRemoteChunkBytes > 0) {
                int currentPlayerMs = mCallback.getCurrentPosition();
                if (currentPlayerMs <= 0) {
                    currentPlayerMs = (int) mAsrRemoteChunkStartPlayerMs + 1500;
                }
                flushRemoteAsrChunkLocked(currentPlayerMs, 0, 0);
            }
        }
        mMainHandler.post(() -> {
            if (mAsrEnabled) {
                mCallback.showToastText(
                        mContext.getString(R.string.subtitle_asr_remote_stop));
            }
        });
    }

    private void flushRemoteAsrChunkLocked(int endPlayerMs, int overlapMs, int overlapBytes) {
        if (mAsrRemoteChunkBytes <= 0) return;
        if (!mAsrRemoteChunkHasVoice) {
            mAsrRemoteChunkBuffer.reset();
            mAsrRemoteChunkBytes = 0;
            mAsrRemoteChunkStartPlayerMs = -1;
            return;
        }

        final byte[] pcm = mAsrRemoteChunkBuffer.toByteArray();
        final int startMs = (int) Math.max(0, mAsrRemoteChunkStartPlayerMs);
        final int endMs = Math.max(startMs + 200, endPlayerMs);
        final String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        final String lang = mSettings != null ? mSettings.getAsrLanguage() : "";

        if (overlapBytes > 0 && pcm.length > overlapBytes) {
            byte[] tail = Arrays.copyOfRange(pcm, pcm.length - overlapBytes, pcm.length);
            mAsrRemoteChunkBuffer.reset();
            mAsrRemoteChunkBuffer.write(tail, 0, tail.length);
            mAsrRemoteChunkBytes = tail.length;
            mAsrRemoteChunkStartPlayerMs = Math.max(0, endMs - overlapMs);
            mAsrRemoteChunkHasVoice = false;
            mAsrRemoteLastVoiceAtMs = -1;
        } else {
            mAsrRemoteChunkBuffer.reset();
            mAsrRemoteChunkBytes = 0;
            mAsrRemoteChunkStartPlayerMs = -1;
            mAsrRemoteChunkHasVoice = false;
            mAsrRemoteLastVoiceAtMs = -1;
        }

        mMainHandler.post(() -> {
            if (mAsrEnabled) {
                mCallback.showToastText(
                        mContext.getString(R.string.subtitle_asr_remote_uploading));
            }
        });

        ensureExecutor();
        mAsrRemoteExecutor.execute(() -> {
            try {
                RemoteAsrClient.Result res = mRemoteAsrClient.transcribePcm16Mono16k(
                        endpoint, pcm, startMs, endMs, lang);
                List<RemoteAsrClient.Segment> segs = res != null ? res.segments : null;
                boolean partial = res != null && res.partial;

                if (segs == null || segs.isEmpty()) {
                    if (!partial) {
                        mMainHandler.post(() -> {
                            mAsrPartialText = null;
                            mCallback.onAsrPartialText(null);
                        });
                    }
                    return;
                }

                mMainHandler.post(() -> {
                    if (!mAsrEnabled) return;
                    if (partial) {
                        String t = joinSegmentsText(segs);
                        mAsrPartialText = TextUtils.isEmpty(t) ? null : t;
                        mCallback.onAsrPartialText(mAsrPartialText);
                        return;
                    }
                    mAsrPartialText = null;
                    for (RemoteAsrClient.Segment s : segs) {
                        if (s == null || TextUtils.isEmpty(s.text)) continue;
                        commitRemoteFinalText(s.startMs, s.endMs, s.text);
                    }
                    mCallback.onAsrPartialText(null);
                });
            } catch (Throwable t) {
                mMainHandler.post(() -> {
                    if (mAsrEnabled) {
                        mCallback.showToastText(
                                mContext.getString(R.string.subtitle_asr_remote_failed));
                    }
                });
            }
        });
    }


    // ================ Remote Track ASR (audio extraction + HTTP) ================

    private void startTrackAsrIfPossible() {
        if (!mAsrEnabled) return;
        mAsrTrackUseWhisper = false;
        mAsrWhisperModelPath = null;
        String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        if (TextUtils.isEmpty(endpoint)) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_remote_missing_endpoint));
            mCallback.invalidateOptionsMenu();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_track_unsupported));
            mCallback.invalidateOptionsMenu();
            return;
        }

        stopAsrListening();
        stopRemoteAsr();
        stopTrackAsr();

        shutdownExecutor();
        mAsrRemoteExecutor = Executors.newSingleThreadExecutor();
        mAsrRemoteCommittedTail = "";
        mAsrPartialText = null;
        mCallback.onAsrPartialText(null);

        resetTrackChunkState();

        String source = resolveTrackSource();
        if (TextUtils.isEmpty(source)) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_remote_failed));
            mCallback.invalidateOptionsMenu();
            return;
        }
        final String finalSource = handleTrackNetworkSource(source);
        if (TextUtils.isEmpty(finalSource)) return;
        if (!mAsrEnabled) return;

        int startPlayerMs = mCallback.getCurrentPosition();
        if (mAsrTrackDecoder == null) {
            mAsrTrackDecoder = new AsrAudioTrackDecoder();
        }

        mCallback.showToastText(mContext.getString(R.string.subtitle_asr_track_start));
        mAsrTrackDecoder.start(mContext, finalSource, startPlayerMs,
                new AsrAudioTrackDecoder.Listener() {
                    @Override
                    public void onPcmChunk(byte[] pcmData, int startMs, int endMs,
                                           int sampleRate, int channelCount, String audioFormat) {
                        handleTrackPcmChunk(pcmData, startMs, endMs, sampleRate,
                                channelCount, audioFormat);
                    }

                    @Override
                    public void onStopped() {
                        mMainHandler.post(() -> {
                            flushPendingTrackChunkFromDecoderStop();
                            if (mAsrEnabled) {
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_track_stop));
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        mMainHandler.post(() -> {
                            if (mAsrEnabled) {
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_remote_failed));
                            }
                        });
                    }
                });
    }

    // ================ Whisper Track ASR ================

    private void startWhisperTrackAsrIfPossible() {
        if (!mAsrEnabled) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_track_unsupported));
            mCallback.invalidateOptionsMenu();
            return;
        }
        if (!WhisperAsrEngine.isLoaded()) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_whisper_missing_native_detail,
                    String.valueOf(BuildConfig.WHISPER_ENABLED)));
            mCallback.invalidateOptionsMenu();
            return;
        }
        if (!WhisperAsrEngine.isAvailable()) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_whisper_disabled_detail,
                    String.valueOf(BuildConfig.WHISPER_ENABLED)));
            mCallback.invalidateOptionsMenu();
            return;
        }

        String modelPath = mSettings != null ? mSettings.getAsrWhisperModelPath() : "";
        File f = !TextUtils.isEmpty(modelPath) ? new File(modelPath) : null;
        if (TextUtils.isEmpty(modelPath) || f == null || !f.exists() || f.length() <= 0) {
            String modelUrl = mSettings != null ? mSettings.getAsrWhisperModelUrl() : "";
            if (TextUtils.isEmpty(modelUrl)) {
                mAsrEnabled = false;
                mCallback.showToastText(mContext.getString(
                        R.string.subtitle_asr_whisper_missing_model));
                mCallback.invalidateOptionsMenu();
                return;
            }
            modelUrl = normalizeUrl(modelUrl);
            handleWhisperModelMissing(modelUrl);
            return;
        }

        if (!WhisperAsrEngine.loadModel(modelPath)) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_whisper_missing_model));
            mCallback.invalidateOptionsMenu();
            return;
        }

        mAsrTrackUseWhisper = true;
        mAsrWhisperModelPath = modelPath;

        stopAsrListening();
        stopRemoteAsr();
        stopTrackAsr();

        shutdownExecutor();
        mAsrRemoteExecutor = Executors.newSingleThreadExecutor();
        mAsrRemoteCommittedTail = "";
        mAsrPartialText = null;
        mCallback.onAsrPartialText(null);
        resetTrackChunkState();

        String source = resolveTrackSource();
        if (TextUtils.isEmpty(source)) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(R.string.subtitle_asr_remote_failed));
            mCallback.invalidateOptionsMenu();
            return;
        }
        final String finalSource = handleTrackNetworkSource(source);
        if (TextUtils.isEmpty(finalSource)) return;
        if (!mAsrEnabled) return;

        int startPlayerMs = mCallback.getCurrentPosition();
        if (mAsrTrackDecoder == null) {
            mAsrTrackDecoder = new AsrAudioTrackDecoder();
        }

        mCallback.showToastText(mContext.getString(R.string.subtitle_asr_whisper_ready));
        mAsrTrackDecoder.start(mContext, finalSource, startPlayerMs,
                new AsrAudioTrackDecoder.Listener() {
                    @Override
                    public void onPcmChunk(byte[] pcmData, int startMs, int endMs,
                                           int sampleRate, int channelCount, String audioFormat) {
                        handleTrackPcmChunk(pcmData, startMs, endMs, sampleRate,
                                channelCount, audioFormat);
                    }

                    @Override
                    public void onStopped() {
                        mMainHandler.post(() -> {
                            flushPendingTrackChunkFromDecoderStop();
                            if (mAsrEnabled) {
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_track_stop));
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        mMainHandler.post(() -> {
                            if (mAsrEnabled) {
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_remote_failed));
                            }
                        });
                    }
                });
    }

    // ================ Track ASR helpers ================

    private void stopTrackAsr() {
        AsrAudioTrackDecoder d = mAsrTrackDecoder;
        if (d != null) {
            try { d.stop(); } catch (Throwable ignored) {}
        }
        mAsrTrackUseWhisper = false;
        mAsrWhisperModelPath = null;
        synchronized (mAsrTrackChunkBuffer) {
            if (mAsrTrackChunkBytes > 0 && mAsrTrackChunkStartMs >= 0) {
                flushTrackChunkLocked(mAsrTrackChunkStartMs + 1500, 0, 0);
            } else {
                resetTrackChunkState();
            }
        }
    }

    private void handleTrackPcmChunk(byte[] pcmData, int startMs, int endMs,
                                     int sampleRate, int channelCount, String audioFormat) {
        if (!mAsrEnabled || pcmData == null || pcmData.length <= 0) return;
        int sr = Math.max(1, sampleRate);
        int ch = Math.max(1, channelCount);
        int bytesPerSecond = sr * 2 * ch;
        int minChunkMs = 900;
        int maxChunkMs = 1800;
        int overlapMs = 450;
        int minChunkBytes = bytesPerSecond * minChunkMs / 1000;
        int maxChunkBytes = bytesPerSecond * maxChunkMs / 1000;
        int overlapBytes = bytesPerSecond * overlapMs / 1000;
        int silenceFlushMs = 650;

        long avgAbs = avgAbsPcm16le(pcmData, pcmData.length, ch);
        boolean voice = avgAbs >= 200;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - mAsrTrackLastVoiceDebugAtMs >= 1000) {
            mAsrTrackLastVoiceDebugAtMs = now;
            Log.d(TAG, "ASR(track) pcm voice=" + voice + " avg=" + avgAbs
                    + " sr=" + sr + " ch=" + ch + " startMs=" + startMs
                    + " endMs=" + endMs + " bytes=" + pcmData.length);
        }

        synchronized (mAsrTrackChunkBuffer) {
            mAsrTrackSampleRate = sr;
            mAsrTrackChannelCount = ch;
            mAsrTrackAudioFormat = TextUtils.isEmpty(audioFormat) ? "pcm_s16le" : audioFormat;
            mAsrTrackLastEndMs = Math.max(mAsrTrackLastEndMs, endMs);

            if (mAsrTrackChunkStartMs < 0) {
                mAsrTrackChunkStartMs = Math.max(0, startMs);
            }
            try {
                mAsrTrackChunkBuffer.write(pcmData, 0, pcmData.length);
                mAsrTrackChunkBytes += pcmData.length;
            } catch (Throwable ignored) {}
            if (voice) {
                mAsrTrackChunkHasVoice = true;
                mAsrTrackLastVoiceMs = endMs;
            }

            int durationMs = Math.max(0, endMs - mAsrTrackChunkStartMs);
            if (mAsrTrackChunkBytes >= maxChunkBytes || durationMs >= maxChunkMs) {
                flushTrackChunkLocked(endMs, overlapMs, overlapBytes);
            } else if (mAsrTrackChunkHasVoice && mAsrTrackChunkBytes >= minChunkBytes
                    && mAsrTrackLastVoiceMs > 0
                    && (endMs - mAsrTrackLastVoiceMs) >= silenceFlushMs) {
                flushTrackChunkLocked(endMs, overlapMs, overlapBytes);
            }
        }
    }

    private void flushPendingTrackChunkFromDecoderStop() {
        synchronized (mAsrTrackChunkBuffer) {
            if (mAsrTrackChunkBytes > 0 && mAsrTrackChunkStartMs >= 0) {
                int endMs = mAsrTrackLastEndMs > 0
                        ? mAsrTrackLastEndMs : (mAsrTrackChunkStartMs + 1500);
                flushTrackChunkLocked(endMs, 0, 0);
            }
        }
    }

    private void flushTrackChunkLocked(int endMs, int overlapMs, int overlapBytes) {
        if (mAsrTrackChunkBytes <= 0 || mAsrTrackChunkStartMs < 0) return;
        if (!mAsrTrackChunkHasVoice) {
            resetTrackChunkState();
            return;
        }

        final byte[] pcm = mAsrTrackChunkBuffer.toByteArray();
        final int startMs = mAsrTrackChunkStartMs;
        final int safeEndMs = Math.max(startMs + 200, endMs);
        final String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        final String lang = mSettings != null ? mSettings.getAsrLanguage() : "";
        final int sr = mAsrTrackSampleRate;
        final int ch = mAsrTrackChannelCount;
        final String fmt = mAsrTrackAudioFormat;

        Log.i(TAG, "ASR(track) flush startMs=" + startMs + " endMs=" + safeEndMs
                + " bytes=" + pcm.length + " sr=" + sr + " ch=" + ch
                + " whisper=" + mAsrTrackUseWhisper);

        if (overlapBytes > 0 && pcm.length > overlapBytes) {
            byte[] tail = Arrays.copyOfRange(pcm, pcm.length - overlapBytes, pcm.length);
            mAsrTrackChunkBuffer.reset();
            try {
                mAsrTrackChunkBuffer.write(tail, 0, tail.length);
                mAsrTrackChunkBytes = tail.length;
                mAsrTrackChunkStartMs = Math.max(0, safeEndMs - overlapMs);
            } catch (Throwable ignored) {
                mAsrTrackChunkBytes = 0;
                mAsrTrackChunkStartMs = -1;
            }
            mAsrTrackChunkHasVoice = false;
            mAsrTrackLastVoiceMs = -1;
        } else {
            resetTrackChunkState();
        }

        mMainHandler.post(() -> {
            if (mAsrEnabled) {
                mCallback.showToastText(
                        mContext.getString(R.string.subtitle_asr_remote_uploading));
            }
        });

        ensureExecutor();
        mAsrRemoteExecutor.execute(() -> {
            try {
                RemoteAsrClient.Result res;
                if (mAsrTrackUseWhisper) {
                    String json = WhisperAsrEngine.transcribePcm16Json(
                            pcm, sr, ch, startMs, safeEndMs, lang);
                    res = parseRemoteAsrResultJson(json, startMs, safeEndMs);
                } else {
                    res = mRemoteAsrClient.transcribePcm(
                            endpoint, pcm, startMs, safeEndMs, sr, ch, fmt, lang);
                }
                List<RemoteAsrClient.Segment> segs = res != null ? res.segments : null;
                boolean partial = res != null && res.partial;

                long now = android.os.SystemClock.elapsedRealtime();
                if (now - mAsrTrackLastResultDebugAtMs >= 1000) {
                    mAsrTrackLastResultDebugAtMs = now;
                    int n = segs != null ? segs.size() : 0;
                    String t = n > 0 ? joinSegmentsText(segs) : "";
                    if (t != null && t.length() > 240) t = t.substring(0, 240);
                    Log.i(TAG, "ASR(track) result partial=" + partial + " segs=" + n + " text=" + t);
                }

                if (segs == null || segs.isEmpty()) {
                    if (!partial) {
                        mMainHandler.post(() -> {
                            mAsrPartialText = null;
                            mCallback.onAsrPartialText(null);
                        });
                    }
                    return;
                }

                mMainHandler.post(() -> {
                    if (!mAsrEnabled) return;
                    if (partial) {
                        String t = joinSegmentsText(segs);
                        mAsrPartialText = TextUtils.isEmpty(t) ? null : t;
                        mCallback.onAsrPartialText(mAsrPartialText);
                        return;
                    }
                    mAsrPartialText = null;
                    for (RemoteAsrClient.Segment s : segs) {
                        if (s == null || TextUtils.isEmpty(s.text)) continue;
                        commitRemoteFinalText(s.startMs, s.endMs, s.text);
                    }
                    mCallback.onAsrPartialText(null);
                });
            } catch (Throwable t) {
                mMainHandler.post(() -> {
                    if (mAsrEnabled) {
                        mCallback.showToastText(
                                mContext.getString(R.string.subtitle_asr_remote_failed));
                    }
                });
            }
        });
    }


    // ================ Track Source Resolution & Network Handling ================

    @Nullable
    private String resolveTrackSource() {
        String source = mCallback.getDataSource();
        if (TextUtils.isEmpty(source)) {
            source = mCallback.getVideoPath();
        }
        if (TextUtils.isEmpty(source)) {
            Uri uri = mCallback.getVideoUri();
            if (uri != null) source = String.valueOf(uri);
        }
        return source;
    }

    @Nullable
    private String handleTrackNetworkSource(String source) {
        source = normalizeUrl(source);
        if (!isNetworkUrl(source)) return source;

        if (source.contains(".m3u8")) {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_track_hls_unsupported));
            mCallback.invalidateOptionsMenu();
            return null;
        }

        if (mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists()
                && mAsrTrackDownloadedFile.length() > 0) {
            return mAsrTrackDownloadedFile.getAbsolutePath();
        }

        if (mAsrTrackDownloadUrl != null && mAsrTrackDownloadUrl.equals(source)
                && mAsrTrackDownloadId > 0) {
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_track_downloading));
            return null;
        }

        boolean started = prepareAsrTrackSource(source);
        if (started) {
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_track_downloading));
            return null;
        }
        if (mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists()
                && mAsrTrackDownloadedFile.length() > 0) {
            return mAsrTrackDownloadedFile.getAbsolutePath();
        }

        mAsrEnabled = false;
        mCallback.showToastText(mContext.getString(
                R.string.subtitle_asr_track_download_failed));
        mCallback.invalidateOptionsMenu();
        return null;
    }

    // ================ Download Management ================

    private boolean prepareAsrTrackSource(String url) {
        try {
            url = normalizeUrl(url);
            if (TextUtils.isEmpty(url) || !isNetworkUrl(url)) return false;
            if (url.contains(".m3u8")) return false;

            File dir = mCallback.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (dir == null) return false;
            File asrDir = new File(dir, "asr");
            if (!asrDir.exists() && !asrDir.mkdirs()) return false;

            String ext = extractExtension(url, ".mp4");
            String name = "asr_" + sha1(url) + ext;
            mAsrTrackDownloadedFile = new File(asrDir, name);
            mAsrTrackDownloadUrl = url;

            if (mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                return false;
            }

            DownloadManager dm = (DownloadManager) mContext.getSystemService(
                    Context.DOWNLOAD_SERVICE);
            if (dm == null) return false;

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            req.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_MOVIES,
                    "asr/" + name);
            mAsrTrackDownloadId = dm.enqueue(req);

            if (mAsrTrackDownloadReceiver == null) {
                mAsrTrackDownloadReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(
                                intent.getAction())) return;
                        long id = intent.getLongExtra(
                                DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                        if (id != mAsrTrackDownloadId) return;
                        handleAsrTrackDownloadComplete();
                    }
                };
                mContext.registerReceiver(mAsrTrackDownloadReceiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "prepareAsrTrackSource failed", t);
            return false;
        }
    }

    private void handleAsrTrackDownloadComplete() {
        try {
            DownloadManager dm = (DownloadManager) mContext.getSystemService(
                    Context.DOWNLOAD_SERVICE);
            if (dm == null) return;
            DownloadManager.Query q = new DownloadManager.Query()
                    .setFilterById(mAsrTrackDownloadId);
            Cursor c = dm.query(q);
            if (c == null) return;
            boolean ok = false;
            try {
                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS));
                    ok = status == DownloadManager.STATUS_SUCCESSFUL;
                }
            } finally {
                c.close();
            }
            if (ok && mAsrTrackDownloadedFile != null
                    && mAsrTrackDownloadedFile.exists()
                    && mAsrTrackDownloadedFile.length() > 0) {
                mCallback.showToastText(mContext.getString(
                        R.string.subtitle_asr_track_downloaded));
                startTrackAsrIfPossible();
            } else {
                mCallback.showToastText(mContext.getString(
                        R.string.subtitle_asr_track_download_failed));
            }
        } catch (Throwable t) {
            try {
                mCallback.showToastText(mContext.getString(
                        R.string.subtitle_asr_track_download_failed));
            } catch (Throwable ignored) {}
        }
    }

    private void unregisterTrackDownloadReceiver() {
        if (mAsrTrackDownloadReceiver != null) {
            try {
                mContext.unregisterReceiver(mAsrTrackDownloadReceiver);
            } catch (Throwable ignored) {}
            mAsrTrackDownloadReceiver = null;
        }
    }

    private void handleWhisperModelMissing(String modelUrl) {
        final String finalModelUrl = modelUrl;
        if (mWhisperModelDownloadedFile != null
                && mWhisperModelDownloadedFile.exists()
                && mWhisperModelDownloadedFile.length() > 0
                && modelUrl.equals(mWhisperModelDownloadUrl)) {
            if (mSettings != null) {
                mSettings.setAsrWhisperModelPath(
                        mWhisperModelDownloadedFile.getAbsolutePath());
            }
            mMainHandler.post(() -> startWhisperTrackAsrIfPossible());
            return;
        }
        if (modelUrl.equals(mWhisperModelDownloadUrl)
                && mWhisperModelDownloadThread != null
                && mWhisperModelDownloadThread.isAlive()) {
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_whisper_downloading));
            return;
        }
        // Signal to activity that a dialog is needed (can't show from here easily)
        // We'll use a simple approach: show a message and start download
        downloadWhisperModel(finalModelUrl);
    }

    private void downloadWhisperModel(String url) {
        boolean started = prepareWhisperModelDownload(url);
        if (started) {
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_whisper_downloading));
        } else if (mWhisperModelDownloadedFile != null
                && mWhisperModelDownloadedFile.exists()
                && mWhisperModelDownloadedFile.length() > 0) {
            if (mSettings != null) {
                mSettings.setAsrWhisperModelPath(
                        mWhisperModelDownloadedFile.getAbsolutePath());
            }
            mMainHandler.post(() -> startWhisperTrackAsrIfPossible());
        } else {
            mAsrEnabled = false;
            mCallback.showToastText(mContext.getString(
                    R.string.subtitle_asr_whisper_download_failed));
            mCallback.invalidateOptionsMenu();
        }
    }

    private boolean prepareWhisperModelDownload(String url) {
        try {
            url = normalizeUrl(url);
            if (TextUtils.isEmpty(url) || !isNetworkUrl(url)) return false;

            File modelDir = new File(mCallback.getFilesDir(), "asr/models");
            if (!modelDir.exists() && !modelDir.mkdirs()) return false;

            String ext = extractExtension(url, ".bin");
            String name = "whisper_" + sha1(url) + ext;
            mWhisperModelDownloadedFile = new File(modelDir, name);
            mWhisperModelDownloadUrl = url;

            if (mWhisperModelDownloadedFile.exists()
                    && mWhisperModelDownloadedFile.length() > 0) {
                if (mSettings != null) {
                    mSettings.setAsrWhisperModelPath(
                            mWhisperModelDownloadedFile.getAbsolutePath());
                }
                return false;
            }
            cancelWhisperModelDownload();

            HttpFileDownloader downloader = new HttpFileDownloader();
            mWhisperModelDownloader = downloader;
            final String finalUrl = url;
            Thread t = new Thread(() -> downloader.download(finalUrl,
                    mWhisperModelDownloadedFile,
                    new HttpFileDownloader.Listener() {
                        @Override
                        public void onProgress(long downloadedBytes, long totalBytes) {
                            mMainHandler.post(() -> {
                                int percent = totalBytes > 0
                                        ? (int) (downloadedBytes * 100 / totalBytes) : 0;
                                String sofarText = formatBytes(downloadedBytes);
                                String totalText = totalBytes > 0
                                        ? formatBytes(totalBytes) : "?";
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_whisper_downloading_progress,
                                        percent, sofarText, totalText));
                            });
                        }

                        @Override
                        public void onSuccess(File file) {
                            mMainHandler.post(() -> {
                                if (mSettings != null) {
                                    mSettings.setAsrWhisperModelPath(
                                            file.getAbsolutePath());
                                }
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_whisper_downloaded_ready));
                                // Delete incomplete download on error
                                if (mWhisperModelDownloadedFile != null
                                        && mWhisperModelDownloadedFile.exists()) {
                                    mWhisperModelDownloadedFile.delete();
                                }
                                if (mAsrEnabled) {
                                    mMainHandler.post(() ->
                                            startWhisperTrackAsrIfPossible());
                                }
                            });
                        }

                        @Override
                        public void onError(Throwable err) {
                            mMainHandler.post(() -> {
                                mCallback.showToastText(mContext.getString(
                                        R.string.subtitle_asr_whisper_download_failed));
                            });
                        }
                    }), "whisper-model-download");
            mWhisperModelDownloadThread = t;
            t.start();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "prepareWhisperModelDownload failed", t);
            return false;
        }
    }

    private void cancelWhisperModelDownload() {
        HttpFileDownloader d = mWhisperModelDownloader;
        mWhisperModelDownloader = null;
        if (d != null) { try { d.cancel(); } catch (Throwable ignored) {} }
        Thread t = mWhisperModelDownloadThread;
        mWhisperModelDownloadThread = null;
        if (t != null) { try { t.interrupt(); } catch (Throwable ignored) {} }
    }

    // ================ Text/Result Processing ================

    private void commitRemoteFinalText(int startMs, int endMs, String rawText) {
        String text = rawText != null ? rawText.trim() : "";
        if (TextUtils.isEmpty(text)) return;

        String delta = removeTextOverlap(mAsrRemoteCommittedTail, text);
        if (TextUtils.isEmpty(delta)) return;

        List<String> parts = splitToSentences(delta);
        if (parts.isEmpty()) return;

        int duration = Math.max(200, endMs - startMs);
        int total = 0;
        for (String p : parts) {
            if (p != null) total += p.length();
        }
        if (total <= 0) {
            mCallback.addSubtitleCueExplicit(startMs, endMs, delta);
            rememberRemoteTail(text);
            return;
        }

        int used = 0;
        int segStart = startMs;
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            if (TextUtils.isEmpty(p)) continue;
            used += p.length();
            int segEnd = startMs + (int) ((long) duration * used / total);
            segEnd = Math.max(segStart + 200, segEnd);
            if (i == parts.size() - 1) {
                segEnd = endMs;
            } else {
                segEnd = Math.min(endMs, segEnd);
            }
            mCallback.addSubtitleCueExplicit(segStart, segEnd, p);
            segStart = segEnd;
        }
        rememberRemoteTail(text);
    }

    private void rememberRemoteTail(String fullText) {
        if (TextUtils.isEmpty(fullText)) return;
        String t = fullText.trim();
        int max = 80;
        if (t.length() > max) {
            t = t.substring(t.length() - max);
        }
        mAsrRemoteCommittedTail = t;
    }

    private String removeTextOverlap(String prevTail, String current) {
        if (TextUtils.isEmpty(current)) return "";
        if (TextUtils.isEmpty(prevTail)) return current;
        String a = prevTail;
        String b = current;
        int max = Math.min(60, Math.min(a.length(), b.length()));
        int best = 0;
        for (int len = 1; len <= max; len++) {
            if (a.regionMatches(a.length() - len, b, 0, len)) {
                best = len;
            }
        }
        if (best >= 3 && best < b.length()) {
            return b.substring(best).trim();
        }
        return b;
    }

    private List<String> splitToSentences(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            boolean hard = c == '\u3002' || c == '\uff01' || c == '\uff1f'
                    || c == '.' || c == '!' || c == '?';
            boolean soft = (c == '\uff0c' || c == ',' || c == ';' || c == '\uff1b')
                    && sb.length() >= 18;
            if (hard || soft) {
                String s = sb.toString().trim();
                if (!TextUtils.isEmpty(s)) out.add(s);
                sb.setLength(0);
            }
        }
        String rest = sb.toString().trim();
        if (!TextUtils.isEmpty(rest)) out.add(rest);
        return out;
    }

    private String joinSegmentsText(List<RemoteAsrClient.Segment> segs) {
        if (segs == null || segs.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (RemoteAsrClient.Segment s : segs) {
            if (s == null || TextUtils.isEmpty(s.text)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(s.text.trim());
        }
        return sb.toString().trim();
    }

    private RemoteAsrClient.Result parseRemoteAsrResultJson(String json, int startMs, int endMs) {
        try {
            if (TextUtils.isEmpty(json)) {
                return new RemoteAsrClient.Result(false, new ArrayList<>());
            }
            JSONObject obj = new JSONObject(json);
            boolean partial = obj.optBoolean("partial", false);
            JSONArray segArr = obj.optJSONArray("segments");
            ArrayList<RemoteAsrClient.Segment> segs = new ArrayList<>();
            if (segArr != null) {
                for (int i = 0; i < segArr.length(); i++) {
                    JSONObject s = segArr.optJSONObject(i);
                    if (s == null) continue;
                    int s0 = s.optInt("startMs", startMs);
                    int s1 = s.optInt("endMs", endMs);
                    String t = s.optString("text", "");
                    if (!TextUtils.isEmpty(t)) {
                        segs.add(new RemoteAsrClient.Segment(s0, s1, t));
                    }
                }
                return new RemoteAsrClient.Result(partial, segs);
            }
            String text = obj.optString("text", "");
            if (!TextUtils.isEmpty(text)) {
                segs.add(new RemoteAsrClient.Segment(startMs, endMs, text));
            }
            return new RemoteAsrClient.Result(partial, segs);
        } catch (Throwable ignored) {
            return new RemoteAsrClient.Result(false, new ArrayList<>());
        }
    }

    // ================ Utility Methods ================

    private boolean isNetworkUrl(String url) {
        String u = normalizeUrl(url);
        return u != null && (u.startsWith("http://") || u.startsWith("https://"));
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        String out = url.trim();
        while (out.startsWith("`")) {
            out = out.substring(1).trim();
        }
        while (out.endsWith("`") || out.endsWith(",")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        return out;
    }

    private String extractExtension(String url, String defaultExt) {
        try {
            String path = Uri.parse(url).getPath();
            if (!TextUtils.isEmpty(path)) {
                int dot = path.lastIndexOf('.');
                if (dot > 0 && dot < path.length() - 1) {
                    String e = path.substring(dot);
                    if (e.length() <= 8) return e;
                }
            }
        } catch (Throwable ignored) {}
        return defaultExt;
    }

    private void resetTrackChunkState() {
        mAsrTrackChunkBuffer.reset();
        mAsrTrackChunkBytes = 0;
        mAsrTrackChunkStartMs = -1;
        mAsrTrackChunkHasVoice = false;
        mAsrTrackLastVoiceMs = -1;
        mAsrTrackLastEndMs = -1;
    }

    private void shutdownExecutor() {
        ExecutorService ex = mAsrRemoteExecutor;
        mAsrRemoteExecutor = null;
        if (ex != null) { try { ex.shutdownNow(); } catch (Throwable ignored) {} }
    }

    private void ensureExecutor() {
        if (mAsrRemoteExecutor == null) {
            mAsrRemoteExecutor = Executors.newSingleThreadExecutor();
        }
    }

    private boolean isVoicePcm16leMono(byte[] pcm, int length) {
        return isVoicePcm16le(pcm, length, 1, 700);
    }

    private boolean isVoicePcm16le(byte[] pcm, int length, int channelCount, int threshold) {
        if (pcm == null || length <= 2) return false;
        int ch = Math.max(1, channelCount);
        int sampleCount = length / 2;
        int frameCount = sampleCount / ch;
        if (frameCount <= 0) return false;
        long sum = 0;
        int step = Math.max(1, frameCount / 160);
        int picked = 0;
        for (int f = 0; f < frameCount; f += step) {
            long frameAbs = 0;
            int base = f * ch * 2;
            for (int c = 0; c < ch; c++) {
                int i = base + c * 2;
                if (i + 1 >= length) break;
                int lo = pcm[i] & 0xff;
                int hi = pcm[i + 1];
                short v = (short) ((hi << 8) | lo);
                frameAbs += Math.abs((int) v);
            }
            sum += (frameAbs / ch);
            picked++;
        }
        if (picked <= 0) return false;
        return (sum / picked) >= threshold;
    }

    private long avgAbsPcm16le(byte[] pcm, int length, int channelCount) {
        if (pcm == null || length <= 2) return 0;
        int ch = Math.max(1, channelCount);
        int sampleCount = length / 2;
        int frameCount = sampleCount / ch;
        if (frameCount <= 0) return 0;
        long sum = 0;
        int step = Math.max(1, frameCount / 160);
        int picked = 0;
        for (int f = 0; f < frameCount; f += step) {
            long frameAbs = 0;
            int base = f * ch * 2;
            for (int c = 0; c < ch; c++) {
                int i = base + c * 2;
                if (i + 1 >= length) break;
                int lo = pcm[i] & 0xff;
                int hi = pcm[i + 1];
                short v = (short) ((hi << 8) | lo);
                frameAbs += Math.abs((int) v);
            }
            sum += (frameAbs / ch);
            picked++;
        }
        return picked > 0 ? (sum / picked) : 0;
    }

    private static String sha1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(text != null ? text.hashCode() : 0);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "0B";
        if (bytes < 1024) return bytes + "B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1fKB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1fMB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2fGB", gb);
    }
}
