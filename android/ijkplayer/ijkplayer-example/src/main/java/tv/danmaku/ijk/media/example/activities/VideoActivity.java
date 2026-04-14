/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.example.activities;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.app.DownloadManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaScannerConnection;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.Toast;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.example.BuildConfig;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.content.RecentMediaStorage;
import tv.danmaku.ijk.media.example.fragments.DiagnosticsBottomSheetDialogFragment;
import tv.danmaku.ijk.media.example.fragments.TracksFragment;
import tv.danmaku.ijk.media.example.player.MediaSourceUtil;
import tv.danmaku.ijk.media.example.player.PlayerFactory;
import tv.danmaku.ijk.media.example.player.PlayerToggle;
import tv.danmaku.ijk.media.example.util.DebugEventLog;
import tv.danmaku.ijk.media.example.util.AsrAudioTrackDecoder;
import tv.danmaku.ijk.media.example.util.NativeFFmpegDiagnostics;
import tv.danmaku.ijk.media.example.util.RemoteAsrClient;
import tv.danmaku.ijk.media.example.util.WhisperAsrEngine;
import tv.danmaku.ijk.media.example.widget.media.AndroidMediaController;

import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.media.IRenderView;
import tv.danmaku.ijk.media.example.widget.media.MeasureHelper;

public class VideoActivity extends AppCompatActivity implements TracksFragment.ITrackHolder {
    private static final String TAG = "VideoActivity";
    private static final String EXTRA_TEST_HINT = "testHint";
    private static final String EXTRA_VF0 = "vf0";

    private String mVideoPath;
    private Uri    mVideoUri;

    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private TextView mToastTextView;
    private TableLayout mHudView;

    private Settings mSettings;
    private boolean mBackPressed;
    private boolean mEdgeBackActive;
    private boolean mEdgeBackFromLeft;
    private float mEdgeBackDownX;
    private float mEdgeBackDownY;
    private float mEdgeBackEdgeSizePx;
    private float mEdgeBackTriggerPx;
    private int mEdgeBackTouchSlop;

    private long mVulkanDownloadId = -1L;
    private File mVulkanDownloadedFile;
    private BroadcastReceiver mVulkanDownloadReceiver;

    private long mAsrTrackDownloadId = -1L;
    private File mAsrTrackDownloadedFile;
    private BroadcastReceiver mAsrTrackDownloadReceiver;
    private String mAsrTrackDownloadUrl;

    private File mWhisperModelDownloadedFile;
    private String mWhisperModelDownloadUrl;
    private tv.danmaku.ijk.media.example.util.HttpFileDownloader mWhisperModelDownloader;
    private Thread mWhisperModelDownloadThread;

    private TextView mSubtitleOverlay;
    private final java.util.ArrayList<SubtitleCue> mSubtitleCues = new java.util.ArrayList<>();
    private final Handler mSubtitleHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSubtitleTick = new Runnable() {
        @Override
        public void run() {
            updateSubtitleOverlay();
            mSubtitleHandler.postDelayed(this, 250);
        }
    };

    // --- Screen keep-on ---
    private boolean mScreenKeepOn = false;

    // --- Loop playback ---
    private boolean mLoopEnabled = false;

    // --- Video filter ---
    /** Currently selected render-layer filter type (see {@link IjkVideoView}.RENDER_FILTER_*) */
    private int mCurrentFilterType = IjkVideoView.RENDER_FILTER_NONE;
    /** Current FFmpeg vf0 filter string; null = no FFmpeg filter active */
    private String mCurrentVf0Filter = null;

    // --- Gesture: brightness / volume ---
    /** true while a brightness/volume vertical gesture is in progress */
    private boolean mGestureActive = false;
    /** true = brightness side (left half), false = volume side (right half) */
    private boolean mGestureBrightnessSide = false;
    private float mGestureDownX;
    private float mGestureDownY;
    /** Brightness value [0,1] at the start of a gesture */
    private float mGestureStartBrightness;
    /** Volume level at the start of a gesture */
    private int mGestureStartVolume;
    private int mGestureMaxVolume;

    // --- Gesture: horizontal seek ---
    /** true while a horizontal seek gesture is in progress */
    private boolean mSeekGestureActive = false;
    /** Player position in ms at the start of a seek gesture */
    private long mSeekGestureStartMs = 0;
    /** Seek target in ms (updated live during gesture, applied on ACTION_UP) */
    private long mSeekGestureTargetMs = 0;

    // --- Gesture: double-tap to toggle pause ---
    private long mLastTapTime = 0;
    private float mLastTapX = -1;
    private float mLastTapY = -1;
    /** Max interval between two taps to be considered a double-tap, ms */
    private static final long DOUBLE_TAP_TIMEOUT_MS = 350;

    // --- Playback position memory (URL -> position ms) ---
    private static final String PREFS_PLAYBACK_POS = "playback_positions";
    private static final int    POSITION_SAVE_THRESHOLD_MS = 5_000;  // don't save if < 5s
    private static final int    POSITION_RESTORE_THRESHOLD_MS = 3_000; // don't restore if < 3s remain
    /** Set to true before a manual rebuild/restart to skip position restore for that cycle. */
    private boolean mSkipNextPositionRestore = false;

    private static final int REQ_RECORD_AUDIO = 2201;
    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechIntent;
    private boolean mAsrEnabled;
    private boolean mAsrPendingStart;
    private String mAsrPartialText;
    private String mAsrLastFinalText;
    private long mAsrLastFinalAtMs;
    private int mAsrServiceCount;
    private AudioRecord mAsrAudioRecord;
    private Thread mAsrRemoteThread;
    private boolean mAsrRemoteRunning;
    private long mAsrRemoteChunkStartPlayerMs = -1;
    private int mAsrRemoteChunkBytes = 0;
    private final java.io.ByteArrayOutputStream mAsrRemoteChunkBuffer = new java.io.ByteArrayOutputStream();
    private final RemoteAsrClient mRemoteAsrClient = new RemoteAsrClient();
    private ExecutorService mAsrRemoteExecutor;
    private boolean mAsrRemoteChunkHasVoice;
    private long mAsrRemoteLastVoiceAtMs = -1;
    private String mAsrRemoteCommittedTail = "";

    private AsrAudioTrackDecoder mAsrTrackDecoder;
    private int mAsrTrackChunkStartMs = -1;
    private int mAsrTrackChunkBytes = 0;
    private boolean mAsrTrackChunkHasVoice;
    private int mAsrTrackLastVoiceMs = -1;
    private int mAsrTrackLastEndMs = -1;
    private int mAsrTrackSampleRate = 16000;
    private int mAsrTrackChannelCount = 1;
    private String mAsrTrackAudioFormat = "pcm_s16le";
    private final java.io.ByteArrayOutputStream mAsrTrackChunkBuffer = new java.io.ByteArrayOutputStream();
    private boolean mAsrTrackUseWhisper;
    private String mAsrWhisperModelPath;
    private long mAsrTrackLastVoiceDebugAtMs;
    private long mAsrTrackLastResultDebugAtMs;

    public static Intent newIntent(Context context, String videoPath, String videoTitle) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoPath", videoPath);
        intent.putExtra("videoTitle", videoTitle);
        return intent;
    }

    public static void intentTo(Context context, String videoPath, String videoTitle) {
        context.startActivity(newIntent(context, videoPath, videoTitle));
    }

    public static void intentToWithHint(Context context, String videoPath, String videoTitle, String hint) {
        Intent intent = newIntent(context, videoPath, videoTitle);
        if (!TextUtils.isEmpty(hint)) {
            intent.putExtra(EXTRA_TEST_HINT, hint);
        }
        context.startActivity(intent);
    }

    public static void intentToWithHintAndVf0(Context context, String videoPath, String videoTitle, String hint, String vf0) {
        Intent intent = newIntent(context, videoPath, videoTitle);
        if (!TextUtils.isEmpty(hint)) {
            intent.putExtra(EXTRA_TEST_HINT, hint);
        }
        if (!TextUtils.isEmpty(vf0)) {
            intent.putExtra(EXTRA_VF0, vf0);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mSettings = new Settings(this);
        applyPlayerOrientation(mSettings.getPlayerOrientation());

        // handle arguments
        mVideoPath = getIntent().getStringExtra("videoPath");
        if (!TextUtils.isEmpty(mVideoPath)) {
            mVideoPath = normalizeUrl(mVideoPath);
        }

        Intent intent = getIntent();
        String intentAction = intent.getAction();
        if (!TextUtils.isEmpty(intentAction)) {
            if (intentAction.equals(Intent.ACTION_VIEW)) {
                mVideoPath = intent.getDataString();
            } else if (intentAction.equals(Intent.ACTION_SEND)) {
                mVideoUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    String scheme = mVideoUri.getScheme();
                    if (TextUtils.isEmpty(scheme)) {
                        Log.e(TAG, "Null unknown scheme\n");
                        finish();
                        return;
                    }
                    if (scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
                        mVideoPath = mVideoUri.getPath();
                    } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                        Log.e(TAG, "Can not resolve content below Android-ICS\n");
                        finish();
                        return;
                    } else {
                        Log.e(TAG, "Unknown scheme " + scheme + "\n");
                        finish();
                        return;
                    }
                }
            }
        }

        if (!TextUtils.isEmpty(mVideoPath)) {
            new RecentMediaStorage(this).saveUrlAsync(mVideoPath);
        }

        // init UI
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mToastTextView = (TextView) findViewById(R.id.toast_text_view);
        mHudView = (TableLayout) findViewById(R.id.hud_view);
        mSubtitleOverlay = (TextView) findViewById(R.id.subtitle_overlay);
        String testHint = getIntent() != null ? getIntent().getStringExtra(EXTRA_TEST_HINT) : null;
        if (!TextUtils.isEmpty(testHint)) {
            mToastTextView.setText(testHint);
            mMediaController.showOnce(mToastTextView);
        }

        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        NativeFFmpegDiagnostics.setDiagnosticsEnabledSafe(mSettings.getNativeDiagnosticsEnabled());
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);
        mVideoView.setHudView(mHudView);
        mVideoView.setMirrorHorizontal(mSettings.getVideoMirrorHorizontal());
        // Initialize AudioManager for gesture-based volume control
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            mGestureMaxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        // Keep screen on when video starts rendering; manage on buffering events
        mVideoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        setScreenKeepOn(true);
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        // Re-apply screen keep-on if still playing after buffering
                        if (mVideoView != null && mVideoView.isPlaying()) {
                            setScreenKeepOn(true);
                        }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
        // Restore saved playback position after player is prepared (post to next frame
        // so IjkVideoView finishes its own onPrepared logic — start(), mSeekWhenPrepared — first)
        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                restorePlaybackPosition();
                // Restore persisted playback speed
                float savedSpeed = mSettings != null ? mSettings.getPlaybackSpeed() : 1.0f;
                if (savedSpeed != 1.0f && mVideoView != null) {
                    mVideoView.setSpeed(savedSpeed);
                }
            }
        });
        // Clear saved position and release screen keep-on when playback completes
        mVideoView.setOnCompletionListener(mp -> {
            if (mLoopEnabled && mVideoView != null) {
                // Loop: seek to beginning and restart without saving position
                mSkipNextPositionRestore = true;
                mVideoView.seekTo(0);
                mVideoView.start();
            } else {
                savePlaybackPosition();  // pos >= dur-3s triggers key removal
                setScreenKeepOn(false);
            }
        });
        String vf0 = getIntent() != null ? getIntent().getStringExtra(EXTRA_VF0) : null;
        boolean isVulkanDemo = !TextUtils.isEmpty(vf0);
        if (!TextUtils.isEmpty(vf0)) {
            DebugEventLog.add("VideoActivity: vulkanDemo=true, vf0.len=" + vf0.length());
            mVideoView.setVideoFilterVf0(vf0);
            mVideoView.forcePlayerTypeOnce(Settings.PV_PLAYER__IjkMediaPlayer);
            mVideoView.setRender(IjkVideoView.RENDER_TEXTURE_VIEW);
            if (mVideoView.isMirrorHorizontal()) {
                DebugEventLog.add("VideoActivity: mirror=true -> disable for demo");
                mVideoView.setMirrorHorizontal(false);
            }
            mVideoView.setAspectRatio(IRenderView.AR_ASPECT_FIT_PARENT);
            boolean deviceVulkan = mVideoView.isDeviceSupportsVulkan();
            String support = deviceVulkan ? getString(R.string.supported) : getString(R.string.unsupported);
            // Detect Vulkan-only effects that have no CPU fallback
            boolean isVulkanOnly = !deviceVulkan && (vf0.contains("chromaber_vulkan")
                    || vf0.contains("blend_vulkan") || vf0.contains("overlay_vulkan"));
            if (isVulkanOnly) {
                mToastTextView.setText(getString(R.string.vulkan_only_effect_unavailable));
            } else {
                mToastTextView.setText(getString(R.string.vulkan_demo_enabled_detail, support));
            }
            mMediaController.showOnce(mToastTextView);
        } else {
            DebugEventLog.add("VideoActivity: vulkanDemo=false, clear vf0");
            mVideoView.setVideoFilterVf0(null);
        }
        installEdgeBackHelper();
        // Read test-hub feature extras
        float initialSpeed = getIntent() != null ? getIntent().getFloatExtra("initialSpeed", 0f) : 0f;
        if (initialSpeed >= 0.25f && initialSpeed <= 4.0f) {
            mVideoView.post(() -> {
                if (mVideoView != null) {
                    mVideoView.setSpeed(initialSpeed);
                }
            });
        }
        boolean enableLoopExtra = getIntent() != null && getIntent().getBooleanExtra("enableLoop", false);
        if (enableLoopExtra) {
            mLoopEnabled = true;
        }
        DebugEventLog.add("VideoActivity: onCreate, source=" + (mVideoPath != null ? mVideoPath : (mVideoUri != null ? mVideoUri.toString() : "null")));
        DebugEventLog.add("VideoActivity: pref.player=" + mSettings.getPlayer() + ", preferExoForHttp=" + mSettings.getPreferExoForHttp());
        // prefer mVideoPath
        if (mVideoPath != null) {
            if (isVulkanDemo && isNetworkUrl(mVideoPath)) {
                if (prepareVulkanDemoSource(mVideoPath)) {
                    DebugEventLog.add("VideoActivity: vulkanDemo download started");
                    mToastTextView.setText(getString(R.string.vulkan_demo_downloading));
                    mMediaController.showOnce(mToastTextView);
                    mMediaController.show();
                    return;
                }
                if (mVulkanDownloadedFile != null && mVulkanDownloadedFile.exists()) {
                    DebugEventLog.add("VideoActivity: vulkanDemo use cached local file=" + mVulkanDownloadedFile.getAbsolutePath());
                    mVideoView.setVideoPath(mVulkanDownloadedFile.getAbsolutePath());
                } else {
                    mVideoView.setVideoPath(mVideoPath);
                }
            } else {
                mVideoView.setVideoPath(mVideoPath);
            }
        }
        else if (mVideoUri != null)
            mVideoView.setVideoURI(mVideoUri);
        else {
            Log.e(TAG, "Null Data Source\n");
            finish();
            return;
        }
        mVideoView.start();
        mMediaController.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release SpeechRecognizer to avoid service leak
        stopAsrListening();
        if (mVulkanDownloadReceiver != null) {
            try {
                unregisterReceiver(mVulkanDownloadReceiver);
            } catch (Throwable ignored) {
            }
            mVulkanDownloadReceiver = null;
        }
        if (mAsrTrackDownloadReceiver != null) {
            try {
                unregisterReceiver(mAsrTrackDownloadReceiver);
            } catch (Throwable ignored) {
            }
            mAsrTrackDownloadReceiver = null;
        }
        tv.danmaku.ijk.media.example.util.HttpFileDownloader d = mWhisperModelDownloader;
        mWhisperModelDownloader = null;
        if (d != null) {
            try {
                d.cancel();
            } catch (Throwable ignored) {
            }
        }
        Thread t = mWhisperModelDownloadThread;
        mWhisperModelDownloadThread = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
    }

    private void installEdgeBackHelper() {
        float density = getResources().getDisplayMetrics().density;
        mEdgeBackEdgeSizePx = 32f * density;
        mEdgeBackTriggerPx = 72f * density;
        mEdgeBackTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev != null && ev.getPointerCount() == 1) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mEdgeBackDownX = ev.getX();
                mEdgeBackDownY = ev.getY();
                int width = getWindow() != null && getWindow().getDecorView() != null ? getWindow().getDecorView().getWidth() : 0;
                if (width > 0) {
                    boolean fromLeft = mEdgeBackDownX <= mEdgeBackEdgeSizePx;
                    boolean fromRight = mEdgeBackDownX >= width - mEdgeBackEdgeSizePx;
                    mEdgeBackActive = fromLeft || fromRight;
                    mEdgeBackFromLeft = fromLeft;
                } else {
                    mEdgeBackActive = false;
                }
                // Initialize gesture control state
                mGestureActive = false;
                mSeekGestureActive = false;
                mGestureDownX = ev.getX();
                mGestureDownY = ev.getY();
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mEdgeBackActive) {
                    float dx = ev.getX() - mEdgeBackDownX;
                    float dy = ev.getY() - mEdgeBackDownY;
                    if (Math.abs(dy) <= Math.abs(dx) && Math.abs(dx) >= mEdgeBackTouchSlop) {
                        if (mEdgeBackFromLeft && dx >= mEdgeBackTriggerPx) {
                            mEdgeBackActive = false;
                            getOnBackPressedDispatcher().onBackPressed();
                            return true;
                        } else if (!mEdgeBackFromLeft && dx <= -mEdgeBackTriggerPx) {
                            mEdgeBackActive = false;
                            getOnBackPressedDispatcher().onBackPressed();
                            return true;
                        }
                    }
                }
                if (!mEdgeBackActive) {
                    float dx = ev.getX() - mGestureDownX;
                    float dy = ev.getY() - mGestureDownY;
                    int width = getWindow() != null && getWindow().getDecorView() != null ? getWindow().getDecorView().getWidth() : 0;
                    int height = getWindow() != null && getWindow().getDecorView() != null ? getWindow().getDecorView().getHeight() : 0;

                    // --- Horizontal seek gesture (left/right swipe) ---
                    if (!mGestureActive && !mSeekGestureActive && width > 0
                            && Math.abs(dx) > mEdgeBackTouchSlop * 2
                            && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        mSeekGestureActive = true;
                        mSeekGestureStartMs = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
                        mSeekGestureTargetMs = mSeekGestureStartMs;
                    }
                    if (mSeekGestureActive && mVideoView != null && width > 0) {
                        // Full screen width corresponds to ±90 seconds
                        long duration = mVideoView.getDuration();
                        long maxSeekRange = Math.min(90_000L, duration > 0 ? duration : 90_000L);
                        long delta = (long) (dx / (float) width * maxSeekRange * 2);
                        mSeekGestureTargetMs = Math.max(0,
                                Math.min(duration > 0 ? duration : Long.MAX_VALUE,
                                        mSeekGestureStartMs + delta));
                        long diffSec = (mSeekGestureTargetMs - mSeekGestureStartMs) / 1000;
                        if (diffSec >= 0) {
                            mToastTextView.setText(getString(R.string.seek_forward_hint, diffSec));
                        } else {
                            mToastTextView.setText(getString(R.string.seek_backward_hint, -diffSec));
                        }
                        mMediaController.showOnce(mToastTextView);
                        return true;
                    }

                    // --- Brightness/Volume vertical gesture ---
                    if (!mGestureActive && !mSeekGestureActive && height > 0
                            && Math.abs(dy) > mEdgeBackTouchSlop
                            && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                        mGestureActive = true;
                        mGestureBrightnessSide = (width > 0 && mGestureDownX < width / 2f);
                        // Capture initial brightness/volume
                        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                        mGestureStartBrightness = (lp.screenBrightness < 0f) ? 0.5f : lp.screenBrightness;
                        AudioManager audioMgr = (AudioManager) getSystemService(AUDIO_SERVICE);
                        mGestureStartVolume = audioMgr != null ? audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                        if (mGestureMaxVolume <= 0 && audioMgr != null) {
                            mGestureMaxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        }
                    }
                    if (mGestureActive && height > 0) {
                        float fraction = -dy / (height * 0.6f);  // 60% screen height = full range
                        if (mGestureBrightnessSide) {
                            float newBrightness = Math.max(0.01f, Math.min(1.0f, mGestureStartBrightness + fraction));
                            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                            lp.screenBrightness = newBrightness;
                            getWindow().setAttributes(lp);
                            int pct = (int) (newBrightness * 100);
                            mToastTextView.setText(getString(R.string.brightness_label) + ": " + pct + "%");
                            mMediaController.showOnce(mToastTextView);
                        } else {
                            int max = mGestureMaxVolume > 0 ? mGestureMaxVolume : 15;
                            int newVol = Math.max(0, Math.min(max, (int) Math.round(mGestureStartVolume + fraction * max)));
                            AudioManager audioMgr = (AudioManager) getSystemService(AUDIO_SERVICE);
                            if (audioMgr != null) {
                                audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                            }
                            int pct = max > 0 ? (int) (newVol * 100.0f / max) : 0;
                            mToastTextView.setText(getString(R.string.volume_label) + ": " + pct + "%");
                            mMediaController.showOnce(mToastTextView);
                        }
                        return true;
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean wasSeekGesture = mSeekGestureActive;
                boolean wasVerticalGesture = mGestureActive;
                mEdgeBackActive = false;
                mGestureActive = false;
                mSeekGestureActive = false;

                if (action == MotionEvent.ACTION_UP) {
                    if (wasSeekGesture) {
                        // Commit the seek
                        if (mVideoView != null) {
                            mVideoView.seekTo((int) mSeekGestureTargetMs);
                        }
                    } else if (!wasVerticalGesture) {
                        // Potential tap / double-tap
                        float dx = Math.abs(ev.getX() - mGestureDownX);
                        float dy = Math.abs(ev.getY() - mGestureDownY);
                        if (dx < mEdgeBackTouchSlop && dy < mEdgeBackTouchSlop) {
                            long now = System.currentTimeMillis();
                            float lastX = mLastTapX;
                            float lastY = mLastTapY;
                            if (now - mLastTapTime <= DOUBLE_TAP_TIMEOUT_MS
                                    && Math.abs(ev.getX() - lastX) < mEdgeBackTouchSlop * 4
                                    && Math.abs(ev.getY() - lastY) < mEdgeBackTouchSlop * 4) {
                                // Double-tap: toggle pause/resume
                                mLastTapTime = 0;  // reset to avoid triple-tap issues
                                if (mVideoView != null) {
                                    if (mVideoView.isPlaying()) {
                                        mVideoView.pause();
                                        mToastTextView.setText(getString(R.string.playback_paused));
                                    } else {
                                        mVideoView.start();
                                        mToastTextView.setText(getString(R.string.playback_resumed_play));
                                    }
                                    mMediaController.showOnce(mToastTextView);
                                }
                            } else {
                                // Single tap: record for potential double-tap
                                mLastTapTime = now;
                                mLastTapX = ev.getX();
                                mLastTapY = ev.getY();
                            }
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isNetworkUrl(String url) {
        String u = normalizeUrl(url);
        return u != null && (u.startsWith("http://") || u.startsWith("https://"));
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String out = url.trim();
        while (out.startsWith("`")) {
            out = out.substring(1).trim();
        }
        while (out.endsWith("`") || out.endsWith(",")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        return out;
    }

    private boolean prepareVulkanDemoSource(String url) {
        try {
            url = normalizeUrl(url);
            File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (dir == null) {
                return false;
            }
            File sampleDir = new File(dir, "samples");
            if (!sampleDir.exists() && !sampleDir.mkdirs()) {
                return false;
            }
            String name = "sample_" + sha1(url) + ".mp4";
            mVulkanDownloadedFile = new File(sampleDir, name);
            if (mVulkanDownloadedFile.exists() && mVulkanDownloadedFile.length() > 0) {
                return false;
            }

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                return false;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MOVIES, "samples/" + name);
            mVulkanDownloadId = dm.enqueue(req);

            if (mVulkanDownloadReceiver == null) {
                mVulkanDownloadReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                            return;
                        }
                        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                        if (id != mVulkanDownloadId) {
                            return;
                        }
                        handleVulkanDownloadComplete();
                    }
                };
                registerReceiver(mVulkanDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            return true;
        } catch (Throwable t) {
            DebugEventLog.add("VideoActivity: vulkanDemo download exception=" + t.getClass().getSimpleName());
            return false;
        }
    }

    private void handleVulkanDownloadComplete() {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                return;
            }
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(mVulkanDownloadId);
            Cursor c = dm.query(q);
            if (c == null) {
                return;
            }
            boolean ok = false;
            try {
                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    ok = status == DownloadManager.STATUS_SUCCESSFUL;
                }
            } finally {
                c.close();
            }

            if (ok && mVulkanDownloadedFile != null && mVulkanDownloadedFile.exists() && mVulkanDownloadedFile.length() > 0) {
                mToastTextView.setText(getString(R.string.vulkan_demo_downloaded));
                mMediaController.showOnce(mToastTextView);
                mVideoView.setVideoPath(mVulkanDownloadedFile.getAbsolutePath());
                mVideoView.start();
                mMediaController.show();
            } else {
                mToastTextView.setText(getString(R.string.vulkan_demo_download_failed));
                mMediaController.showOnce(mToastTextView);
            }
        } catch (Throwable t) {
            try {
                mToastTextView.setText(getString(R.string.vulkan_demo_download_failed));
                mMediaController.showOnce(mToastTextView);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean prepareAsrTrackSource(String url) {
        try {
            url = normalizeUrl(url);
            if (TextUtils.isEmpty(url) || !isNetworkUrl(url)) {
                return false;
            }
            if (url.contains(".m3u8")) {
                return false;
            }
            File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (dir == null) {
                return false;
            }
            File asrDir = new File(dir, "asr");
            if (!asrDir.exists() && !asrDir.mkdirs()) {
                return false;
            }
            String ext = ".mp4";
            try {
                String path = Uri.parse(url).getPath();
                if (!TextUtils.isEmpty(path)) {
                    int dot = path.lastIndexOf('.');
                    if (dot > 0 && dot < path.length() - 1) {
                        String e = path.substring(dot);
                        if (e.length() <= 6) {
                            ext = e;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            String name = "asr_" + sha1(url) + ext;
            mAsrTrackDownloadedFile = new File(asrDir, name);
            mAsrTrackDownloadUrl = url;
            if (mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                return false;
            }
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                return false;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MOVIES, "asr/" + name);
            mAsrTrackDownloadId = dm.enqueue(req);
            if (mAsrTrackDownloadReceiver == null) {
                mAsrTrackDownloadReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                            return;
                        }
                        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                        if (id != mAsrTrackDownloadId) {
                            return;
                        }
                        handleAsrTrackDownloadComplete();
                    }
                };
                registerReceiver(mAsrTrackDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            return true;
        } catch (Throwable t) {
            DebugEventLog.add("VideoActivity: asrTrack download exception=" + t.getClass().getSimpleName());
            return false;
        }
    }

    private void handleAsrTrackDownloadComplete() {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                return;
            }
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(mAsrTrackDownloadId);
            Cursor c = dm.query(q);
            if (c == null) {
                return;
            }
            boolean ok = false;
            try {
                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    ok = status == DownloadManager.STATUS_SUCCESSFUL;
                }
            } finally {
                c.close();
            }
            if (ok && mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                mToastTextView.setText(getString(R.string.subtitle_asr_track_downloaded));
                mMediaController.showOnce(mToastTextView);
                startTrackAsrIfPossible();
            } else {
                mToastTextView.setText(getString(R.string.subtitle_asr_track_download_failed));
                mMediaController.showOnce(mToastTextView);
            }
        } catch (Throwable t) {
            try {
                mToastTextView.setText(getString(R.string.subtitle_asr_track_download_failed));
                mMediaController.showOnce(mToastTextView);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean prepareWhisperModelDownload(String url) {
        try {
            url = normalizeUrl(url);
            if (TextUtils.isEmpty(url) || !isNetworkUrl(url)) {
                return false;
            }
            File modelDir = new File(getFilesDir(), "asr/models");
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                return false;
            }
            String ext = ".bin";
            try {
                String path = Uri.parse(url).getPath();
                if (!TextUtils.isEmpty(path)) {
                    int dot = path.lastIndexOf('.');
                    if (dot > 0 && dot < path.length() - 1) {
                        String e = path.substring(dot);
                        if (e.length() <= 8) {
                            ext = e;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            String name = "whisper_" + sha1(url) + ext;
            mWhisperModelDownloadedFile = new File(modelDir, name);
            mWhisperModelDownloadUrl = url;
            if (mWhisperModelDownloadedFile.exists() && mWhisperModelDownloadedFile.length() > 0) {
                if (mSettings != null) {
                    mSettings.setAsrWhisperModelPath(mWhisperModelDownloadedFile.getAbsolutePath());
                }
                return false;
            }
            if (mWhisperModelDownloader != null) {
                try {
                    mWhisperModelDownloader.cancel();
                } catch (Throwable ignored) {
                }
            }
            tv.danmaku.ijk.media.example.util.HttpFileDownloader downloader = new tv.danmaku.ijk.media.example.util.HttpFileDownloader();
            mWhisperModelDownloader = downloader;
            String finalUrl = url;
            Thread t = new Thread(() -> downloader.download(finalUrl, mWhisperModelDownloadedFile, new tv.danmaku.ijk.media.example.util.HttpFileDownloader.Listener() {
                @Override
                public void onProgress(long downloadedBytes, long totalBytes) {
                    mSubtitleHandler.post(() -> {
                        int percent = totalBytes > 0 ? (int) (downloadedBytes * 100 / totalBytes) : 0;
                        String sofarText = formatBytes(downloadedBytes);
                        String totalText = totalBytes > 0 ? formatBytes(totalBytes) : "?";
                        mToastTextView.setText(getString(R.string.subtitle_asr_whisper_downloading_progress, percent, sofarText, totalText));
                        mMediaController.showOnce(mToastTextView);
                    });
                }

                @Override
                public void onSuccess(File file) {
                    mSubtitleHandler.post(() -> {
                        if (mSettings != null) {
                            mSettings.setAsrWhisperModelPath(file.getAbsolutePath());
                        }
                        mToastTextView.setText(getString(R.string.subtitle_asr_whisper_downloaded_ready));
                        mMediaController.showOnce(mToastTextView);
                        if (mAsrEnabled) {
                            startWhisperTrackAsrIfPossible();
                        }
                    });
                }

                @Override
                public void onError(Throwable t) {
                    mSubtitleHandler.post(() -> {
                        mToastTextView.setText(getString(R.string.subtitle_asr_whisper_download_failed));
                        mMediaController.showOnce(mToastTextView);
                    });
                }
            }), "whisper-model-download");
            mWhisperModelDownloadThread = t;
            t.start();
            return true;
        } catch (Throwable t) {
            DebugEventLog.add("VideoActivity: whisperModel download exception=" + t.getClass().getSimpleName());
            return false;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "0B";
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1fKB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1fMB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2fGB", gb);
    }

    private String sha1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(text != null ? text.hashCode() : 0);
        }
    }

    private void playUrl(String url) {
        if (TextUtils.isEmpty(url))
            return;
        url = normalizeUrl(url);
        try {
            if (MediaSourceUtil.isLikelyMediaUrl(url)) {
                DebugEventLog.add("playUrl: " + url);
                mVideoPath = url;
                mVideoUri = Uri.parse(url);
                mVideoView.stopPlayback();
                mVideoView.release(true);
                DebugEventLog.add("playUrl: clear vf0");
                mVideoView.setVideoFilterVf0(null);
                mVideoView.setVideoURI(Uri.parse(url));
                mVideoView.start();
                new RecentMediaStorage(this).saveUrlAsync(url);
            } else {
                Toast.makeText(this, getString(R.string.error_url_not_direct_video), Toast.LENGTH_SHORT).show();
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browser);
            }
        } catch (Exception e) {
            Log.e(TAG, "play url error", e);
        }
    }

    private String getCurrentSource() {
        return MediaSourceUtil.getCurrentSource(mVideoPath, mVideoUri);
    }

    /**
     * Apply a video filter by rebuilding the player with the given vf0 string.
     * Playback position is preserved via the normal onPrepared restore path.
     *
     * @param vf0   FFmpeg vf0 filter string, or null to remove the filter
     * @param label Human-readable label shown in the toast
     */
    /**
     * Apply a visual filter via the render-view layer (ColorMatrix / View transform).
     * No player rebuild needed — takes effect instantly without interrupting playback.
     */
    private void applyVideoFilter(int filterType, String label) {
        if (mVideoView == null) return;
        mCurrentFilterType = filterType;
        // Clear FFmpeg vf0 filter when switching to render-layer filter
        if (mCurrentVf0Filter != null) {
            mCurrentVf0Filter = null;
            mVideoView.applyVf0FilterNow(null);
        }
        mVideoView.setRenderFilter(filterType);
        mToastTextView.setText(getString(R.string.filter_applied, label));
        mMediaController.showOnce(mToastTextView);
        invalidateOptionsMenu();
    }

    /**
     * Apply an FFmpeg software filter via vf0 (requires recompiled FFmpeg with --enable-filters).
     * Uses applyVf0FilterNow for runtime update without rebuild.
     * Clears any active render-layer filter first.
     *
     * @param vf0   FFmpeg vf0 filter string, or null/empty to clear
     * @param menuId the menu item id to associate (for onPrepareOptionsMenu check state)
     * @param label  Human-readable label for toast
     */
    private void applyFfmpegFilter(String vf0, int menuId, String label) {
        if (mVideoView == null) return;
        // FFmpeg vf0 filters only work with the Ijk (FFmpeg) backend.
        // If currently using ExoPlayer / AndroidMediaPlayer, inform the user and bail out.
        if (!mVideoView.isActivePlayerIjk()) {
            Toast.makeText(this, getString(R.string.ffmpeg_filter_ijk_only), Toast.LENGTH_SHORT).show();
            return;
        }
        // Toggle off if already active
        if (vf0 != null && vf0.equals(mCurrentVf0Filter)) {
            mCurrentVf0Filter = null;
            mVideoView.applyVf0FilterNow(null);
            mToastTextView.setText(getString(R.string.filter_applied, getString(R.string.filter_none)));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        // Clear render-layer filter
        if (mCurrentFilterType != IjkVideoView.RENDER_FILTER_NONE) {
            mCurrentFilterType = IjkVideoView.RENDER_FILTER_NONE;
            mVideoView.setRenderFilter(IjkVideoView.RENDER_FILTER_NONE);
        }
        mCurrentVf0Filter = vf0;
        mVideoView.applyVf0FilterNow(vf0);
        mToastTextView.setText(getString(R.string.filter_applied, label));
        mMediaController.showOnce(mToastTextView);
        invalidateOptionsMenu();
    }

    /**
     * Show a dialog for the user to enter a custom FFmpeg vf0 filter string.
     * Pre-fills with the current active vf0 filter (if any).
     */
    private void showCustomVf0Dialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.filter_ffmpeg_custom_hint));
        input.setSingleLine(false);
        input.setMaxLines(3);
        if (!TextUtils.isEmpty(mCurrentVf0Filter)) {
            input.setText(mCurrentVf0Filter);
            input.setSelection(mCurrentVf0Filter.length());
        }
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.filter_ffmpeg_custom_dialog_title))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String vf0 = input.getText().toString().trim();
                    if (TextUtils.isEmpty(vf0)) {
                        // Clear filter
                        applyFfmpegFilter(null, R.id.action_filter_ffmpeg_custom, getString(R.string.filter_none));
                    } else {
                        applyFfmpegFilter(vf0, R.id.action_filter_ffmpeg_custom,
                                getString(R.string.filter_ffmpeg_custom).replace("…", "") + ": " + vf0);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void rebuildAndPlayCurrent() {
        String source = getCurrentSource();
        if (TextUtils.isEmpty(source))
            return;

        // User explicitly requested rebuild: start from the beginning
        mSkipNextPositionRestore = true;
        try {
            DebugEventLog.add("rebuildAndPlayCurrent: " + source);
            mVideoView.stopPlayback();
            mVideoView.release(true);
            DebugEventLog.add("rebuildAndPlayCurrent: clear vf0");
            mVideoView.setVideoFilterVf0(null);
            if (MediaSourceUtil.isManifestStringSource(source)) {
                mVideoView.setVideoPath(source);
            } else {
                mVideoView.setVideoURI(Uri.parse(source));
            }
            mVideoView.start();
        } catch (Exception e) {
            Log.e(TAG, "rebuild play error", e);
        }
    }

    private void restartSameSourceSeek0() {
        // User explicitly restarted to position 0: skip position restore
        mSkipNextPositionRestore = true;
        try {
            mVideoView.pause();
            mVideoView.seekTo(0);
            mVideoView.start();
        } catch (Exception e) {
            rebuildAndPlayCurrent();
        }
    }

    private void toggleCoreAndRebuild() {
        int current = mSettings.getPlayer();
        PlayerToggle.ToggleResult result = PlayerToggle.toggleCore(current);
        int next = result.nextPlayer;
        boolean preferExoForHttp = result.preferExoForHttp;

        DebugEventLog.add("toggleCore: " + current + " -> " + next + ", preferExoForHttp=" + preferExoForHttp);
        mSettings.setPlayer(next);
        mSettings.setPreferExoForHttp(preferExoForHttp);
        // Switching player engine: treat as fresh start
        mSkipNextPositionRestore = true;
        rebuildAndPlayCurrent();

        String playerText = IjkVideoView.getPlayerText(this, next);
        mToastTextView.setText(playerText);
        mMediaController.showOnce(mToastTextView);
    }

    private void showOpenUrlDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        String current = mVideoPath != null ? mVideoPath : (mVideoUri != null ? mVideoUri.toString() : "");
        input.setText(current);
        input.setSingleLine(true);

        // Auto-fill from clipboard if it looks like a media URL and differs from current
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData.Item clipItem = cm.getPrimaryClip().getItemAt(0);
                if (clipItem != null) {
                    CharSequence seq = clipItem.getText();
                    if (seq != null) {
                        String clipText = normalizeUrl(seq.toString());
                        if (!TextUtils.isEmpty(clipText)
                                && (clipText.startsWith("http://") || clipText.startsWith("https://"))
                                && !clipText.equals(current)) {
                            input.setText(clipText);
                            input.setSelection(clipText.length());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.open_url))
                .setView(input)
                .setPositiveButton(getString(R.string.action_play), (d, which) -> {
                    String url = input.getText() != null ? input.getText().toString().trim() : "";
                    playUrl(url);
                })
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    @Override
    public void onBackPressed() {
        mBackPressed = true;

        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mBackPressed || !mVideoView.isBackgroundPlayEnabled()) {
            // Save playback position before stopping
            savePlaybackPosition();
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        // Release screen keep-on when leaving
        setScreenKeepOn(false);
        IjkMediaPlayer.native_profileEnd();
    }

    /** Keep screen on while playing; release when paused/stopped. */
    private void setScreenKeepOn(boolean on) {
        if (mScreenKeepOn == on) return;
        mScreenKeepOn = on;
        if (on) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /** Persist current URL -> position into SharedPreferences. */
    private void savePlaybackPosition() {
        if (mVideoView == null) return;
        String url = normalizePositionKey(
                mVideoPath != null ? mVideoPath : (mVideoUri != null ? mVideoUri.toString() : null));
        if (TextUtils.isEmpty(url)) return;
        int pos = mVideoView.getCurrentPosition();
        int dur = mVideoView.getDuration();
        android.content.SharedPreferences prefs =
                getSharedPreferences(PREFS_PLAYBACK_POS, MODE_PRIVATE);
        if (pos < POSITION_SAVE_THRESHOLD_MS || (dur > 0 && pos >= dur - POSITION_RESTORE_THRESHOLD_MS)) {
            // Near start or end: clear saved position
            prefs.edit().remove(url).apply();
        } else {
            prefs.edit().putInt(url, pos).apply();
        }
    }

    /** Restore saved position for the current URL, seekTo it after player starts. */
    private void restorePlaybackPosition() {
        // Skip if a manual rebuild/restart requested fresh start
        if (mSkipNextPositionRestore) {
            mSkipNextPositionRestore = false;
            return;
        }
        if (mVideoView == null) return;
        String url = normalizePositionKey(
                mVideoPath != null ? mVideoPath : (mVideoUri != null ? mVideoUri.toString() : null));
        if (TextUtils.isEmpty(url)) return;
        android.content.SharedPreferences prefs =
                getSharedPreferences(PREFS_PLAYBACK_POS, MODE_PRIVATE);
        final int savedPos = prefs.getInt(url, 0);
        if (savedPos > POSITION_SAVE_THRESHOLD_MS) {
            // Post to next frame: let IjkVideoView finish its own onPrepared logic
            // (start(), mSeekWhenPrepared handling) before we override seek position
            mVideoView.post(() -> {
                if (mVideoView != null && savedPos > POSITION_SAVE_THRESHOLD_MS) {
                    mVideoView.seekTo(savedPos);
                    mToastTextView.setText(getString(R.string.playback_position_resumed));
                    mMediaController.showOnce(mToastTextView);
                }
            });
        }
    }

    /**
     * Normalize a URL to use as SharedPreferences key.
     * Strips query parameters and fragment so that the same video with different
     * token/session params maps to the same key.
     */
    private String normalizePositionKey(String url) {
        if (url == null) return null;
        try {
            Uri u = Uri.parse(url);
            return u.buildUpon().clearQuery().fragment(null).build().toString();
        } catch (Throwable t) {
            return url;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSubtitleHandler.removeCallbacks(mSubtitleTick);
        mSubtitleHandler.post(mSubtitleTick);
        if (mAsrEnabled) {
            startAsrByMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSubtitleHandler.removeCallbacks(mSubtitleTick);
        stopAsrListening();
        stopTrackAsr();
        stopRemoteAsr();
        // Release screen keep-on whenever the activity is no longer in foreground
        setScreenKeepOn(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_orientation) {
            int next = nextOrientation(mSettings.getPlayerOrientation());
            mSettings.setPlayerOrientation(next);
            applyPlayerOrientation(next);
            mToastTextView.setText(getString(R.string.toggle_orientation) + ": " + getOrientationText(next));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_rebuild_play) {
            rebuildAndPlayCurrent();
            return true;
        } else if (id == R.id.action_restart_seek0) {
            restartSameSourceSeek0();
            return true;
        } else if (id == R.id.action_toggle_ratio) {
            int aspectRatio = mVideoView.toggleAspectRatio();
            String aspectRatioText = MeasureHelper.getAspectRatioText(this, aspectRatio);
            mToastTextView.setText(aspectRatioText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_player) {
            toggleCoreAndRebuild();
            return true;
        } else if (id == R.id.action_toggle_render) {
            int render = mVideoView.toggleRender();
            String renderText = IjkVideoView.getRenderText(this, render);
            mToastTextView.setText(renderText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_show_info) {
            mVideoView.showMediaInfo();
        } else if (id == R.id.action_show_tracks) {
            String tag = "tracks_sheet";
            Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
            if (existing != null) {
                getSupportFragmentManager().beginTransaction().remove(existing).commit();
            } else {
                tv.danmaku.ijk.media.example.fragments.TracksBottomSheetDialogFragment.newInstance()
                        .show(getSupportFragmentManager(), tag);
            }
        } else if (id == R.id.action_show_diagnostics) {
            showDiagnosticsSheet();
            return true;
        } else if (id == R.id.action_toggle_speed) {
            float speed = mVideoView.toggleSpeed();
            if (mSettings != null) {
                mSettings.setPlaybackSpeed(speed);
            }
            mToastTextView.setText(String.format(Locale.US, "%s: %.1fx", getString(R.string.playback_speed), speed));
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_loop) {
            mLoopEnabled = !mLoopEnabled;
            item.setChecked(mLoopEnabled);
            mToastTextView.setText(getString(mLoopEnabled ? R.string.loop_on : R.string.loop_off));
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_filter_none) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_NONE, getString(R.string.filter_none));
            return true;
        } else if (id == R.id.action_filter_grayscale) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_GRAYSCALE, getString(R.string.filter_grayscale));
            return true;
        } else if (id == R.id.action_filter_hflip) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_HFLIP, getString(R.string.filter_hflip));
            return true;
        } else if (id == R.id.action_filter_vflip) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_VFLIP, getString(R.string.filter_vflip));
            return true;
        } else if (id == R.id.action_filter_blur) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_BRIGHT, getString(R.string.filter_blur));
            return true;
        } else if (id == R.id.action_filter_dark) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_DARK, getString(R.string.filter_dark));
            return true;
        } else if (id == R.id.action_filter_rotate90) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_ROTATE90, getString(R.string.filter_rotate90));
            return true;
        } else if (id == R.id.action_filter_warm) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_WARM, getString(R.string.filter_warm));
            return true;
        } else if (id == R.id.action_filter_cool) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_COOL, getString(R.string.filter_cool));
            return true;
        } else if (id == R.id.action_filter_sharpen) {
            applyVideoFilter(IjkVideoView.RENDER_FILTER_SHARPEN, getString(R.string.filter_sharpen));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_hflip) {
            applyFfmpegFilter("hflip", id, getString(R.string.filter_ffmpeg_hflip));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_vflip) {
            applyFfmpegFilter("vflip", id, getString(R.string.filter_ffmpeg_vflip));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_gblur) {
            applyFfmpegFilter("gblur=sigma=5", id, getString(R.string.filter_ffmpeg_gblur));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_eq_bright) {
            applyFfmpegFilter(PlayerFactory.buildCurvesVf0("lighter"), id, getString(R.string.filter_ffmpeg_eq_bright));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_eq_dark) {
            applyFfmpegFilter(PlayerFactory.buildCurvesVf0("darker"), id, getString(R.string.filter_ffmpeg_eq_dark));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_sharpen) {
            applyFfmpegFilter("unsharp=luma_msize_x=5:luma_msize_y=5:luma_amount=1.5", id, getString(R.string.filter_ffmpeg_sharpen));
            return true;
        } else if (id == R.id.action_filter_ffmpeg_custom) {
            showCustomVf0Dialog();
            return true;
        } else if (id == R.id.action_toggle_mirror) {
            boolean next = !mSettings.getVideoMirrorHorizontal();
            mSettings.setVideoMirrorHorizontal(next);
            mVideoView.setMirrorHorizontal(next);
            item.setChecked(next);
            mToastTextView.setText(getString(next ? R.string.mirror_on : R.string.mirror_off));
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_snapshot) {
            takeSnapshot();
            return true;
        } else if (id == R.id.action_subtitle_add) {
            showSubtitleAddDialog();
            return true;
        } else if (id == R.id.action_subtitle_asr_toggle) {
            boolean next = !mAsrEnabled;
            if (next) {
                mAsrEnabled = true;
                startAsrByMode();
            } else {
                mAsrEnabled = false;
                stopAsrListening();
                stopTrackAsr();
                stopRemoteAsr();
                mAsrPartialText = null;
                updateSubtitleOverlay();
            }
            item.setChecked(mAsrEnabled);
            return true;
        } else if (id == R.id.action_subtitle_clear) {
            mSubtitleCues.clear();
            updateSubtitleOverlay();
            mToastTextView.setText(getString(R.string.subtitle_clear));
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_subtitle_export) {
            exportSubtitlesSrt();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showSubtitleAddDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(2);

        int pos = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.subtitle_input_title) + " (" + formatSrtTime(pos) + ")")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(text)) {
                        return;
                    }
                    int startMs = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
                    addSubtitleCue(startMs, text);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addSubtitleCue(int startMs, String text) {
        SubtitleCue cue = new SubtitleCue(startMs, -1, text);
        int insertAt = mSubtitleCues.size();
        for (int i = 0; i < mSubtitleCues.size(); i++) {
            if (startMs < mSubtitleCues.get(i).startMs) {
                insertAt = i;
                break;
            }
        }
        mSubtitleCues.add(insertAt, cue);
        normalizeSubtitleEnds();
        updateSubtitleOverlay();
    }

    private void normalizeSubtitleEnds() {
        int duration = mVideoView != null ? mVideoView.getDuration() : -1;
        for (int i = 0; i < mSubtitleCues.size(); i++) {
            SubtitleCue c = mSubtitleCues.get(i);
            int nextStart = (i + 1 < mSubtitleCues.size()) ? mSubtitleCues.get(i + 1).startMs : -1;
            int end;
            if (nextStart > 0) {
                end = Math.max(c.startMs + 300, nextStart - 1);
            } else if (duration > 0) {
                end = Math.min(duration, c.startMs + 2000);
            } else {
                end = c.startMs + 2000;
            }
            c.endMs = end;
        }
    }

    private void updateSubtitleOverlay() {
        if (mSubtitleOverlay == null || mVideoView == null) {
            return;
        }
        if (mAsrEnabled && !TextUtils.isEmpty(mAsrPartialText)) {
            mSubtitleOverlay.setText(mAsrPartialText);
            mSubtitleOverlay.setVisibility(View.VISIBLE);
            return;
        }
        int pos = mVideoView.getCurrentPosition();
        String text = null;
        for (int i = 0; i < mSubtitleCues.size(); i++) {
            SubtitleCue c = mSubtitleCues.get(i);
            if (pos >= c.startMs && pos <= c.endMs) {
                text = c.text;
                break;
            }
        }
        if (TextUtils.isEmpty(text)) {
            mSubtitleOverlay.setVisibility(View.GONE);
        } else {
            mSubtitleOverlay.setText(text);
            mSubtitleOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void exportSubtitlesSrt() {
        if (mSubtitleCues.isEmpty()) {
            mToastTextView.setText(getString(R.string.subtitle_failed));
            mMediaController.showOnce(mToastTextView);
            return;
        }
        normalizeSubtitleEnds();
        String srt = buildSrtText();
        boolean ok = writeSrtToDownloads(srt);
        mToastTextView.setText(getString(ok ? R.string.subtitle_saved : R.string.subtitle_failed));
        mMediaController.showOnce(mToastTextView);
    }

    private String buildSrtText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mSubtitleCues.size(); i++) {
            SubtitleCue c = mSubtitleCues.get(i);
            sb.append(i + 1).append('\n');
            sb.append(formatSrtTime(c.startMs)).append(" --> ").append(formatSrtTime(c.endMs)).append('\n');
            sb.append(c.text != null ? c.text : "").append("\n\n");
        }
        return sb.toString();
    }

    private boolean writeSrtToDownloads(String content) {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "subtitle_" + time + ".srt";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IJKPlayer");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) {
                        return false;
                    }
                    os.write(content.getBytes("UTF-8"));
                    os.flush();
                }
                return true;
            }
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                return false;
            }
            File out = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String formatSrtTime(int ms) {
        int total = Math.max(0, ms);
        int hours = total / 3600000;
        int minutes = (total % 3600000) / 60000;
        int seconds = (total % 60000) / 1000;
        int millis = total % 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    static final class SubtitleCue {
        final int startMs;
        int endMs;
        final String text;

        SubtitleCue(int startMs, int endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    private void startAsrIfPossible() {
        if (!mAsrEnabled) {
            return;
        }
        boolean availableFlag = false;
        try {
            availableFlag = SpeechRecognizer.isRecognitionAvailable(this);
        } catch (Throwable ignored) {
        }
        int services = queryAsrServiceCount();
        if (!availableFlag && services <= 0) {
            String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
            if (!TextUtils.isEmpty(endpoint)) {
                startRemoteAsrIfPossible();
                return;
            }
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_unavailable));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mAsrPendingStart = true;
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        startAsrListening();
    }

    private void startAsrListening() {
        if (!mAsrEnabled) {
            return;
        }
        try {
            if (mSpeechRecognizer == null) {
                mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) {
                    }

                    @Override
                    public void onEndOfSpeech() {
                    }

                    @Override
                    public void onError(int error) {
                        if (!mAsrEnabled) {
                            return;
                        }
                        DebugEventLog.add("ASR.onError=" + errorToText(error));
                        mSubtitleHandler.postDelayed(() -> {
                            if (mAsrEnabled) {
                                restartAsr();
                            }
                        }, 600);
                    }

                    @Override
                    public void onResults(Bundle results) {
                        handleAsrResults(results, false);
                        if (mAsrEnabled) {
                            mSubtitleHandler.postDelayed(() -> {
                                if (mAsrEnabled) {
                                    restartAsr();
                                }
                            }, 200);
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        handleAsrResults(partialResults, true);
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {
                    }
                });
            }
            if (mSpeechIntent == null) {
                mSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            }
            mSpeechRecognizer.startListening(mSpeechIntent);
        } catch (Throwable t) {
            mAsrEnabled = false;
            mAsrPartialText = null;
            updateSubtitleOverlay();
            mToastTextView.setText(getString(R.string.subtitle_asr_start_failed));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
        }
    }

    private void restartAsr() {
        try {
            if (mSpeechRecognizer != null) {
                mSpeechRecognizer.cancel();
            }
        } catch (Throwable ignored) {
        }
        startAsrListening();
    }

    private void stopAsrListening() {
        try {
            if (mSpeechRecognizer != null) {
                mSpeechRecognizer.cancel();
                mSpeechRecognizer.destroy();
                mSpeechRecognizer = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void startAsrByMode() {
        if (!mAsrEnabled) {
            return;
        }
        String mode = mSettings != null ? mSettings.getAsrMode() : "system";
        if ("whisper_track".equalsIgnoreCase(mode)) {
            startWhisperTrackAsrIfPossible();
        } else if ("remote_track".equalsIgnoreCase(mode)) {
            startTrackAsrIfPossible();
        } else if ("remote".equalsIgnoreCase(mode)) {
            startRemoteAsrIfPossible();
        } else {
            startAsrIfPossible();
        }
    }

    private void startRemoteAsrIfPossible() {
        if (!mAsrEnabled) {
            return;
        }
        String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        if (TextUtils.isEmpty(endpoint)) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_remote_missing_endpoint));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mAsrPendingStart = true;
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        startRemoteAsr();
    }

    private void startRemoteAsr() {
        stopRemoteAsr();
        stopTrackAsr();
        stopAsrListening();

        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        int bufferSize = Math.max(minBuffer, sampleRate * 2);
        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
        try {
            record.startRecording();
        } catch (Throwable t) {
            try {
                record.release();
            } catch (Throwable ignored) {
            }
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        mAsrAudioRecord = record;
        mAsrRemoteRunning = true;
        mAsrRemoteChunkStartPlayerMs = -1;
        mAsrRemoteChunkBytes = 0;
        mAsrRemoteChunkHasVoice = false;
        mAsrRemoteLastVoiceAtMs = -1;
        mAsrRemoteChunkBuffer.reset();
        mAsrRemoteCommittedTail = "";
        if (mAsrRemoteExecutor != null) {
            try {
                mAsrRemoteExecutor.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
        mAsrRemoteExecutor = Executors.newSingleThreadExecutor();

        mToastTextView.setText(getString(R.string.subtitle_asr_remote_start));
        mMediaController.showOnce(mToastTextView);

        mAsrRemoteThread = new Thread(() -> remoteAsrLoop(sampleRate), "asr-remote");
        mAsrRemoteThread.start();
    }

    private void stopRemoteAsr() {
        mAsrRemoteRunning = false;
        AudioRecord r = mAsrAudioRecord;
        mAsrAudioRecord = null;
        if (r != null) {
            try {
                r.stop();
            } catch (Throwable ignored) {
            }
            try {
                r.release();
            } catch (Throwable ignored) {
            }
        }
        Thread t = mAsrRemoteThread;
        mAsrRemoteThread = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
        ExecutorService ex = mAsrRemoteExecutor;
        mAsrRemoteExecutor = null;
        if (ex != null) {
            try {
                ex.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
    }

    private void startTrackAsrIfPossible() {
        if (!mAsrEnabled) {
            return;
        }
        mAsrTrackUseWhisper = false;
        mAsrWhisperModelPath = null;
        String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        if (TextUtils.isEmpty(endpoint)) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_remote_missing_endpoint));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_track_unsupported));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }

        stopAsrListening();
        stopRemoteAsr();
        stopTrackAsr();

        if (mAsrRemoteExecutor != null) {
            try {
                mAsrRemoteExecutor.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
        mAsrRemoteExecutor = Executors.newSingleThreadExecutor();
        mAsrRemoteCommittedTail = "";
        mAsrPartialText = null;
        updateSubtitleOverlay();

        mAsrTrackChunkBuffer.reset();
        mAsrTrackChunkBytes = 0;
        mAsrTrackChunkStartMs = -1;
        mAsrTrackChunkHasVoice = false;
        mAsrTrackLastVoiceMs = -1;
        mAsrTrackLastEndMs = -1;

        String source = mVideoView != null ? mVideoView.getDataSource() : null;
        if (TextUtils.isEmpty(source)) {
            source = mVideoPath != null ? mVideoPath : (mVideoUri != null ? String.valueOf(mVideoUri) : "");
        }
        String finalSource = source;
        if (TextUtils.isEmpty(finalSource)) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        finalSource = normalizeUrl(finalSource);
        if (isNetworkUrl(finalSource)) {
            if (finalSource.contains(".m3u8")) {
                mAsrEnabled = false;
                mToastTextView.setText(getString(R.string.subtitle_asr_track_hls_unsupported));
                mMediaController.showOnce(mToastTextView);
                invalidateOptionsMenu();
                return;
            }
            if (mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                finalSource = mAsrTrackDownloadedFile.getAbsolutePath();
            } else if (mAsrTrackDownloadUrl != null && mAsrTrackDownloadUrl.equals(finalSource) && mAsrTrackDownloadId > 0) {
                mToastTextView.setText(getString(R.string.subtitle_asr_track_downloading));
                mMediaController.showOnce(mToastTextView);
                return;
            } else {
                boolean started = prepareAsrTrackSource(finalSource);
                if (started) {
                    mToastTextView.setText(getString(R.string.subtitle_asr_track_downloading));
                    mMediaController.showOnce(mToastTextView);
                    return;
                }
                if (mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                    finalSource = mAsrTrackDownloadedFile.getAbsolutePath();
                } else {
                    mAsrEnabled = false;
                    mToastTextView.setText(getString(R.string.subtitle_asr_track_download_failed));
                    mMediaController.showOnce(mToastTextView);
                    invalidateOptionsMenu();
                    return;
                }
            }
        }

        int startPlayerMs = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
        if (mAsrTrackDecoder == null) {
            mAsrTrackDecoder = new AsrAudioTrackDecoder();
        }

        mToastTextView.setText(getString(R.string.subtitle_asr_track_start));
        mMediaController.showOnce(mToastTextView);

        mAsrTrackDecoder.start(this, finalSource, startPlayerMs, new AsrAudioTrackDecoder.Listener() {
            @Override
            public void onPcmChunk(byte[] pcmData, int startMs, int endMs, int sampleRate, int channelCount, String audioFormat) {
                handleTrackPcmChunk(pcmData, startMs, endMs, sampleRate, channelCount, audioFormat);
            }

            @Override
            public void onStopped() {
                mSubtitleHandler.post(() -> {
                    flushPendingTrackChunkFromDecoderStop();
                    if (mAsrEnabled) {
                        mToastTextView.setText(getString(R.string.subtitle_asr_track_stop));
                        mMediaController.showOnce(mToastTextView);
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                mSubtitleHandler.post(() -> {
                    if (mAsrEnabled) {
                        mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
                        mMediaController.showOnce(mToastTextView);
                    }
                });
            }
        });
    }

    private void startWhisperTrackAsrIfPossible() {
        if (!mAsrEnabled) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_track_unsupported));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        if (!WhisperAsrEngine.isLoaded()) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_whisper_missing_native_detail, String.valueOf(BuildConfig.WHISPER_ENABLED)));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        if (!WhisperAsrEngine.isAvailable()) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_whisper_disabled_detail, String.valueOf(BuildConfig.WHISPER_ENABLED)));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        String modelPath = mSettings != null ? mSettings.getAsrWhisperModelPath() : "";
        java.io.File f = !TextUtils.isEmpty(modelPath) ? new java.io.File(modelPath) : null;
        if (TextUtils.isEmpty(modelPath) || f == null || !f.exists() || f.length() <= 0) {
            String modelUrl = mSettings != null ? mSettings.getAsrWhisperModelUrl() : "";
            if (TextUtils.isEmpty(modelUrl)) {
                mAsrEnabled = false;
                mToastTextView.setText(getString(R.string.subtitle_asr_whisper_missing_model));
                mMediaController.showOnce(mToastTextView);
                invalidateOptionsMenu();
                return;
            }

            modelUrl = normalizeUrl(modelUrl);
            final String finalModelUrl = modelUrl;
            if (mWhisperModelDownloadedFile != null && mWhisperModelDownloadedFile.exists() && mWhisperModelDownloadedFile.length() > 0 && modelUrl.equals(mWhisperModelDownloadUrl)) {
                if (mSettings != null) {
                    mSettings.setAsrWhisperModelPath(mWhisperModelDownloadedFile.getAbsolutePath());
                }
                startWhisperTrackAsrIfPossible();
                return;
            }
            if (modelUrl.equals(mWhisperModelDownloadUrl) && mWhisperModelDownloadThread != null && mWhisperModelDownloadThread.isAlive()) {
                mToastTextView.setText(getString(R.string.subtitle_asr_whisper_downloading));
                mMediaController.showOnce(mToastTextView);
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.subtitle_asr_whisper_download_prompt_title))
                    .setMessage(getString(R.string.subtitle_asr_whisper_download_prompt_message))
                    .setPositiveButton(getString(R.string.subtitle_asr_whisper_download_start), (d, which) -> {
                        boolean started = prepareWhisperModelDownload(finalModelUrl);
                        if (started) {
                            mToastTextView.setText(getString(R.string.subtitle_asr_whisper_downloading));
                            mMediaController.showOnce(mToastTextView);
                        } else if (mWhisperModelDownloadedFile != null && mWhisperModelDownloadedFile.exists() && mWhisperModelDownloadedFile.length() > 0) {
                            if (mSettings != null) {
                                mSettings.setAsrWhisperModelPath(mWhisperModelDownloadedFile.getAbsolutePath());
                            }
                            startWhisperTrackAsrIfPossible();
                        } else {
                            mToastTextView.setText(getString(R.string.subtitle_asr_whisper_download_failed));
                            mMediaController.showOnce(mToastTextView);
                        }
                    })
                    .setNegativeButton(getString(R.string.subtitle_asr_whisper_download_cancel), (d, which) -> {
                        mAsrEnabled = false;
                        invalidateOptionsMenu();
                    })
                    .show();
            return;
        }
        if (!WhisperAsrEngine.loadModel(modelPath)) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_whisper_missing_model));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }

        mAsrTrackUseWhisper = true;
        mAsrWhisperModelPath = modelPath;

        stopAsrListening();
        stopRemoteAsr();
        stopTrackAsr();

        if (mAsrRemoteExecutor != null) {
            try {
                mAsrRemoteExecutor.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
        mAsrRemoteExecutor = Executors.newSingleThreadExecutor();
        mAsrRemoteCommittedTail = "";
        mAsrPartialText = null;
        updateSubtitleOverlay();

        mAsrTrackChunkBuffer.reset();
        mAsrTrackChunkBytes = 0;
        mAsrTrackChunkStartMs = -1;
        mAsrTrackChunkHasVoice = false;
        mAsrTrackLastVoiceMs = -1;
        mAsrTrackLastEndMs = -1;

        String source = mVideoView != null ? mVideoView.getDataSource() : null;
        if (TextUtils.isEmpty(source)) {
            source = mVideoPath != null ? mVideoPath : (mVideoUri != null ? String.valueOf(mVideoUri) : "");
        }
        String finalSource = source;
        if (TextUtils.isEmpty(finalSource)) {
            mAsrEnabled = false;
            mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return;
        }
        finalSource = normalizeUrl(finalSource);
        if (isNetworkUrl(finalSource)) {
            if (finalSource.contains(".m3u8")) {
                mAsrEnabled = false;
                mToastTextView.setText(getString(R.string.subtitle_asr_track_hls_unsupported));
                mMediaController.showOnce(mToastTextView);
                invalidateOptionsMenu();
                return;
            }
            if (mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                finalSource = mAsrTrackDownloadedFile.getAbsolutePath();
            } else if (mAsrTrackDownloadUrl != null && mAsrTrackDownloadUrl.equals(finalSource) && mAsrTrackDownloadId > 0) {
                mToastTextView.setText(getString(R.string.subtitle_asr_track_downloading));
                mMediaController.showOnce(mToastTextView);
                return;
            } else {
                boolean started = prepareAsrTrackSource(finalSource);
                if (started) {
                    mToastTextView.setText(getString(R.string.subtitle_asr_track_downloading));
                    mMediaController.showOnce(mToastTextView);
                    return;
                }
                if (mAsrTrackDownloadedFile != null && mAsrTrackDownloadedFile.exists() && mAsrTrackDownloadedFile.length() > 0) {
                    finalSource = mAsrTrackDownloadedFile.getAbsolutePath();
                } else {
                    mAsrEnabled = false;
                    mToastTextView.setText(getString(R.string.subtitle_asr_track_download_failed));
                    mMediaController.showOnce(mToastTextView);
                    invalidateOptionsMenu();
                    return;
                }
            }
        }

        int startPlayerMs = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
        if (mAsrTrackDecoder == null) {
            mAsrTrackDecoder = new AsrAudioTrackDecoder();
        }

        mToastTextView.setText(getString(R.string.subtitle_asr_whisper_ready));
        mMediaController.showOnce(mToastTextView);

        mAsrTrackDecoder.start(this, finalSource, startPlayerMs, new AsrAudioTrackDecoder.Listener() {
            @Override
            public void onPcmChunk(byte[] pcmData, int startMs, int endMs, int sampleRate, int channelCount, String audioFormat) {
                handleTrackPcmChunk(pcmData, startMs, endMs, sampleRate, channelCount, audioFormat);
            }

            @Override
            public void onStopped() {
                mSubtitleHandler.post(() -> {
                    flushPendingTrackChunkFromDecoderStop();
                    if (mAsrEnabled) {
                        mToastTextView.setText(getString(R.string.subtitle_asr_track_stop));
                        mMediaController.showOnce(mToastTextView);
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                mSubtitleHandler.post(() -> {
                    if (mAsrEnabled) {
                        mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
                        mMediaController.showOnce(mToastTextView);
                    }
                });
            }
        });
    }

    private void stopTrackAsr() {
        AsrAudioTrackDecoder d = mAsrTrackDecoder;
        if (d != null) {
            try {
                d.stop();
            } catch (Throwable ignored) {
            }
        }
        mAsrTrackUseWhisper = false;
        mAsrWhisperModelPath = null;
        synchronized (mAsrTrackChunkBuffer) {
            if (mAsrTrackChunkBytes > 0 && mAsrTrackChunkStartMs >= 0) {
                flushTrackChunkLocked(mAsrTrackChunkStartMs + 1500, 0, 0);
            } else {
                mAsrTrackChunkBuffer.reset();
                mAsrTrackChunkBytes = 0;
                mAsrTrackChunkStartMs = -1;
                mAsrTrackChunkHasVoice = false;
                mAsrTrackLastVoiceMs = -1;
                mAsrTrackLastEndMs = -1;
            }
        }
    }

    private void handleTrackPcmChunk(byte[] pcmData, int startMs, int endMs, int sampleRate, int channelCount, String audioFormat) {
        if (!mAsrEnabled || pcmData == null || pcmData.length <= 0) {
            return;
        }
        int sr = Math.max(1, sampleRate);
        int ch = Math.max(1, channelCount);
        int bytesPerSecond = sr * 2 * ch;
        int minChunkMs = 900;
        int maxChunkMs = 1800;
        int overlapMs = 450;
        int minChunkBytes = bytesPerSecond * minChunkMs / 1000;
        int maxChunkBytes = bytesPerSecond * maxChunkMs / 1000;
        int overlapBytes = bytesPerSecond * overlapMs / 1000;
        int silenceFlushMs = 650;

        long avgAbs = avgAbsPcm16le(pcmData, pcmData.length, ch);
        boolean voice = avgAbs >= 200;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - mAsrTrackLastVoiceDebugAtMs >= 1000) {
            mAsrTrackLastVoiceDebugAtMs = now;
            Log.d(TAG, "ASR(track) pcm voice=" + voice + " avg=" + avgAbs + " sr=" + sr + " ch=" + ch + " startMs=" + startMs + " endMs=" + endMs + " bytes=" + pcmData.length);
        }
        synchronized (mAsrTrackChunkBuffer) {
            mAsrTrackSampleRate = sr;
            mAsrTrackChannelCount = ch;
            mAsrTrackAudioFormat = TextUtils.isEmpty(audioFormat) ? "pcm_s16le" : audioFormat;
            mAsrTrackLastEndMs = Math.max(mAsrTrackLastEndMs, endMs);

            if (mAsrTrackChunkStartMs < 0) {
                mAsrTrackChunkStartMs = Math.max(0, startMs);
            }
            try {
                mAsrTrackChunkBuffer.write(pcmData, 0, pcmData.length);
                mAsrTrackChunkBytes += pcmData.length;
            } catch (Throwable ignored) {
            }
            if (voice) {
                mAsrTrackChunkHasVoice = true;
                mAsrTrackLastVoiceMs = endMs;
            }

            int durationMs = Math.max(0, endMs - mAsrTrackChunkStartMs);
            if (mAsrTrackChunkBytes >= maxChunkBytes || durationMs >= maxChunkMs) {
                flushTrackChunkLocked(endMs, overlapMs, overlapBytes);
            } else if (mAsrTrackChunkHasVoice && mAsrTrackChunkBytes >= minChunkBytes && mAsrTrackLastVoiceMs > 0 && (endMs - mAsrTrackLastVoiceMs) >= silenceFlushMs) {
                flushTrackChunkLocked(endMs, overlapMs, overlapBytes);
            }
        }
    }

    private void flushPendingTrackChunkFromDecoderStop() {
        synchronized (mAsrTrackChunkBuffer) {
            if (mAsrTrackChunkBytes > 0 && mAsrTrackChunkStartMs >= 0) {
                int endMs = mAsrTrackLastEndMs > 0 ? mAsrTrackLastEndMs : (mAsrTrackChunkStartMs + 1500);
                flushTrackChunkLocked(endMs, 0, 0);
            }
        }
    }

    private void flushTrackChunkLocked(int endMs, int overlapMs, int overlapBytes) {
        if (mAsrTrackChunkBytes <= 0 || mAsrTrackChunkStartMs < 0) {
            return;
        }
        if (!mAsrTrackChunkHasVoice) {
            mAsrTrackChunkBuffer.reset();
            mAsrTrackChunkBytes = 0;
            mAsrTrackChunkStartMs = -1;
            mAsrTrackLastVoiceMs = -1;
            return;
        }

        final byte[] pcm = mAsrTrackChunkBuffer.toByteArray();
        final int startMs = mAsrTrackChunkStartMs;
        final int safeEndMs = Math.max(startMs + 200, endMs);
        final String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        final String lang = mSettings != null ? mSettings.getAsrLanguage() : "";
        final int sr = mAsrTrackSampleRate;
        final int ch = mAsrTrackChannelCount;
        final String fmt = mAsrTrackAudioFormat;

        Log.i(TAG, "ASR(track) flush startMs=" + startMs + " endMs=" + safeEndMs + " bytes=" + pcm.length + " sr=" + sr + " ch=" + ch + " whisper=" + mAsrTrackUseWhisper);

        if (overlapBytes > 0 && pcm.length > overlapBytes) {
            byte[] tail = Arrays.copyOfRange(pcm, pcm.length - overlapBytes, pcm.length);
            mAsrTrackChunkBuffer.reset();
            try {
                mAsrTrackChunkBuffer.write(tail, 0, tail.length);
                mAsrTrackChunkBytes = tail.length;
                mAsrTrackChunkStartMs = Math.max(0, safeEndMs - overlapMs);
            } catch (Throwable ignored) {
                mAsrTrackChunkBytes = 0;
                mAsrTrackChunkStartMs = -1;
            }
            mAsrTrackChunkHasVoice = false;
            mAsrTrackLastVoiceMs = -1;
        } else {
            mAsrTrackChunkBuffer.reset();
            mAsrTrackChunkBytes = 0;
            mAsrTrackChunkStartMs = -1;
            mAsrTrackChunkHasVoice = false;
            mAsrTrackLastVoiceMs = -1;
        }

        mSubtitleHandler.post(() -> {
            if (mAsrEnabled) {
                mToastTextView.setText(getString(R.string.subtitle_asr_remote_uploading));
                mMediaController.showOnce(mToastTextView);
            }
        });

        ExecutorService ex = mAsrRemoteExecutor;
        if (ex == null) {
            ex = Executors.newSingleThreadExecutor();
            mAsrRemoteExecutor = ex;
        }
        ex.execute(() -> {
            try {
                RemoteAsrClient.Result res;
                if (mAsrTrackUseWhisper) {
                    String json = WhisperAsrEngine.transcribePcm16Json(pcm, sr, ch, startMs, safeEndMs, lang);
                    res = parseRemoteAsrResultJson(json, startMs, safeEndMs);
                } else {
                    res = mRemoteAsrClient.transcribePcm(endpoint, pcm, startMs, safeEndMs, sr, ch, fmt, lang);
                }
                List<RemoteAsrClient.Segment> segs = res != null ? res.segments : null;
                boolean partial = res != null && res.partial;

                long now = android.os.SystemClock.elapsedRealtime();
                if (now - mAsrTrackLastResultDebugAtMs >= 1000) {
                    mAsrTrackLastResultDebugAtMs = now;
                    int n = segs != null ? segs.size() : 0;
                    String t = n > 0 ? joinSegmentsText(segs) : "";
                    if (t != null && t.length() > 240) {
                        t = t.substring(0, 240);
                    }
                    Log.i(TAG, "ASR(track) result partial=" + partial + " segs=" + n + " text=" + t);
                }

                if (segs == null || segs.isEmpty()) {
                    if (!partial) {
                        mSubtitleHandler.post(() -> {
                            mAsrPartialText = null;
                            updateSubtitleOverlay();
                        });
                    }
                    return;
                }
                mSubtitleHandler.post(() -> {
                    if (!mAsrEnabled) {
                        return;
                    }
                    if (partial) {
                        String t = joinSegmentsText(segs);
                        mAsrPartialText = TextUtils.isEmpty(t) ? null : t;
                        updateSubtitleOverlay();
                        return;
                    }
                    mAsrPartialText = null;
                    for (RemoteAsrClient.Segment s : segs) {
                        if (s == null || TextUtils.isEmpty(s.text)) {
                            continue;
                        }
                        Log.d(TAG, "ASR(track) commit " + s.startMs + "-" + s.endMs + " " + s.text);
                        commitRemoteFinalText(s.startMs, s.endMs, s.text);
                    }
                    updateSubtitleOverlay();
                });
            } catch (Throwable t) {
                mSubtitleHandler.post(() -> {
                    if (mAsrEnabled) {
                        mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
                        mMediaController.showOnce(mToastTextView);
                    }
                });
            }
        });
    }

    private long avgAbsPcm16le(byte[] pcm, int length, int channelCount) {
        if (pcm == null || length <= 2) {
            return 0;
        }
        int ch = Math.max(1, channelCount);
        int sampleCount = length / 2;
        int frameCount = sampleCount / ch;
        if (frameCount <= 0) {
            return 0;
        }
        long sum = 0;
        int step = Math.max(1, frameCount / 160);
        int picked = 0;
        for (int f = 0; f < frameCount; f += step) {
            long frameAbs = 0;
            int base = f * ch * 2;
            for (int c = 0; c < ch; c++) {
                int i = base + c * 2;
                if (i + 1 >= length) {
                    break;
                }
                int lo = pcm[i] & 0xff;
                int hi = pcm[i + 1];
                short v = (short) ((hi << 8) | lo);
                frameAbs += Math.abs((int) v);
            }
            sum += (frameAbs / ch);
            picked++;
        }
        return picked > 0 ? (sum / picked) : 0;
    }

    private RemoteAsrClient.Result parseRemoteAsrResultJson(String json, int startMs, int endMs) {
        try {
            if (TextUtils.isEmpty(json)) {
                return new RemoteAsrClient.Result(false, new ArrayList<>());
            }
            org.json.JSONObject obj = new org.json.JSONObject(json);
            boolean partial = obj.optBoolean("partial", false);
            org.json.JSONArray segArr = obj.optJSONArray("segments");
            ArrayList<RemoteAsrClient.Segment> segs = new ArrayList<>();
            if (segArr != null) {
                for (int i = 0; i < segArr.length(); i++) {
                    org.json.JSONObject s = segArr.optJSONObject(i);
                    if (s == null) {
                        continue;
                    }
                    int s0 = s.optInt("startMs", startMs);
                    int s1 = s.optInt("endMs", endMs);
                    String t = s.optString("text", "");
                    if (!TextUtils.isEmpty(t)) {
                        segs.add(new RemoteAsrClient.Segment(s0, s1, t));
                    }
                }
                return new RemoteAsrClient.Result(partial, segs);
            }
            String text = obj.optString("text", "");
            if (!TextUtils.isEmpty(text)) {
                segs.add(new RemoteAsrClient.Segment(startMs, endMs, text));
            }
            return new RemoteAsrClient.Result(partial, segs);
        } catch (Throwable ignored) {
            return new RemoteAsrClient.Result(false, new ArrayList<>());
        }
    }

    private void remoteAsrLoop(int sampleRate) {
        int bytesPerSecond = sampleRate * 2;
        int minChunkMs = 900;
        int maxChunkMs = 1800;
        int overlapMs = 450;
        int minChunkBytes = bytesPerSecond * minChunkMs / 1000;
        int maxChunkBytes = bytesPerSecond * maxChunkMs / 1000;
        int overlapBytes = bytesPerSecond * overlapMs / 1000;
        int silenceFlushMs = 650;
        byte[] buf = new byte[8 * 1024];
        while (mAsrRemoteRunning) {
            AudioRecord r = mAsrAudioRecord;
            if (r == null) {
                break;
            }
            int n;
            try {
                n = r.read(buf, 0, buf.length);
            } catch (Throwable t) {
                break;
            }
            if (n <= 0) {
                continue;
            }
            boolean voice = isVoicePcm16leMono(buf, n);
            long now = System.currentTimeMillis();
            if (voice) {
                mAsrRemoteLastVoiceAtMs = now;
            }
            if (mAsrRemoteChunkStartPlayerMs < 0) {
                mAsrRemoteChunkStartPlayerMs = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
            }
            synchronized (mAsrRemoteChunkBuffer) {
                mAsrRemoteChunkBuffer.write(buf, 0, n);
                mAsrRemoteChunkBytes += n;
                if (voice) {
                    mAsrRemoteChunkHasVoice = true;
                }
                int currentPlayerMs = mVideoView != null ? mVideoView.getCurrentPosition() : ((int) mAsrRemoteChunkStartPlayerMs + maxChunkMs);
                if (mAsrRemoteChunkBytes >= maxChunkBytes) {
                    flushRemoteAsrChunkLocked(currentPlayerMs, overlapMs, overlapBytes);
                } else if (mAsrRemoteChunkHasVoice && mAsrRemoteChunkBytes >= minChunkBytes && mAsrRemoteLastVoiceAtMs > 0 && (now - mAsrRemoteLastVoiceAtMs) >= silenceFlushMs) {
                    flushRemoteAsrChunkLocked(currentPlayerMs, overlapMs, overlapBytes);
                }
            }
        }
        synchronized (mAsrRemoteChunkBuffer) {
            if (mAsrRemoteChunkBytes > 0) {
                int currentPlayerMs = mVideoView != null ? mVideoView.getCurrentPosition() : ((int) mAsrRemoteChunkStartPlayerMs + 1500);
                flushRemoteAsrChunkLocked(currentPlayerMs, 0, 0);
            }
        }
        mSubtitleHandler.post(() -> {
            if (!mAsrEnabled) {
                return;
            }
            mToastTextView.setText(getString(R.string.subtitle_asr_remote_stop));
            mMediaController.showOnce(mToastTextView);
        });
    }

    private void flushRemoteAsrChunkLocked(int endPlayerMs, int overlapMs, int overlapBytes) {
        if (mAsrRemoteChunkBytes <= 0) {
            return;
        }
        if (!mAsrRemoteChunkHasVoice) {
            mAsrRemoteChunkBuffer.reset();
            mAsrRemoteChunkBytes = 0;
            mAsrRemoteChunkStartPlayerMs = -1;
            return;
        }
        final byte[] pcm = mAsrRemoteChunkBuffer.toByteArray();
        final int startMs = (int) Math.max(0, mAsrRemoteChunkStartPlayerMs);
        final int endMs = Math.max(startMs + 200, endPlayerMs);
        final String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
        final String lang = mSettings != null ? mSettings.getAsrLanguage() : "";

        if (overlapBytes > 0 && pcm.length > overlapBytes) {
            byte[] tail = Arrays.copyOfRange(pcm, pcm.length - overlapBytes, pcm.length);
            mAsrRemoteChunkBuffer.reset();
            mAsrRemoteChunkBuffer.write(tail, 0, tail.length);
            mAsrRemoteChunkBytes = tail.length;
            mAsrRemoteChunkStartPlayerMs = Math.max(0, endMs - overlapMs);
            mAsrRemoteChunkHasVoice = false;
            mAsrRemoteLastVoiceAtMs = -1;
        } else {
            mAsrRemoteChunkBuffer.reset();
            mAsrRemoteChunkBytes = 0;
            mAsrRemoteChunkStartPlayerMs = -1;
            mAsrRemoteChunkHasVoice = false;
            mAsrRemoteLastVoiceAtMs = -1;
        }

        mSubtitleHandler.post(() -> {
            if (mAsrEnabled) {
                mToastTextView.setText(getString(R.string.subtitle_asr_remote_uploading));
                mMediaController.showOnce(mToastTextView);
            }
        });

        ExecutorService ex = mAsrRemoteExecutor;
        if (ex == null) {
            ex = Executors.newSingleThreadExecutor();
            mAsrRemoteExecutor = ex;
        }
        ex.execute(() -> {
            try {
                RemoteAsrClient.Result res = mRemoteAsrClient.transcribePcm16Mono16k(endpoint, pcm, startMs, endMs, lang);
                List<RemoteAsrClient.Segment> segs = res != null ? res.segments : null;
                boolean partial = res != null && res.partial;

                if (segs == null || segs.isEmpty()) {
                    if (!partial) {
                        mSubtitleHandler.post(() -> {
                            mAsrPartialText = null;
                            updateSubtitleOverlay();
                        });
                    }
                    return;
                }

                mSubtitleHandler.post(() -> {
                    if (!mAsrEnabled) {
                        return;
                    }
                    if (partial) {
                        String t = joinSegmentsText(segs);
                        mAsrPartialText = TextUtils.isEmpty(t) ? null : t;
                        updateSubtitleOverlay();
                        return;
                    }
                    mAsrPartialText = null;
                    for (RemoteAsrClient.Segment s : segs) {
                        if (s == null || TextUtils.isEmpty(s.text)) {
                            continue;
                        }
                        commitRemoteFinalText(s.startMs, s.endMs, s.text);
                    }
                    updateSubtitleOverlay();
                });
            } catch (Throwable t) {
                mSubtitleHandler.post(() -> {
                    if (mAsrEnabled) {
                        mToastTextView.setText(getString(R.string.subtitle_asr_remote_failed));
                        mMediaController.showOnce(mToastTextView);
                    }
                });
            }
        });
    }

    private String joinSegmentsText(List<RemoteAsrClient.Segment> segs) {
        if (segs == null || segs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (RemoteAsrClient.Segment s : segs) {
            if (s == null || TextUtils.isEmpty(s.text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s.text.trim());
        }
        return sb.toString().trim();
    }

    private void commitRemoteFinalText(int startMs, int endMs, String rawText) {
        String text = rawText != null ? rawText.trim() : "";
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String delta = removeTextOverlap(mAsrRemoteCommittedTail, text);
        if (TextUtils.isEmpty(delta)) {
            return;
        }
        List<String> parts = splitToSentences(delta);
        if (parts.isEmpty()) {
            return;
        }
        int duration = Math.max(200, endMs - startMs);
        int total = 0;
        for (String p : parts) {
            if (p != null) {
                total += p.length();
            }
        }
        if (total <= 0) {
            addSubtitleCueExplicit(startMs, endMs, delta);
            rememberRemoteTail(text);
            return;
        }
        int used = 0;
        int segStart = startMs;
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            if (TextUtils.isEmpty(p)) {
                continue;
            }
            used += p.length();
            int segEnd = startMs + (int) ((long) duration * used / total);
            segEnd = Math.max(segStart + 200, segEnd);
            if (i == parts.size() - 1) {
                segEnd = endMs;
            } else {
                segEnd = Math.min(endMs, segEnd);
            }
            addSubtitleCueExplicit(segStart, segEnd, p);
            segStart = segEnd;
        }
        rememberRemoteTail(text);
    }

    private void rememberRemoteTail(String fullText) {
        if (TextUtils.isEmpty(fullText)) {
            return;
        }
        String t = fullText.trim();
        int max = 80;
        if (t.length() > max) {
            t = t.substring(t.length() - max);
        }
        mAsrRemoteCommittedTail = t;
    }

    private String removeTextOverlap(String prevTail, String current) {
        if (TextUtils.isEmpty(current)) {
            return "";
        }
        if (TextUtils.isEmpty(prevTail)) {
            return current;
        }
        String a = prevTail;
        String b = current;
        int max = Math.min(60, Math.min(a.length(), b.length()));
        int best = 0;
        for (int len = 1; len <= max; len++) {
            String suf = a.substring(a.length() - len);
            String pre = b.substring(0, len);
            if (suf.equalsIgnoreCase(pre)) {
                best = len;
            }
        }
        if (best >= 3 && best < b.length()) {
            return b.substring(best).trim();
        }
        return b;
    }

    private List<String> splitToSentences(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return out;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            boolean hard = c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?';
            boolean soft = (c == '，' || c == ',' || c == ';' || c == '；') && sb.length() >= 18;
            if (hard || soft) {
                String s = sb.toString().trim();
                if (!TextUtils.isEmpty(s)) {
                    out.add(s);
                }
                sb.setLength(0);
            }
        }
        String rest = sb.toString().trim();
        if (!TextUtils.isEmpty(rest)) {
            out.add(rest);
        }
        return out;
    }

    private boolean isVoicePcm16le(byte[] pcm, int length, int channelCount, int threshold) {
        if (pcm == null || length <= 2) {
            return false;
        }
        int ch = Math.max(1, channelCount);
        int sampleCount = length / 2;
        int frameCount = sampleCount / ch;
        if (frameCount <= 0) {
            return false;
        }
        long sum = 0;
        int step = Math.max(1, frameCount / 160);
        int picked = 0;
        for (int f = 0; f < frameCount; f += step) {
            long frameAbs = 0;
            int base = f * ch * 2;
            for (int c = 0; c < ch; c++) {
                int i = base + c * 2;
                if (i + 1 >= length) {
                    break;
                }
                int lo = pcm[i] & 0xff;
                int hi = pcm[i + 1];
                short v = (short) ((hi << 8) | lo);
                frameAbs += Math.abs((int) v);
            }
            sum += (frameAbs / ch);
            picked++;
        }
        if (picked <= 0) {
            return false;
        }
        long avg = sum / picked;
        return avg >= threshold;
    }

    private boolean isVoicePcm16leMono(byte[] pcm, int length) {
        return isVoicePcm16le(pcm, length, 1, 700);
    }

    private void addSubtitleCueExplicit(int startMs, int endMs, String text) {
        SubtitleCue cue = new SubtitleCue(startMs, endMs, text);
        int insertAt = mSubtitleCues.size();
        for (int i = 0; i < mSubtitleCues.size(); i++) {
            if (startMs < mSubtitleCues.get(i).startMs) {
                insertAt = i;
                break;
            }
        }
        mSubtitleCues.add(insertAt, cue);
        normalizeSubtitleEnds();
    }

    private int queryAsrServiceCount() {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            if (pm == null) {
                return 0;
            }
            java.util.List<android.content.pm.ResolveInfo> infos = pm.queryIntentServices(new Intent("android.speech.RecognitionService"), 0);
            int count = infos != null ? infos.size() : 0;
            mAsrServiceCount = count;
            return count;
        } catch (Throwable ignored) {
            mAsrServiceCount = 0;
            return 0;
        }
    }

    private String errorToText(int error) {
        if (error == SpeechRecognizer.ERROR_AUDIO) return "ERROR_AUDIO";
        if (error == SpeechRecognizer.ERROR_CLIENT) return "ERROR_CLIENT";
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return "ERROR_INSUFFICIENT_PERMISSIONS";
        if (error == SpeechRecognizer.ERROR_NETWORK) return "ERROR_NETWORK";
        if (error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) return "ERROR_NETWORK_TIMEOUT";
        if (error == SpeechRecognizer.ERROR_NO_MATCH) return "ERROR_NO_MATCH";
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return "ERROR_RECOGNIZER_BUSY";
        if (error == SpeechRecognizer.ERROR_SERVER) return "ERROR_SERVER";
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return "ERROR_SPEECH_TIMEOUT";
        return "ERROR_" + error;
    }

    private void handleAsrResults(Bundle bundle, boolean partial) {
        if (!mAsrEnabled) {
            return;
        }
        try {
            java.util.ArrayList<String> list = bundle != null ? bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
            String text = (list != null && !list.isEmpty()) ? list.get(0) : null;
            if (TextUtils.isEmpty(text)) {
                if (partial) {
                    mAsrPartialText = null;
                    updateSubtitleOverlay();
                }
                return;
            }
            text = text.trim();
            if (TextUtils.isEmpty(text)) {
                return;
            }

            if (partial) {
                mAsrPartialText = text;
                updateSubtitleOverlay();
                return;
            }

            long now = System.currentTimeMillis();
            if (text.equals(mAsrLastFinalText) && (now - mAsrLastFinalAtMs) < 800) {
                mAsrPartialText = null;
                updateSubtitleOverlay();
                return;
            }
            mAsrLastFinalText = text;
            mAsrLastFinalAtMs = now;
            mAsrPartialText = null;

            int startMs = mVideoView != null ? mVideoView.getCurrentPosition() : 0;
            addSubtitleCue(startMs, text);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem orientation = menu != null ? menu.findItem(R.id.action_toggle_orientation) : null;
        if (orientation != null) {
            int current = mSettings != null ? mSettings.getPlayerOrientation() : Settings.ORIENTATION__Auto;
            orientation.setTitle(getString(R.string.toggle_orientation) + ": " + getOrientationText(current));
        }
        MenuItem mirror = menu != null ? menu.findItem(R.id.action_toggle_mirror) : null;
        if (mirror != null) {
            mirror.setChecked(mSettings.getVideoMirrorHorizontal());
        }
        MenuItem asr = menu != null ? menu.findItem(R.id.action_subtitle_asr_toggle) : null;
        if (asr != null) {
            asr.setChecked(mAsrEnabled);
        }
        MenuItem loop = menu != null ? menu.findItem(R.id.action_toggle_loop) : null;
        if (loop != null) {
            loop.setChecked(mLoopEnabled);
        }
        // Sync filter check state
        int[] filterIds = {
                R.id.action_filter_none, R.id.action_filter_grayscale,
                R.id.action_filter_hflip, R.id.action_filter_vflip,
                R.id.action_filter_blur, R.id.action_filter_dark, R.id.action_filter_rotate90,
                R.id.action_filter_warm, R.id.action_filter_cool, R.id.action_filter_sharpen
        };
        int[] filterTypes = {
                IjkVideoView.RENDER_FILTER_NONE, IjkVideoView.RENDER_FILTER_GRAYSCALE,
                IjkVideoView.RENDER_FILTER_HFLIP, IjkVideoView.RENDER_FILTER_VFLIP,
                IjkVideoView.RENDER_FILTER_BRIGHT, IjkVideoView.RENDER_FILTER_DARK, IjkVideoView.RENDER_FILTER_ROTATE90,
                IjkVideoView.RENDER_FILTER_WARM, IjkVideoView.RENDER_FILTER_COOL, IjkVideoView.RENDER_FILTER_SHARPEN
        };
        if (menu != null) {
            for (int i = 0; i < filterIds.length; i++) {
                MenuItem fi = menu.findItem(filterIds[i]);
                if (fi != null) {
                    fi.setChecked(mCurrentFilterType == filterTypes[i]);
                }
            }
        }
        // Sync FFmpeg vf0 filter check state; disable items when not using Ijk backend
        int[] vf0MenuIds = {
                R.id.action_filter_ffmpeg_hflip, R.id.action_filter_ffmpeg_vflip,
                R.id.action_filter_ffmpeg_gblur,
                R.id.action_filter_ffmpeg_eq_bright, R.id.action_filter_ffmpeg_eq_dark,
                R.id.action_filter_ffmpeg_sharpen,
                R.id.action_filter_ffmpeg_custom
        };
        String[] vf0Values = {
                "hflip", "vflip",
                "gblur=sigma=5",
                PlayerFactory.buildCurvesVf0("lighter"),
                PlayerFactory.buildCurvesVf0("darker"),
                "unsharp=luma_msize_x=5:luma_msize_y=5:luma_amount=1.5",
                null  // custom — no fixed value to match
        };
        boolean isIjkActive = mVideoView != null && mVideoView.isActivePlayerIjk();
        if (menu != null) {
            for (int i = 0; i < vf0MenuIds.length; i++) {
                MenuItem fi = menu.findItem(vf0MenuIds[i]);
                if (fi != null) {
                    fi.setEnabled(isIjkActive);
                    if (vf0Values[i] != null) {
                        fi.setChecked(isIjkActive && vf0Values[i].equals(mCurrentVf0Filter));
                    }
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted && mAsrPendingStart) {
                mAsrPendingStart = false;
                startAsrByMode();
            } else if (!granted) {
                mAsrPendingStart = false;
                mAsrEnabled = false;
                mAsrPartialText = null;
                updateSubtitleOverlay();
                mToastTextView.setText(getString(R.string.subtitle_asr_permission_denied));
                mMediaController.showOnce(mToastTextView);
                invalidateOptionsMenu();
            }
        }
    }

    private void showDiagnosticsSheet() {
        String summary = buildDiagnosticsSummary();
        String logs = buildDiagnosticsLogs();
        DiagnosticsBottomSheetDialogFragment.newInstance(summary, logs)
                .show(getSupportFragmentManager(), "diagnostics_sheet");
    }

    private String buildDiagnosticsSummary() {
        StringBuilder sb = new StringBuilder();
        String source = mVideoView != null ? mVideoView.getDataSource() : null;
        if (TextUtils.isEmpty(source)) {
            source = mVideoPath != null ? mVideoPath : (mVideoUri != null ? String.valueOf(mVideoUri) : "");
        }

        sb.append(getString(R.string.diagnostics_title)).append('\n');
        sb.append("source=").append(source).append('\n');
        sb.append("pref.player=").append(mSettings != null ? mSettings.getPlayer() : -1).append('\n');
        sb.append("orientation=").append(getOrientationText(mSettings != null ? mSettings.getPlayerOrientation() : Settings.ORIENTATION__Auto)).append('\n');
        sb.append("render=").append(mVideoView != null ? IjkVideoView.getRenderText(this, mVideoView.getRender()) : "").append('\n');
        sb.append("ratio=").append(MeasureHelper.getAspectRatioText(this, mVideoView != null ? mVideoView.getCurrentAspectRatio() : IRenderView.AR_ASPECT_FIT_PARENT)).append('\n');
        sb.append("mirror=").append(mSettings != null && mSettings.getVideoMirrorHorizontal()).append('\n');
        sb.append("deviceVulkan=").append(mVideoView != null && mVideoView.isDeviceSupportsVulkan()).append('\n');

        String vf0 = mVideoView != null ? mVideoView.getVideoFilterVf0() : null;
        sb.append("vf0=").append(TextUtils.isEmpty(vf0) ? "null" : ("len=" + vf0.length())).append('\n');

        if (mVideoView != null) {
            int fw = mVideoView.getLastErrorFramework();
            int impl = mVideoView.getLastErrorImpl();
            if (fw != 0 || impl != 0) {
                sb.append("lastError=").append(fw).append(',').append(impl).append(" timeMs=").append(mVideoView.getLastErrorTimeMs()).append('\n');
            }
        }

        String lastCreate = findLastLogLineContains("createPlayer:");
        if (!TextUtils.isEmpty(lastCreate)) {
            sb.append(lastCreate).append('\n');
        }
        String lastApplyVf0 = findLastLogLineContains("PlayerFactory.configure: apply vf0=");
        if (!TextUtils.isEmpty(lastApplyVf0)) {
            sb.append(lastApplyVf0).append('\n');
        }
        appendApkNativeLibInfo(sb);
        appendNativeCapabilities(sb);
        appendAsrCapabilities(sb);
        return sb.toString();
    }

    private void appendApkNativeLibInfo(StringBuilder sb) {
        try {
            String dir = getApplicationInfo() != null ? getApplicationInfo().nativeLibraryDir : null;
            sb.append("apk.nativeLibDir=").append(TextUtils.isEmpty(dir) ? "null" : dir).append('\n');

            File nativeDir = !TextUtils.isEmpty(dir) ? new File(dir) : null;
            appendNativeLibFileInfo(sb, nativeDir, "libijkffmpeg.so");
            appendNativeLibFileInfo(sb, nativeDir, "libijkplayer.so");
        } catch (Throwable ignored) {
        }
    }

    private void appendNativeLibFileInfo(StringBuilder sb, File nativeDir, String name) {
        try {
            if (nativeDir == null || TextUtils.isEmpty(name)) {
                return;
            }
            File f = new File(nativeDir, name);
            if (!f.exists()) {
                sb.append("apk.").append(name).append("=missing").append('\n');
                return;
            }
            sb.append("apk.").append(name).append(".size=").append(f.length()).append('\n');
            String sha1 = sha1Hex(f);
            if (!TextUtils.isEmpty(sha1)) {
                sb.append("apk.").append(name).append(".sha1=").append(sha1).append('\n');
            }
        } catch (Throwable ignored) {
        }
    }

    private String sha1Hex(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            byte[] b = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte v : b) {
                sb.append(String.format(Locale.US, "%02x", v));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void appendAsrCapabilities(StringBuilder sb) {
        try {
            boolean availableFlag = SpeechRecognizer.isRecognitionAvailable(this);
            int services = queryAsrServiceCount();
            String service = android.provider.Settings.Secure.getString(getContentResolver(), "voice_recognition_service");
            sb.append("asr.availableFlag=").append(availableFlag).append(" services=").append(services).append('\n');
            sb.append("asr.voiceService=").append(TextUtils.isEmpty(service) ? "null" : service).append('\n');
            sb.append("asr.enabled=").append(mAsrEnabled).append('\n');
            String mode = mSettings != null ? mSettings.getAsrMode() : "system";
            String endpoint = mSettings != null ? mSettings.getAsrRemoteEndpoint() : "";
            sb.append("asr.mode=").append(TextUtils.isEmpty(mode) ? "system" : mode).append('\n');
            sb.append("asr.remote.endpointConfigured=").append(!TextUtils.isEmpty(endpoint)).append('\n');
        } catch (Throwable ignored) {
        }
    }

    private void appendNativeCapabilities(StringBuilder sb) {
        try {
            String json = NativeFFmpegDiagnostics.getCapabilitiesJsonOrNull();
            if (TextUtils.isEmpty(json)) {
                return;
            }
            org.json.JSONObject obj = new org.json.JSONObject(json);

            String cfg = obj.optString("avformat_configuration", "");
            boolean openssl = cfg.contains("enable-openssl");
            boolean gnutls = cfg.contains("enable-gnutls");

            org.json.JSONArray in = obj.optJSONArray("protocols_in");
            boolean http = containsString(in, "http");
            boolean https = containsString(in, "https");
            boolean tls = containsString(in, "tls");

            org.json.JSONObject filters = obj.optJSONObject("filter_presence");
            boolean drawbox = filters != null && filters.optBoolean("drawbox", false);
            boolean scaleVulkan = filters != null && filters.optBoolean("scale_vulkan", false);
            boolean hflipVulkan = filters != null && filters.optBoolean("hflip_vulkan", false);
            boolean vflipVulkan = filters != null && filters.optBoolean("vflip_vulkan", false);
            boolean transposeVulkan = filters != null && filters.optBoolean("transpose_vulkan", false);

            sb.append("ffmpeg.openssl=").append(openssl).append(" gnutls=").append(gnutls).append('\n');
            sb.append("ffmpeg.protocols.http=").append(http).append(" https=").append(https).append(" tls=").append(tls).append('\n');
            sb.append("ffmpeg.filters.drawbox=").append(drawbox).append('\n');
            sb.append("ffmpeg.filters.vulkan=").append(scaleVulkan || hflipVulkan || vflipVulkan || transposeVulkan).append('\n');
            if (!TextUtils.isEmpty(cfg)) {
                sb.append("ffmpeg.avformat_configuration=").append(cfg).append('\n');
            }
            sb.append("ffmpeg.nativeDiag=").append(NativeFFmpegDiagnostics.isDiagnosticsEnabledSafe()).append('\n');
        } catch (Throwable ignored) {
        }
    }

    private boolean containsString(org.json.JSONArray arr, String value) {
        if (arr == null || TextUtils.isEmpty(value)) {
            return false;
        }
        for (int i = 0; i < arr.length(); i++) {
            if (value.equalsIgnoreCase(arr.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private String buildDiagnosticsLogs() {
        StringBuilder sb = new StringBuilder();
        for (String line : DebugEventLog.tail(200)) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String findLastLogLineContains(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return null;
        }
        java.util.List<String> lines = DebugEventLog.tail(200);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line != null && line.contains(keyword)) {
                return line;
            }
        }
        return null;
    }

    private int nextOrientation(int current) {
        if (current == Settings.ORIENTATION__Auto) {
            return Settings.ORIENTATION__Landscape;
        }
        if (current == Settings.ORIENTATION__Landscape) {
            return Settings.ORIENTATION__Portrait;
        }
        return Settings.ORIENTATION__Auto;
    }

    private String getOrientationText(int orientation) {
        if (orientation == Settings.ORIENTATION__Landscape) {
            return getString(R.string.orientation_landscape);
        }
        if (orientation == Settings.ORIENTATION__Portrait) {
            return getString(R.string.orientation_portrait);
        }
        return getString(R.string.orientation_auto);
    }

    private void applyPlayerOrientation(int orientation) {
        int requested;
        if (orientation == Settings.ORIENTATION__Landscape) {
            requested = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        } else if (orientation == Settings.ORIENTATION__Portrait) {
            requested = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else {
            requested = ActivityInfo.SCREEN_ORIENTATION_USER;
        }
        DebugEventLog.add("VideoActivity: applyOrientation=" + orientation + ", requested=" + requested);
        setRequestedOrientation(requested);
    }

    private void takeSnapshot() {
        mToastTextView.setText(getString(R.string.snapshot_saving));
        mMediaController.showOnce(mToastTextView);

        mVideoView.captureFrameBitmapAsync(bitmap -> {
            if (bitmap == null) {
                mToastTextView.setText(getString(R.string.snapshot_failed));
                mMediaController.showOnce(mToastTextView);
                return;
            }

            String location = saveBitmapToGallery(bitmap);
            try {
                bitmap.recycle();
            } catch (Throwable ignored) {
            }

            if (!TextUtils.isEmpty(location)) {
                mToastTextView.setText(getString(R.string.snapshot_saved));
            } else {
                mToastTextView.setText(getString(R.string.snapshot_failed));
            }
            mMediaController.showOnce(mToastTextView);
        });
    }

    private String saveBitmapToGallery(Bitmap bitmap) {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "snapshot_" + time + ".png";
        try {
            ContentResolver resolver = getContentResolver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "IJKPlayer");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                OutputStream os = resolver.openOutputStream(uri);
                if (os == null) {
                    return null;
                }
                boolean ok;
                try {
                    ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                } finally {
                    os.close();
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return ok ? uri.toString() : null;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "IJKPlayer");
                if (!dir.exists() && !dir.mkdirs()) {
                    return null;
                }
                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                boolean ok;
                try {
                    ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                } finally {
                    fos.close();
                }
                if (!ok) {
                    return null;
                }
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
                return file.getAbsolutePath();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        if (mVideoView == null)
            return null;

        return mVideoView.getTrackInfo();
    }

    @Override
    public void selectTrack(int stream) {
        mVideoView.selectTrack(stream);
    }

    @Override
    public void deselectTrack(int stream) {
        mVideoView.deselectTrack(stream);
    }

    @Override
    public int getSelectedTrack(int trackType) {
        if (mVideoView == null)
            return -1;

        return mVideoView.getSelectedTrack(trackType);
    }
}
