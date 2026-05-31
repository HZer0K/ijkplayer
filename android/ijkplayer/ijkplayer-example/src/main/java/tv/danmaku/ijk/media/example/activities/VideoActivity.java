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
import android.media.AudioManager;
import android.media.MediaScannerConnection;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
import tv.danmaku.ijk.media.example.util.AiHelper;
import tv.danmaku.ijk.media.example.util.DiagnosticsHelper;
import tv.danmaku.ijk.media.example.util.NativeFFmpegDiagnostics;
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
    private TextView mGestureOverlay;
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

    private AiHelper mAiHelper;

    private final AiHelper.Callback mAiCallback = new AiHelper.Callback() {
        @Override
        public void onAiPartialText(String text) {
            updateSubtitleOverlay();
        }

        @Override
        public void showToastText(String text) {
            if (mToastTextView != null && mMediaController != null) {
                mToastTextView.setText(text);
                mMediaController.showOnce(mToastTextView);
            }
        }

        @Override
        public void invalidateOptionsMenu() {
            VideoActivity.this.invalidateOptionsMenu();
        }

        @Override
        public String getAiPartialText() {
            return mAiHelper != null && mAiHelper.isEnabled() ? mAiHelper.getPartialText() : null;
        }
    };

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
        mAiHelper = new AiHelper(this, mSettings, mAiCallback);
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
        // Add status bar height as top padding so Toolbar is not obscured by system bars
        int statusBarHeight = getStatusBarHeight();
        if (statusBarHeight > 0) {
            toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    toolbar.getPaddingTop() + statusBarHeight,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom());
        }

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mToastTextView = (TextView) findViewById(R.id.toast_text_view);
        mHudView = (TableLayout) findViewById(R.id.hud_view);
        mSubtitleOverlay = (TextView) findViewById(R.id.subtitle_overlay);
        // Subtitle overlay styling: shadow for readability
        mSubtitleOverlay.setShadowLayer(6f, 1f, 1f, android.graphics.Color.BLACK);
        // Create floating gesture feedback overlay (center screen)
        mGestureOverlay = new TextView(this);
        mGestureOverlay.setTextSize(28);
        mGestureOverlay.setTextColor(android.graphics.Color.WHITE);
        mGestureOverlay.setGravity(android.view.Gravity.CENTER);
        mGestureOverlay.setVisibility(View.GONE);
        mGestureOverlay.setBackgroundResource(android.R.drawable.toast_frame);
        int gp = (int) (20 * getResources().getDisplayMetrics().density);
        mGestureOverlay.setPadding(gp, gp / 2, gp, gp / 2);
        FrameLayout root = (FrameLayout) findViewById(android.R.id.content).getRootView();
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        glp.gravity = android.view.Gravity.CENTER;
        root.addView(mGestureOverlay, glp);
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
        if (mAiHelper != null) {
            mAiHelper.stop();
        }
        if (mVulkanDownloadReceiver != null) {
            try {
                unregisterReceiver(mVulkanDownloadReceiver);
            } catch (Throwable ignored) {
                Log.w(TAG, "unregisterReceiver failed", ignored);
            }
            mVulkanDownloadReceiver = null;
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
                        String seekText;
                        if (diffSec >= 0) {
                            seekText = getString(R.string.seek_forward_hint, diffSec);
                        } else {
                            seekText = getString(R.string.seek_backward_hint, -diffSec);
                        }
                        // Show time preview on floating overlay
                        String timePreview = formatSrtTime((int) mSeekGestureTargetMs) + " / " + formatSrtTime((int) duration);
                        mGestureOverlay.setVisibility(View.VISIBLE);
                        mGestureOverlay.setText(seekText + "\n" + timePreview);
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
                            mGestureOverlay.setVisibility(View.VISIBLE);
                            mGestureOverlay.setText(getString(R.string.brightness_label) + ": " + pct + "%");
                        } else {
                            int max = mGestureMaxVolume > 0 ? mGestureMaxVolume : 15;
                            int newVol = Math.max(0, Math.min(max, (int) Math.round(mGestureStartVolume + fraction * max)));
                            AudioManager audioMgr = (AudioManager) getSystemService(AUDIO_SERVICE);
                            if (audioMgr != null) {
                                audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                            }
                            int pct = max > 0 ? (int) (newVol * 100.0f / max) : 0;
                            mGestureOverlay.setVisibility(View.VISIBLE);
                            mGestureOverlay.setText(getString(R.string.volume_label) + ": " + pct + "%");
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
                // Hide gesture overlay
                mGestureOverlay.setVisibility(View.GONE);

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

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            return getResources().getDimensionPixelSize(id);
        }
        // Fallback: estimate 24dp for status bar
        return (int) (24 * getResources().getDisplayMetrics().density);
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
                Log.w(TAG, "show toast after vulkan download failed", ignored);
            }
        }
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
            Log.w(TAG, "clipboard access failed", ignored);
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
        // Release AI engine
        if (mAiHelper != null) {
            mAiHelper.stop();
        }
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSubtitleHandler.removeCallbacks(mSubtitleTick);
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
            String summary = DiagnosticsHelper.buildSummary(this, mVideoView, mVideoPath, mVideoUri, mSettings);
            String logs = DiagnosticsHelper.buildLogs();
            DiagnosticsBottomSheetDialogFragment.newInstance(summary, logs)
                    .show(getSupportFragmentManager(), "diagnostics_sheet");
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
        } else if (id == R.id.action_subtitle_clear) {
            mSubtitleCues.clear();
            updateSubtitleOverlay();
            mToastTextView.setText(getString(R.string.subtitle_clear));
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_subtitle_export) {
            exportSubtitlesSrt();
            return true;
        } else if (id == R.id.action_ai_llm_toggle) {
            if (mAiHelper != null) {
                mAiHelper.toggleAi(this);
            }
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
        // Check AI partial text first (highest priority)
        String aiText = mAiHelper != null && mAiHelper.isEnabled()
                ? mAiHelper.getPartialText() : null;
        if (!TextUtils.isEmpty(aiText)) {
            mSubtitleOverlay.setText(aiText);
            mSubtitleOverlay.setVisibility(View.VISIBLE);
            return;
        }
        // Fall back to manual subtitle cues
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
            Log.w(TAG, "writeSrtToDownloads failed", ignored);
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
        MenuItem aiLlm = menu != null ? menu.findItem(R.id.action_ai_llm_toggle) : null;
        if (aiLlm != null) {
            aiLlm.setChecked(mAiHelper != null && mAiHelper.isEnabled());
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
                Log.w(TAG, "bitmap.recycle failed", ignored);
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
