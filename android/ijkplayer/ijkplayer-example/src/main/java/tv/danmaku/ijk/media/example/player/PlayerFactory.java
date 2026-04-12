package tv.danmaku.ijk.media.example.player;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import java.util.Locale;

import tv.danmaku.ijk.media.exo.IjkExoMediaPlayer;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.TextureMediaPlayer;
import tv.danmaku.ijk.media.example.util.DebugEventLog;

public final class PlayerFactory {
    private final Context mAppContext;

    public PlayerFactory(Context appContext) {
        mAppContext = appContext.getApplicationContext();
    }

    public IMediaPlayer create(int playerType) {
        if (playerType == Settings.PV_PLAYER__IjkExoMediaPlayer) {
            return new IjkExoMediaPlayer(mAppContext);
        } else if (playerType == Settings.PV_PLAYER__AndroidMediaPlayer) {
            return new AndroidMediaPlayer();
        } else {
            IjkMediaPlayer ijk = new IjkMediaPlayer();
            ijk.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG);
            return ijk;
        }
    }

    public IMediaPlayer configure(IMediaPlayer mediaPlayer, Settings settings, Uri uri, String manifestString, boolean enableVulkan, boolean deviceSupportsVulkan, String vf0Override) {
        if (!(mediaPlayer instanceof IjkMediaPlayer) || settings == null) {
            return mediaPlayer;
        }

        IjkMediaPlayer ijk = (IjkMediaPlayer) mediaPlayer;
        DebugEventLog.add("PlayerFactory.configure: enableVulkan=" + enableVulkan + ", deviceVulkan=" + deviceSupportsVulkan + ", sdk=" + Build.VERSION.SDK_INT + ", vf0Override=" + (!TextUtils.isEmpty(vf0Override)));

        if (!TextUtils.isEmpty(manifestString)) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "iformat", "ijklas");
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "find_stream_info", 0);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "manifest_string", manifestString);
        }

        boolean hasVf0Override = !TextUtils.isEmpty(vf0Override);
        if (hasVf0Override && settings.getUsingMediaCodec()) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
            DebugEventLog.add("PlayerFactory.configure: disable mediacodec due to vf0Override");
        } else
        if (settings.getUsingMediaCodec()) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", settings.getUsingMediaCodecAutoRotate() ? 1 : 0);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", settings.getMediaCodecHandleResolutionChange() ? 1 : 0);
        } else {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
        }

        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", settings.getUsingOpenSLES() ? 1 : 0);

        String pixelFormat = settings.getPixelFormat();
        if (TextUtils.isEmpty(pixelFormat)) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
        } else {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", pixelFormat);
        }

        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
        // Use skip_loop_filter=0 by default for best quality;
        // callers may pass settings.getSkipLoopFilter() to allow per-device tuning.
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", settings.getSkipLoopFilter());

        String vf0ToApply = null;
        if (hasVf0Override) {
            vf0ToApply = vf0Override;
            if (vf0ToApply.contains("vulkan")) {
                if (!deviceSupportsVulkan || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    vf0ToApply = mapVulkanVf0ToSoftware(vf0ToApply);
                    DebugEventLog.add("PlayerFactory.configure: vulkan not available -> fallback vf0=" + vf0ToApply);
                } else {
                    if (!vf0ToApply.contains("hwupload")) {
                        vf0ToApply = "hwupload," + vf0ToApply;
                    }
                    if (!vf0ToApply.contains("hwdownload")) {
                        vf0ToApply = vf0ToApply + ",hwdownload,format=yuv420p";
                    }
                }
            }
        } else if (enableVulkan && deviceSupportsVulkan && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Prefer Vulkan GPU passthrough scale; falls back to software if filters not compiled
            vf0ToApply = "hwupload,scale_vulkan=w=iw:h=ih,hwdownload,format=yuv420p";
        }

        if (!TextUtils.isEmpty(vf0ToApply)) {
            DebugEventLog.add("PlayerFactory.configure: apply vf0=" + vf0ToApply);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vf0", vf0ToApply);
        }

        if (uri != null) {
            String scheme = uri.getScheme();
            if (scheme != null) {
                String lower = scheme.toLowerCase(Locale.US);
                if (lower.equals("http") || lower.equals("https")) {
                    ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
                }
            }
        }

        return ijk;
    }

    private String mapVulkanVf0ToSoftware(String vf0) {
        String out = vf0;
        out = out.replace("scale_vulkan", "scale");
        out = out.replace("hflip_vulkan", "hflip");
        out = out.replace("vflip_vulkan", "vflip");
        out = out.replace("transpose_vulkan", "transpose");
        out = out.replace("gblur_vulkan", "gblur");
        out = out.replace("avgblur_vulkan", "avgblur");
        // chromaber_vulkan / blend_vulkan / overlay_vulkan have no direct CPU equivalent;
        // remove them gracefully so the filter graph still parses.
        out = out.replace("chromaber_vulkan", "null");
        out = out.replace("blend_vulkan", "null");
        out = out.replace("overlay_vulkan", "null");
        out = out.replace("hwupload,", "");
        out = out.replace(",hwdownload,format=yuv420p", "");
        out = out.replace(",hwdownload", "");
        out = out.replace("hwdownload,", "");
        return out;
    }

    /**
     * Build a software-only vf0 string for brightness/contrast/saturation.
     * Uses FFmpeg "eq" filter: brightness [-1,1], contrast [0,2], saturation [0,3].
     * All parameters are optional; pass Float.NaN to omit.
     */
    public static String buildEqVf0(float brightness, float contrast, float saturation) {
        StringBuilder sb = new StringBuilder("eq");
        boolean any = false;
        if (!Float.isNaN(brightness)) {
            sb.append(any ? ":" : "=").append("brightness=").append(brightness);
            any = true;
        }
        if (!Float.isNaN(contrast)) {
            sb.append(any ? ":" : "=").append("contrast=").append(contrast);
            any = true;
        }
        if (!Float.isNaN(saturation)) {
            sb.append(any ? ":" : "=").append("saturation=").append(saturation);
        }
        return sb.toString();
    }

    public IMediaPlayer wrapIfNeeded(IMediaPlayer mediaPlayer, Settings settings) {
        if (mediaPlayer == null || settings == null) {
            return mediaPlayer;
        }
        if (settings.getEnableDetachedSurfaceTextureView()) {
            return new TextureMediaPlayer(mediaPlayer);
        }
        return mediaPlayer;
    }
}
