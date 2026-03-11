package tv.danmaku.ijk.media.example.util;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DebugEventLog {
    private static final int MAX_LINES = 200;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private DebugEventLog() {
    }

    public static void add(String message) {
        if (message == null)
            return;
        String line = TIME.format(new Date()) + " " + message;
        synchronized (LOCK) {
            LINES.addLast(line);
            while (LINES.size() > MAX_LINES) {
                LINES.removeFirst();
            }
        }
    }

    public static List<String> tail(int count) {
        if (count <= 0)
            return new ArrayList<>();
        ArrayList<String> out = new ArrayList<>(count);
        synchronized (LOCK) {
            int start = Math.max(0, LINES.size() - count);
            int idx = 0;
            for (String line : LINES) {
                if (idx++ >= start) {
                    out.add(line);
                }
            }
        }
        return out;
    }
}

