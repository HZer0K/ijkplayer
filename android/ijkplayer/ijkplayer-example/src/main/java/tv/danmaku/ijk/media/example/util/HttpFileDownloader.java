package tv.danmaku.ijk.media.example.util;

import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class HttpFileDownloader {
    public interface Listener {
        void onProgress(long downloadedBytes, long totalBytes);

        void onSuccess(File file);

        void onError(Throwable t);
    }

    private volatile boolean mCanceled;

    public void cancel() {
        mCanceled = true;
    }

    public void download(String url, File targetFile, Listener listener) {
        if (listener == null) {
            return;
        }
        if (TextUtils.isEmpty(url) || targetFile == null) {
            listener.onError(new IllegalArgumentException("invalid args"));
            return;
        }
        File tmp = new File(targetFile.getParentFile(), targetFile.getName() + ".download");
        HttpURLConnection conn = null;
        try {
            if (targetFile.exists() && targetFile.length() > 0) {
                listener.onSuccess(targetFile);
                return;
            }
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                listener.onError(new RuntimeException("mkdirs failed"));
                return;
            }
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                listener.onError(new RuntimeException("http " + code));
                return;
            }
            long total = -1;
            try {
                total = conn.getContentLengthLong();
            } catch (Throwable ignored) {
            }
            try (InputStream is = conn.getInputStream(); FileOutputStream os = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                long sofar = 0;
                long lastReport = 0;
                for (;;) {
                    if (mCanceled) {
                        listener.onError(new RuntimeException("canceled"));
                        return;
                    }
                    int n = is.read(buf);
                    if (n < 0) {
                        break;
                    }
                    os.write(buf, 0, n);
                    sofar += n;
                    long now = System.currentTimeMillis();
                    if (now - lastReport >= 500) {
                        lastReport = now;
                        listener.onProgress(sofar, total);
                    }
                }
                os.flush();
                listener.onProgress(sofar, total);
            }
            if (targetFile.exists() && !targetFile.delete()) {
                listener.onError(new RuntimeException("delete existing failed"));
                return;
            }
            if (!tmp.renameTo(targetFile)) {
                listener.onError(new RuntimeException("rename failed"));
                return;
            }
            listener.onSuccess(targetFile);
        } catch (Throwable t) {
            listener.onError(t);
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (tmp.exists() && (!targetFile.exists() || targetFile.length() <= 0)) {
                    tmp.delete();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

