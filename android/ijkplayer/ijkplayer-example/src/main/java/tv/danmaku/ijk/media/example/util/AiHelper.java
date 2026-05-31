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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.example.util;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.player.IjkAIEngine;

/**
 * Manages AI/LLM engine lifecycle: model download, engine init, prompt dialog.
 * Delegates UI updates (subtitle overlay, toast) to Callback.
 */
public class AiHelper {
    private static final String TAG = "AiHelper";

    /** Default LLM model download URL (Qwen2.5-0.5B GGUF, ~350MB) */
    private static final String AI_MODEL_DEFAULT_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";

    /** Chinese mirror URL for the same model (hf-mirror.com syncs from Hugging Face) */
    private static final String AI_MODEL_MIRROR_URL =
            "https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";

    public interface Callback {
        /** Called when AI partial text should be displayed in subtitle overlay. */
        void onAiPartialText(@Nullable String text);
        /** Called when user sends a prompt to the AI. */
        void onUserPromptSent(String prompt);
        /** Called to show a toast-like message via the media controller. */
        void showToastText(String text);
        /** Called to invalidate the options menu. */
        void invalidateOptionsMenu();
        /** Returns the current AI partial text (may be null). */
        @Nullable String getAiPartialText();
    }

    private final Context mContext;
    private final Settings mSettings;
    private final Handler mMainHandler;
    private final Callback mCallback;

    private IjkAIEngine mAiEngine;
    private boolean mAiEnabled;
    private String mAiPartialText;
    private File mAiModelDownloadedFile;
    private String mAiModelDownloadUrl;
    private HttpFileDownloader mAiModelDownloader;
    private Thread mAiModelDownloadThread;

    public AiHelper(Context context, Settings settings, Callback callback) {
        mContext = context.getApplicationContext();
        mSettings = settings;
        mMainHandler = new Handler(Looper.getMainLooper());
        mCallback = callback;
    }

    /** Whether AI is enabled and engine is initialized. */
    public boolean isEnabled() {
        return mAiEnabled;
    }

    /** Returns current AI partial text (may be null). */
    @Nullable
    public String getPartialText() {
        return mAiPartialText;
    }

    /** Whether a model download is in progress. */
    public boolean isDownloading() {
        return mAiModelDownloader != null;
    }

    /**
     * Toggle AI: if already initialized, show prompt dialog; otherwise start AI.
     * Called from menu item handler.
     */
    public void toggleAi(AppCompatActivity activity) {
        if (mAiEnabled && mAiEngine != null && mAiEngine.isInitialized()) {
            showAiPromptDialog(activity);
        } else if (mAiModelDownloader != null) {
            // Already downloading, do nothing
        } else {
            startAiIfPossible(activity);
        }
    }

    /** Release AI engine and cancel any download. */
    public void stop() {
        mAiEnabled = false;
        mAiPartialText = null;
        if (mAiEngine != null) {
            try {
                mAiEngine.release();
            } catch (Throwable ignored) {
            }
            mAiEngine = null;
        }
        cancelDownload();
        mCallback.onAiPartialText(null);
        mCallback.invalidateOptionsMenu();
    }

    /** Cancel model download. */
    public void cancelDownloadIfActive() {
        cancelDownload();
    }

    /** Check whether AI is currently waiting for a response (partial text is non-null). */
    public boolean hasActiveOutput() {
        return !TextUtils.isEmpty(mAiPartialText);
    }

    // ================ Private ================

    private void startAiIfPossible(AppCompatActivity activity) {
        if (mAiEngine != null && mAiEngine.isInitialized()) {
            showAiPromptDialog(activity);
            return;
        }

        // 1. Check model path from settings
        String modelPath = mSettings != null ? mSettings.getAiModelPath() : null;
        if (TextUtils.isEmpty(modelPath)) {
            // Try the download directory
            File modelDir = new File(mContext.getExternalFilesDir(null), "models");
            if (modelDir != null) {
                modelDir.mkdirs();
                File[] ggufFiles = modelDir.listFiles((dir, name) -> name.endsWith(".gguf"));
                if (ggufFiles != null && ggufFiles.length > 0) {
                    modelPath = ggufFiles[0].getAbsolutePath();
                }
            }
        }

        if (TextUtils.isEmpty(modelPath)) {
            // Auto-download default model
            startAiModelDownload(activity);
            return;
        }

        // 2. Init engine
        initAiEngine(activity, modelPath);
    }

    private void initAiEngine(AppCompatActivity activity, String modelPath) {
        int nThreads = mSettings != null ? mSettings.getAiThreadCount() : 4;
        mAiEngine = new IjkAIEngine();
        boolean initOk;
        try {
            initOk = mAiEngine.init(IjkAIEngine.TYPE_LLM, modelPath, nThreads);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "AI native library not available", e);
            initOk = false;
        } catch (Throwable t) {
            Log.e(TAG, "AI engine init error", t);
            initOk = false;
        }
        if (!initOk) {
            mAiEngine = null;
            mAiPartialText = activity.getString(R.string.ai_llm_native_missing);
            mCallback.onAiPartialText(mAiPartialText);
            mAiEnabled = false;
            mCallback.invalidateOptionsMenu();
            return;
        }
        if (mSettings != null) {
            mSettings.setAiModelPath(modelPath);
        }
        mAiEnabled = true;
        mAiPartialText = null;
        mCallback.onAiPartialText(null);
        mCallback.showToastText(activity.getString(R.string.ai_llm_init_ok));
        mCallback.invalidateOptionsMenu();
        showAiPromptDialog(activity);
    }

    private void showAiPromptDialog(final AppCompatActivity activity) {
        final android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint(R.string.ai_llm_prompt_hint);
        input.setSingleLine(false);
        input.setMinLines(3);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.ai_llm_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.ai_llm_send, (dialog, which) -> {
                    String prompt = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(prompt)) return;

                    mCallback.onUserPromptSent(prompt);

                    mAiPartialText = activity.getString(R.string.ai_llm_waiting);
                    mCallback.onAiPartialText(mAiPartialText);

                    int ret = mAiEngine.prompt(prompt, mAiCallback);
                    if (ret != 0) {
                        mAiPartialText = activity.getString(R.string.ai_llm_init_failed);
                        mCallback.onAiPartialText(mAiPartialText);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final IjkAIEngine.Callback mAiCallback = new IjkAIEngine.Callback() {
        @Override
        public void onText(String text, boolean isComplete) {
            mMainHandler.post(() -> {
                if (isComplete) {
                    mAiPartialText = null;
                    mCallback.onAiPartialText(null);
                } else {
                    mAiPartialText = text;
                    mCallback.onAiPartialText(text);
                }
            });
        }

        @Override
        public void onError(String error) {
            mMainHandler.post(() -> {
                mAiPartialText = mContext.getString(R.string.ai_llm_error_prefix) + error;
                mAiEnabled = false;
                mCallback.onAiPartialText(mAiPartialText);
                mCallback.invalidateOptionsMenu();
            });
        }
    };

    private void startAiModelDownload(final AppCompatActivity activity) {
        startAiModelDownload(AI_MODEL_DEFAULT_URL, activity);
    }

    private void startAiModelDownload(final String url, final AppCompatActivity activity) {
        if (mAiModelDownloader != null) {
            return;
        }
        File modelDir = new File(mContext.getExternalFilesDir(null), "models");
        String name = "llm_" + sha1(AI_MODEL_DEFAULT_URL) + ".gguf";
        mAiModelDownloadedFile = new File(modelDir, name);
        mAiModelDownloadUrl = url;

        if (mAiModelDownloadedFile.exists() && mAiModelDownloadedFile.length() > 0) {
            initAiEngine(activity, mAiModelDownloadedFile.getAbsolutePath());
            return;
        }

        mAiPartialText = activity.getString(R.string.ai_llm_downloading);
        mCallback.onAiPartialText(mAiPartialText);

        mAiEnabled = true;
        mCallback.invalidateOptionsMenu();

        HttpFileDownloader downloader = new HttpFileDownloader();
        mAiModelDownloader = downloader;
        Thread t = new Thread(() -> downloader.download(url,
                mAiModelDownloadedFile,
                new HttpFileDownloader.Listener() {
                    @Override
                    public void onProgress(long downloadedBytes, long totalBytes) {
                        mMainHandler.post(() -> {
                            int percent = totalBytes > 0
                                    ? (int) (downloadedBytes * 100 / totalBytes) : 0;
                            String sofarText = formatBytes(downloadedBytes);
                            String totalText = totalBytes > 0
                                    ? formatBytes(totalBytes) : "?";
                            mAiPartialText = activity.getString(
                                    R.string.ai_llm_downloading_progress,
                                    percent, sofarText, totalText);
                            mCallback.onAiPartialText(mAiPartialText);
                        });
                    }

                    @Override
                    public void onSuccess(File file) {
                        mMainHandler.post(() -> {
                            mAiPartialText = activity.getString(R.string.ai_llm_downloaded_ready);
                            mCallback.onAiPartialText(mAiPartialText);
                            mAiModelDownloader = null;
                            mAiModelDownloadThread = null;
                            initAiEngine(activity, file.getAbsolutePath());
                        });
                    }

                    @Override
                    public void onError(Throwable err) {
                        final boolean retryWithMirror =
                                AI_MODEL_DEFAULT_URL.equals(url)
                                && !TextUtils.isEmpty(AI_MODEL_MIRROR_URL);
                        mMainHandler.post(() -> {
                            if (retryWithMirror) {
                                // Primary source failed, retry with Chinese mirror
                                mAiModelDownloader = null;
                                mAiModelDownloadThread = null;
                                mAiPartialText = activity.getString(
                                        R.string.ai_llm_download_retry_mirror);
                                mCallback.onAiPartialText(mAiPartialText);
                                startAiModelDownload(AI_MODEL_MIRROR_URL, activity);
                            } else {
                                mAiModelDownloader = null;
                                mAiModelDownloadThread = null;
                                mAiPartialText = activity.getString(R.string.ai_llm_download_failed);
                                mCallback.onAiPartialText(mAiPartialText);
                                mAiEnabled = false;
                                mCallback.invalidateOptionsMenu();
                            }
                        });
                    }
                }), "ai-model-download");
        mAiModelDownloadThread = t;
        t.start();
    }

    private void cancelDownload() {
        HttpFileDownloader d = mAiModelDownloader;
        mAiModelDownloader = null;
        if (d != null) {
            try {
                d.cancel();
            } catch (Throwable ignored) {
            }
        }
        Thread t = mAiModelDownloadThread;
        mAiModelDownloadThread = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
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
        if (bytes < 0) {
            return "0B";
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1fKB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1fMB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2fGB", gb);
    }
}
