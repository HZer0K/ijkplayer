package tv.danmaku.ijk.media.example.util;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RemoteAsrClient {
    public static final class Result {
        public final boolean partial;
        public final List<Segment> segments;

        public Result(boolean partial, List<Segment> segments) {
            this.partial = partial;
            this.segments = segments;
        }
    }

    public static final class Segment {
        public final int startMs;
        public final int endMs;
        public final String text;

        public Segment(int startMs, int endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    public Result transcribePcm16Mono16k(String endpointUrl, byte[] pcmData, int startMs, int endMs, String language) throws Exception {
        return transcribePcm(endpointUrl, pcmData, startMs, endMs, 16000, 1, "pcm_s16le", language);
    }

    public Result transcribePcm(String endpointUrl, byte[] pcmData, int startMs, int endMs, int sampleRate, int channelCount, String audioFormat, String language) throws Exception {
        if (TextUtils.isEmpty(endpointUrl) || pcmData == null || pcmData.length <= 0) {
            return new Result(false, new ArrayList<>());
        }
        URL url = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(60_000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("X-Audio-Format", TextUtils.isEmpty(audioFormat) ? "pcm_s16le" : audioFormat);
        conn.setRequestProperty("X-Sample-Rate", String.valueOf(Math.max(1, sampleRate)));
        conn.setRequestProperty("X-Channel-Count", String.valueOf(Math.max(1, channelCount)));
        conn.setRequestProperty("X-Start-Ms", String.valueOf(startMs));
        conn.setRequestProperty("X-End-Ms", String.valueOf(endMs));
        if (!TextUtils.isEmpty(language)) {
            conn.setRequestProperty("X-Lang", language);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(pcmData);
            os.flush();
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAllUtf8(is);
        if (TextUtils.isEmpty(body)) {
            return new Result(false, new ArrayList<>());
        }
        body = body.trim();
        if (body.startsWith("{")) {
            return parseJson(body, startMs, endMs);
        }
        ArrayList<Segment> out = new ArrayList<>();
        out.add(new Segment(startMs, endMs, body));
        return new Result(false, out);
    }

    private Result parseJson(String json, int startMs, int endMs) throws Exception {
        JSONObject obj = new JSONObject(json);
        boolean partial = obj.optBoolean("partial", false);
        JSONArray segments = obj.optJSONArray("segments");
        ArrayList<Segment> out = new ArrayList<>();
        if (segments != null && segments.length() > 0) {
            for (int i = 0; i < segments.length(); i++) {
                JSONObject s = segments.optJSONObject(i);
                if (s == null) {
                    continue;
                }
                int s0 = s.optInt("startMs", startMs);
                int s1 = s.optInt("endMs", endMs);
                String t = s.optString("text", "");
                if (!TextUtils.isEmpty(t)) {
                    out.add(new Segment(s0, s1, t));
                }
            }
            return new Result(partial, out);
        }
        String text = obj.optString("text", "");
        if (!TextUtils.isEmpty(text)) {
            out.add(new Segment(startMs, endMs, text));
        }
        return new Result(partial, out);
    }

    private String readAllUtf8(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
