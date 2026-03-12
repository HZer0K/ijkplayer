package tv.danmaku.ijk.media.example.player;

import android.net.Uri;
import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaSourceUtil {
    private static final Pattern LIKELY_MEDIA_URL_HINT = Pattern.compile("(?i)video|play|stream.*(m3u8|mp4)");

    private MediaSourceUtil() {
    }

    public static String getCurrentSource(String videoPath, Uri videoUri) {
        if (!TextUtils.isEmpty(videoPath)) {
            return videoPath;
        }
        return videoUri != null ? videoUri.toString() : null;
    }

    public static boolean isManifestStringSource(String source) {
        return source != null && source.contains("adaptationSet");
    }

    public static boolean isLikelyMediaUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.startsWith("rtmp://") || lower.startsWith("rtsp://")) {
            return true;
        }
        if (lower.endsWith(".m3u8") || lower.contains(".m3u8?")) {
            return true;
        }
        if (lower.endsWith(".mp4") || lower.contains(".mp4?")) {
            return true;
        }
        if (lower.endsWith(".flv") || lower.contains(".flv?")) {
            return true;
        }
        if (lower.endsWith(".mov") || lower.contains(".mov?")) {
            return true;
        }
        Matcher m = LIKELY_MEDIA_URL_HINT.matcher(lower);
        return m.find();
    }
}

