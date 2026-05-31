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
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.media.IRenderView;
import tv.danmaku.ijk.media.example.widget.media.MeasureHelper;

/**
 * Builds diagnostic summary and log strings for the diagnostics bottom sheet.
 * All methods are static — no instance state needed.
 */
public final class DiagnosticsHelper {
    private static final String TAG = "DiagnosticsHelper";

    private DiagnosticsHelper() {
    }

    /**
     * Build a multi-line summary of current player/diagnostics state.
     */
    public static String buildSummary(Context context, IjkVideoView videoView,
                                       @Nullable String videoPath, @Nullable Uri videoUri,
                                       Settings settings) {
        StringBuilder sb = new StringBuilder();
        if (context == null) {
            return "";
        }
        String source = videoView != null ? videoView.getDataSource() : null;
        if (TextUtils.isEmpty(source)) {
            source = videoPath != null ? videoPath : (videoUri != null ? String.valueOf(videoUri) : "");
        }

        sb.append(context.getString(
                tv.danmaku.ijk.media.example.R.string.diagnostics_title)).append('\n');
        sb.append("source=").append(source).append('\n');
        sb.append("pref.player=").append(settings != null ? settings.getPlayer() : -1).append('\n');
        sb.append("orientation=").append(getOrientationText(context,
                settings != null ? settings.getPlayerOrientation() : Settings.ORIENTATION__Auto)).append('\n');
        sb.append("render=").append(videoView != null ?
                IjkVideoView.getRenderText(context, videoView.getRender()) : "").append('\n');
        sb.append("ratio=").append(MeasureHelper.getAspectRatioText(context,
                videoView != null ? videoView.getCurrentAspectRatio() : IRenderView.AR_ASPECT_FIT_PARENT)).append('\n');
        sb.append("mirror=").append(settings != null && settings.getVideoMirrorHorizontal()).append('\n');
        sb.append("deviceVulkan=").append(videoView != null && videoView.isDeviceSupportsVulkan()).append('\n');

        String vf0 = videoView != null ? videoView.getVideoFilterVf0() : null;
        sb.append("vf0=").append(TextUtils.isEmpty(vf0) ? "null" : ("len=" + vf0.length())).append('\n');

        if (videoView != null) {
            int fw = videoView.getLastErrorFramework();
            int impl = videoView.getLastErrorImpl();
            if (fw != 0 || impl != 0) {
                sb.append("lastError=").append(fw).append(',').append(impl)
                        .append(" timeMs=").append(videoView.getLastErrorTimeMs()).append('\n');
            }
        }

        String lastCreate = findLastLogLineContains(DebugEventLog.tail(200), "createPlayer:");
        if (!TextUtils.isEmpty(lastCreate)) {
            sb.append(lastCreate).append('\n');
        }
        String lastApplyVf0 = findLastLogLineContains(DebugEventLog.tail(200), "PlayerFactory.configure: apply vf0=");
        if (!TextUtils.isEmpty(lastApplyVf0)) {
            sb.append(lastApplyVf0).append('\n');
        }
        appendApkNativeLibInfo(context, sb);
        appendNativeCapabilities(sb);
        return sb.toString();
    }

    /**
     * Build log tail string.
     */
    public static String buildLogs() {
        StringBuilder sb = new StringBuilder();
        for (String line : DebugEventLog.tail(200)) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ================ Private helpers ================

    private static String getOrientationText(Context context, int orientation) {
        if (orientation == Settings.ORIENTATION__Landscape) {
            return context.getString(tv.danmaku.ijk.media.example.R.string.orientation_landscape);
        }
        if (orientation == Settings.ORIENTATION__Portrait) {
            return context.getString(tv.danmaku.ijk.media.example.R.string.orientation_portrait);
        }
        return context.getString(tv.danmaku.ijk.media.example.R.string.orientation_auto);
    }

    private static String findLastLogLineContains(List<String> lines, String keyword) {
        if (TextUtils.isEmpty(keyword) || lines == null) {
            return null;
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line != null && line.contains(keyword)) {
                return line;
            }
        }
        return null;
    }

    private static void appendApkNativeLibInfo(Context context, StringBuilder sb) {
        try {
            String dir = context.getApplicationInfo() != null
                    ? context.getApplicationInfo().nativeLibraryDir : null;
            sb.append("apk.nativeLibDir=").append(TextUtils.isEmpty(dir) ? "null" : dir).append('\n');

            File nativeDir = !TextUtils.isEmpty(dir) ? new File(dir) : null;
            appendNativeLibFileInfo(sb, nativeDir, "libijkffmpeg.so");
            appendNativeLibFileInfo(sb, nativeDir, "libijkplayer.so");
        } catch (Throwable ignored) {
            Log.w(TAG, "appendApkNativeLibInfo failed", ignored);
        }
    }

    private static void appendNativeLibFileInfo(StringBuilder sb, File nativeDir, String name) {
        try {
            if (nativeDir == null || TextUtils.isEmpty(name)) {
                return;
            }
            File f = new File(nativeDir, name);
            if (!f.exists()) {
                sb.append("apk.").append(name).append("=missing").append('\n');
                return;
            }
            sb.append("apk.").append(name).append(".size=").append(f.length()).append('\n');
            String sha1 = sha1Hex(f);
            if (!TextUtils.isEmpty(sha1)) {
                sb.append("apk.").append(name).append(".sha1=").append(sha1).append('\n');
            }
        } catch (Throwable ignored) {
            Log.w(TAG, "appendNativeLibFileInfo failed for " + name, ignored);
        }
    }

    private static String sha1Hex(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            byte[] b = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte v : b) {
                sb.append(String.format(Locale.US, "%02x", v));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void appendNativeCapabilities(StringBuilder sb) {
        try {
            String json = NativeFFmpegDiagnostics.getCapabilitiesJsonOrNull();
            if (TextUtils.isEmpty(json)) {
                return;
            }
            JSONObject obj = new JSONObject(json);

            String cfg = obj.optString("avformat_configuration", "");
            boolean openssl = cfg.contains("enable-openssl");
            boolean gnutls = cfg.contains("enable-gnutls");

            JSONArray in = obj.optJSONArray("protocols_in");
            boolean http = containsString(in, "http");
            boolean https = containsString(in, "https");
            boolean tls = containsString(in, "tls");

            JSONObject filters = obj.optJSONObject("filter_presence");
            boolean drawbox = filters != null && filters.optBoolean("drawbox", false);
            boolean scaleVulkan = filters != null && filters.optBoolean("scale_vulkan", false);
            boolean hflipVulkan = filters != null && filters.optBoolean("hflip_vulkan", false);
            boolean vflipVulkan = filters != null && filters.optBoolean("vflip_vulkan", false);
            boolean transposeVulkan = filters != null && filters.optBoolean("transpose_vulkan", false);

            sb.append("ffmpeg.openssl=").append(openssl).append(" gnutls=").append(gnutls).append('\n');
            sb.append("ffmpeg.protocols.http=").append(http)
                    .append(" https=").append(https).append(" tls=").append(tls).append('\n');
            sb.append("ffmpeg.filters.drawbox=").append(drawbox).append('\n');
            sb.append("ffmpeg.filters.vulkan=")
                    .append(scaleVulkan || hflipVulkan || vflipVulkan || transposeVulkan).append('\n');
            if (!TextUtils.isEmpty(cfg)) {
                sb.append("ffmpeg.avformat_configuration=").append(cfg).append('\n');
            }
            sb.append("ffmpeg.nativeDiag=")
                    .append(NativeFFmpegDiagnostics.isDiagnosticsEnabledSafe()).append('\n');
        } catch (Throwable ignored) {
            Log.w(TAG, "appendNativeCapabilities failed", ignored);
        }
    }

    private static boolean containsString(JSONArray arr, String value) {
        if (arr == null || TextUtils.isEmpty(value)) {
            return false;
        }
        for (int i = 0; i < arr.length(); i++) {
            if (value.equalsIgnoreCase(arr.optString(i))) {
                return true;
            }
        }
        return false;
    }
}
