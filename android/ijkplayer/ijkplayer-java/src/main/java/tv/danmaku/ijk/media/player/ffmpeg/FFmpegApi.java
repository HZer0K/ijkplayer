package tv.danmaku.ijk.media.player.ffmpeg;

public class FFmpegApi {
    public static native String av_base64_encode(byte in[]);

    public static native String getCapabilitiesJson();

    public static native void setDiagnosticsEnabled(boolean enabled);

    public static native boolean isDiagnosticsEnabled();
}
