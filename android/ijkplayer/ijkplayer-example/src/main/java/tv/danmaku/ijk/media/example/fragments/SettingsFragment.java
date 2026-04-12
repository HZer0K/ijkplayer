/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
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

package tv.danmaku.ijk.media.example.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.util.HttpFileDownloader;

public class SettingsFragment extends PreferenceFragmentCompat {
    private File mWhisperModelDownloadedFile;
    private String mWhisperModelDownloadUrl;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private HttpFileDownloader mWhisperDownloader;
    private Thread mWhisperDownloadThread;

    public static SettingsFragment newInstance() {
        SettingsFragment f = new SettingsFragment();
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getPreferenceManager() != null && getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(mPrefListener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getPreferenceManager() != null && getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(mPrefListener);
        }
        HttpFileDownloader d = mWhisperDownloader;
        mWhisperDownloader = null;
        if (d != null) {
            d.cancel();
        }
        Thread t = mWhisperDownloadThread;
        mWhisperDownloadThread = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
    }

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            (prefs, key) -> {
                if (prefs == null || TextUtils.isEmpty(key)) {
                    return;
                }
                String presetKey = getString(R.string.pref_key_asr_whisper_model_preset);
                if (presetKey.equals(key)) {
                    String url = prefs.getString(presetKey, "");
                    if (!TextUtils.isEmpty(url)) {
                        maybePromptDownloadWhisperModel(url);
                    }
                }
            };

    private void maybePromptDownloadWhisperModel(String url) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        url = normalizeUrl(url);
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String pathKey = getString(R.string.pref_key_asr_whisper_model_path);
        String existingPath = PreferenceManager.getDefaultSharedPreferences(context).getString(pathKey, "");
        File f = !TextUtils.isEmpty(existingPath) ? new File(existingPath) : null;
        if (f != null && f.exists() && f.length() > 0) {
            return;
        }
        if (url.equals(mWhisperModelDownloadUrl) && mWhisperDownloadThread != null && mWhisperDownloadThread.isAlive()) {
            Toast.makeText(context, getString(R.string.subtitle_asr_whisper_downloading), Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalUrl = url;
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.subtitle_asr_whisper_download_prompt_title))
                .setMessage(getString(R.string.subtitle_asr_whisper_download_prompt_message))
                .setPositiveButton(getString(R.string.subtitle_asr_whisper_download_start), (d, which) -> {
                    boolean started = prepareWhisperModelDownload(finalUrl);
                    if (started) {
                        Toast.makeText(context, getString(R.string.subtitle_asr_whisper_downloading), Toast.LENGTH_SHORT).show();
                    } else if (mWhisperModelDownloadedFile != null && mWhisperModelDownloadedFile.exists() && mWhisperModelDownloadedFile.length() > 0) {
                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putString(pathKey, mWhisperModelDownloadedFile.getAbsolutePath())
                                .apply();
                        Toast.makeText(context, getString(R.string.subtitle_asr_whisper_downloaded_ready), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, getString(R.string.subtitle_asr_whisper_download_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.subtitle_asr_whisper_download_cancel), null)
                .show();
    }

    private boolean prepareWhisperModelDownload(String url) {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        try {
            url = normalizeUrl(url);
            // Guard: only allow http/https URLs, reject local paths
            if (TextUtils.isEmpty(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                return false;
            }
            File modelDir = new File(context.getFilesDir(), "asr/models");
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                return false;
            }
            String ext = ".bin";
            try {
                String path = Uri.parse(url).getPath();
                if (!TextUtils.isEmpty(path)) {
                    int dot = path.lastIndexOf('.');
                    if (dot > 0 && dot < path.length() - 1) {
                        String e = path.substring(dot);
                        if (e.length() <= 8) {
                            ext = e;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            String name = "whisper_" + sha1(url) + ext;
            mWhisperModelDownloadedFile = new File(modelDir, name);
            mWhisperModelDownloadUrl = url;
            if (mWhisperModelDownloadedFile.exists() && mWhisperModelDownloadedFile.length() > 0) {
                String pathKey = getString(R.string.pref_key_asr_whisper_model_path);
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(pathKey, mWhisperModelDownloadedFile.getAbsolutePath())
                        .apply();
                return false;
            }
            if (mWhisperDownloader != null) {
                try {
                    mWhisperDownloader.cancel();
                } catch (Throwable ignored) {
                }
            }
            HttpFileDownloader downloader = new HttpFileDownloader();
            mWhisperDownloader = downloader;
            setWhisperDownloadSummary(getString(R.string.subtitle_asr_whisper_downloading));
            final String finalUrl = url;
            Thread t = new Thread(() -> downloader.download(finalUrl, mWhisperModelDownloadedFile, new HttpFileDownloader.Listener() {
                @Override
                public void onProgress(long downloadedBytes, long totalBytes) {
                    mHandler.post(() -> {
                        int percent = totalBytes > 0 ? (int) (downloadedBytes * 100 / totalBytes) : 0;
                        String sofarText = formatBytes(downloadedBytes);
                        String totalText = totalBytes > 0 ? formatBytes(totalBytes) : "?";
                        String s = getString(R.string.subtitle_asr_whisper_downloading_progress, percent, sofarText, totalText);
                        setWhisperDownloadSummary(s);
                    });
                }

                @Override
                public void onSuccess(File file) {
                    mHandler.post(() -> {
                        String pathKey = getString(R.string.pref_key_asr_whisper_model_path);
                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putString(pathKey, file.getAbsolutePath())
                                .apply();
                        Toast.makeText(context, getString(R.string.subtitle_asr_whisper_downloaded_ready), Toast.LENGTH_SHORT).show();
                        setWhisperDownloadSummary(getString(R.string.subtitle_asr_whisper_downloaded_ready));
                    });
                }

                @Override
                public void onError(Throwable t) {
                    // Delete incomplete download file to allow clean retry
                    if (mWhisperModelDownloadedFile != null && mWhisperModelDownloadedFile.exists()) {
                        mWhisperModelDownloadedFile.delete();
                    }
                    mHandler.post(() -> {
                        Toast.makeText(context, getString(R.string.subtitle_asr_whisper_download_failed), Toast.LENGTH_SHORT).show();
                        setWhisperDownloadSummary(getString(R.string.subtitle_asr_whisper_download_failed));
                    });
                }
            }), "whisper-model-download");
            mWhisperDownloadThread = t;
            t.start();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setWhisperDownloadSummary(String summary) {
        try {
            String presetKey = getString(R.string.pref_key_asr_whisper_model_preset);
            androidx.preference.Preference preset = findPreference(presetKey);
            if (preset != null && !TextUtils.isEmpty(summary)) {
                preset.setSummary(escapePercentForListPreference(summary));
            }
            String urlKey = getString(R.string.pref_key_asr_whisper_model_url);
            androidx.preference.Preference url = findPreference(urlKey);
            if (url != null && !TextUtils.isEmpty(summary)) {
                url.setSummary(summary);
            }
        } catch (Throwable ignored) {
        }
    }

    private String escapePercentForListPreference(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("%", "%%");
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String out = url.trim();
        while (out.startsWith("`")) {
            out = out.substring(1).trim();
        }
        while (out.endsWith("`") || out.endsWith(",")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        return out;
    }

    private String sha1(String text) {
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

    private String formatBytes(long bytes) {
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
