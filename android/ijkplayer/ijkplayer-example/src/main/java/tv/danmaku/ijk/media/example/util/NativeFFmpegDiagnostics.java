package tv.danmaku.ijk.media.example.util;

import tv.danmaku.ijk.media.player.ffmpeg.FFmpegApi;

public final class NativeFFmpegDiagnostics {
    private static volatile Boolean sAvailable;

    private NativeFFmpegDiagnostics() {
    }

    public static boolean isAvailable() {
        Boolean cached = sAvailable;
        if (cached != null) {
            return cached;
        }
        boolean ok;
        try {
            FFmpegApi.isDiagnosticsEnabled();
            ok = true;
        } catch (Throwable ignored) {
            ok = false;
        }
        sAvailable = ok;
        return ok;
    }

    public static String getCapabilitiesJsonOrNull() {
        try {
            return FFmpegApi.getCapabilitiesJson();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean setDiagnosticsEnabledSafe(boolean enabled) {
        try {
            FFmpegApi.setDiagnosticsEnabled(enabled);
            return true;
        } catch (Throwable ignored) {
            sAvailable = false;
            return false;
        }
    }

    public static boolean isDiagnosticsEnabledSafe() {
        try {
            return FFmpegApi.isDiagnosticsEnabled();
        } catch (Throwable ignored) {
            sAvailable = false;
            return false;
        }
    }
}
