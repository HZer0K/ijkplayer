package tv.danmaku.ijk.media.example.player;

import android.net.Uri;

import tv.danmaku.ijk.media.example.application.Settings;

public interface PlaybackPolicy {
    int resolvePlayerType(int requestedPlayerType, Uri uri, Settings settings, boolean forceExoOnce);
}

