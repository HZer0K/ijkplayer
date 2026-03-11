package tv.danmaku.ijk.media.example.player;

import android.net.Uri;

import java.util.Locale;

import tv.danmaku.ijk.media.example.application.Settings;

public final class DefaultPlaybackPolicy implements PlaybackPolicy {
    @Override
    public int resolvePlayerType(int requestedPlayerType, Uri uri, Settings settings, boolean forceExoOnce) {
        if (forceExoOnce) {
            return Settings.PV_PLAYER__IjkExoMediaPlayer;
        }

        if (uri != null && settings != null && settings.getPreferExoForHttp()) {
            String scheme = uri.getScheme();
            if (scheme != null) {
                String lower = scheme.toLowerCase(Locale.US);
                if (lower.equals("http") || lower.equals("https")) {
                    return Settings.PV_PLAYER__IjkExoMediaPlayer;
                }
            }
        }

        if (requestedPlayerType == Settings.PV_PLAYER__Auto) {
            return Settings.PV_PLAYER__IjkMediaPlayer;
        }

        return requestedPlayerType;
    }
}

