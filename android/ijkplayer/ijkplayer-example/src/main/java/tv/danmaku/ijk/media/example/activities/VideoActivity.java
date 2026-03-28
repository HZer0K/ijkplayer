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
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.widget.Toast;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.content.RecentMediaStorage;
import tv.danmaku.ijk.media.example.fragments.DiagnosticsBottomSheetDialogFragment;
import tv.danmaku.ijk.media.example.fragments.TracksFragment;
import tv.danmaku.ijk.media.example.player.MediaSourceUtil;
import tv.danmaku.ijk.media.example.player.PlayerToggle;
import tv.danmaku.ijk.media.example.util.DebugEventLog;
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
            String support = mVideoView.isDeviceSupportsVulkan() ? getString(R.string.supported) : getString(R.string.unsupported);
            mToastTextView.setText(getString(R.string.vulkan_demo_enabled_detail, support));
            mMediaController.showOnce(mToastTextView);
        } else {
            DebugEventLog.add("VideoActivity: vulkanDemo=false, clear vf0");
            mVideoView.setVideoFilterVf0(null);
        }
        installEdgeBackHelper();
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
        if (mVulkanDownloadReceiver != null) {
            try {
                unregisterReceiver(mVulkanDownloadReceiver);
            } catch (Throwable ignored) {
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
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mEdgeBackActive = false;
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
                Toast.makeText(this, "请输入直接视频链接（mp4/m3u8等），当前为网页地址", Toast.LENGTH_SHORT).show();
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

    private void rebuildAndPlayCurrent() {
        String source = getCurrentSource();
        if (TextUtils.isEmpty(source))
            return;

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

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.open_url))
                .setView(input)
                .setPositiveButton("播放", (d, which) -> {
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
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        IjkMediaPlayer.native_profileEnd();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open_url) {
            showOpenUrlDialog();
            return true;
        } else if (id == R.id.action_toggle_orientation) {
            int next = nextOrientation(mSettings.getPlayerOrientation());
            mSettings.setPlayerOrientation(next);
            applyPlayerOrientation(next);
            mToastTextView.setText(getString(R.string.toggle_orientation) + ": " + getOrientationText(next));
            mMediaController.showOnce(mToastTextView);
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_demo_hls) {
            playUrl("https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8");
            return true;
        } else if (id == R.id.action_demo_mp4) {
            playUrl("https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4");
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
            mToastTextView.setText(String.format(Locale.US, "%s: %.1fx", getString(R.string.playback_speed), speed));
            mMediaController.showOnce(mToastTextView);
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
        }

        return super.onOptionsItemSelected(item);
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
        return super.onPrepareOptionsMenu(menu);
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
        appendNativeCapabilities(sb);
        return sb.toString();
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
