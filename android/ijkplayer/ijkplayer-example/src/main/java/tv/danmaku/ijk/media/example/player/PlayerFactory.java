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

    public IMediaPlayer configure(IMediaPlayer mediaPlayer, Settings settings, Uri uri, String manifestString, boolean enableVulkan, boolean deviceSupportsVulkan) {
        if (!(mediaPlayer instanceof IjkMediaPlayer) || settings == null) {
            return mediaPlayer;
        }

        IjkMediaPlayer ijk = (IjkMediaPlayer) mediaPlayer;

        if (!TextUtils.isEmpty(manifestString)) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "iformat", "ijklas");
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "find_stream_info", 0);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "manifest_string", manifestString);
        }

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
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);

        if (enableVulkan && deviceSupportsVulkan && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vf0", "scale_vulkan=iw:ih");
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

