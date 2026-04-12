package tv.danmaku.ijk.media.example.util;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;

import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public final class AsrAudioTrackDecoder {
    public interface Listener {
        void onPcmChunk(byte[] pcmData, int startMs, int endMs, int sampleRate, int channelCount, String audioFormat);

        void onStopped();

        void onError(Throwable t);
    }

    private Thread mThread;
    private volatile boolean mRunning;

    public boolean isRunning() {
        return mRunning;
    }

    public void start(android.content.Context context, String dataSource, int startPlayerMs, Listener listener) {
        stop();
        if (listener == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            listener.onError(new RuntimeException("MediaCodec unsupported"));
            return;
        }
        if (TextUtils.isEmpty(dataSource)) {
            listener.onError(new IllegalArgumentException("empty dataSource"));
            return;
        }
        mRunning = true;
        mThread = new Thread(() -> runDecode(context, dataSource, startPlayerMs, listener), "asr-track");
        mThread.start();
    }

    public void stop() {
        mRunning = false;
        Thread t = mThread;
        mThread = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
    }

    private void runDecode(android.content.Context context, String dataSource, int startPlayerMs, Listener listener) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        boolean codecStarted = false;
        try {
            extractor = new MediaExtractor();
            if (dataSource.startsWith("content://")) {
                if (context == null) {
                    throw new IllegalArgumentException("content uri without context");
                }
                extractor.setDataSource(context, android.net.Uri.parse(dataSource), null);
            } else {
                extractor.setDataSource(dataSource);
            }
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("audio track not found");
            }
            extractor.selectTrack(trackIndex);
            extractor.seekTo(Math.max(0, (long) startPlayerMs) * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            long baseAudioUs = Math.max(0, extractor.getSampleTime());
            int basePlayerMs = Math.max(0, startPlayerMs);

            MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (TextUtils.isEmpty(mime)) {
                throw new RuntimeException("empty mime");
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            codecStarted = true;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 16000;
            int channelCount = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

            while (mRunning && !outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                        if (inBuf != null) {
                            inBuf.clear();
                            int size = extractor.readSampleData(inBuf, 0);
                            long pts = extractor.getSampleTime();
                            if (size < 0 || pts < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, pts, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFmt = codec.getOutputFormat();
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                } else if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset);
                        outBuf.limit(info.offset + info.size);
                        byte[] pcm = new byte[info.size];
                        outBuf.get(pcm);

                        String fmt = "pcm_s16le";
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            pcm = floatToPcm16Le(pcm);
                            fmt = "pcm_s16le";
                        }
                        int startMs = basePlayerMs + (int) ((info.presentationTimeUs - baseAudioUs) / 1000L);
                        int endMs = Math.max(startMs + 1, startMs + pcmDurationMs(pcm.length, sampleRate, channelCount));
                        listener.onPcmChunk(pcm, Math.max(0, startMs), Math.max(0, endMs), sampleRate, channelCount, fmt);
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }
            listener.onStopped();
        } catch (Throwable t) {
            listener.onError(t);
        } finally {
            try {
                // Only call stop() if codec was successfully started; avoids double-stop crash
                if (codec != null && codecStarted) {
                    codec.stop();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (codec != null) {
                    codec.release();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (extractor != null) {
                    extractor.release();
                }
            } catch (Throwable ignored) {
            }
            mRunning = false;
        }
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!TextUtils.isEmpty(mime) && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private int pcmDurationMs(int byteCount, int sampleRate, int channels) {
        int ch = Math.max(1, channels);
        int sr = Math.max(1, sampleRate);
        int bytesPerFrame = 2 * ch;
        int frames = byteCount / bytesPerFrame;
        return (int) ((long) frames * 1000L / sr);
    }

    private byte[] floatToPcm16Le(byte[] f32le) {
        int samples = f32le.length / 4;
        byte[] out = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            int base = i * 4;
            int bits = (f32le[base] & 0xff) | ((f32le[base + 1] & 0xff) << 8) | ((f32le[base + 2] & 0xff) << 16) | ((f32le[base + 3] & 0xff) << 24);
            float v = Float.intBitsToFloat(bits);
            v = Math.max(-1f, Math.min(1f, v));
            short s = (short) Math.round(v * 32767f);
            out[i * 2] = (byte) (s & 0xff);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return out;
    }
}
