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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;
import java.util.Locale;

import android.widget.Toast;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.content.RecentMediaStorage;
import tv.danmaku.ijk.media.example.fragments.TracksFragment;
import tv.danmaku.ijk.media.example.util.DebugEventLog;
import tv.danmaku.ijk.media.example.widget.media.AndroidMediaController;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.media.MeasureHelper;

public class VideoActivity extends AppCompatActivity implements TracksFragment.ITrackHolder {
    private static final String TAG = "VideoActivity";

    private String mVideoPath;
    private Uri    mVideoUri;

    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private TextView mToastTextView;
    private TableLayout mHudView;

    private Settings mSettings;
    private boolean mBackPressed;
    private boolean mEdgeBackActive;
    private float mEdgeBackDownX;
    private float mEdgeBackDownY;
    private float mEdgeBackEdgeSizePx;
    private float mEdgeBackTriggerPx;
    private int mEdgeBackTouchSlop;

    public static Intent newIntent(Context context, String videoPath, String videoTitle) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoPath", videoPath);
        intent.putExtra("videoTitle", videoTitle);
        return intent;
    }

    public static void intentTo(Context context, String videoPath, String videoTitle) {
        context.startActivity(newIntent(context, videoPath, videoTitle));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mSettings = new Settings(this);

        // handle arguments
        mVideoPath = getIntent().getStringExtra("videoPath");

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

        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);
        mVideoView.setHudView(mHudView);
        installEdgeBackHelper();
        DebugEventLog.add("VideoActivity: onCreate, source=" + (mVideoPath != null ? mVideoPath : (mVideoUri != null ? mVideoUri.toString() : "null")));
        DebugEventLog.add("VideoActivity: pref.player=" + mSettings.getPlayer() + ", preferExoForHttp=" + mSettings.getPreferExoForHttp());
        // prefer mVideoPath
        if (mVideoPath != null)
            mVideoView.setVideoPath(mVideoPath);
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

    private void installEdgeBackHelper() {
        float density = getResources().getDisplayMetrics().density;
        mEdgeBackEdgeSizePx = 32f * density;
        mEdgeBackTriggerPx = 72f * density;
        mEdgeBackTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        View root = findViewById(android.R.id.content);
        if (root == null) {
            root = mVideoView;
        }
        if (root == null) {
            return;
        }

        root.setOnTouchListener((v, event) -> {
            if (event == null) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mEdgeBackDownX = event.getX();
                mEdgeBackDownY = event.getY();
                int width = v.getWidth();
                mEdgeBackActive = width > 0 && (mEdgeBackDownX <= mEdgeBackEdgeSizePx || mEdgeBackDownX >= width - mEdgeBackEdgeSizePx);
                return false;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (!mEdgeBackActive) {
                    return false;
                }
                float dx = event.getX() - mEdgeBackDownX;
                float dy = event.getY() - mEdgeBackDownY;
                if (Math.abs(dy) > Math.abs(dx)) {
                    return false;
                }
                if (Math.abs(dx) < mEdgeBackTouchSlop) {
                    return false;
                }
                if (Math.abs(dx) >= mEdgeBackTriggerPx) {
                    getOnBackPressedDispatcher().onBackPressed();
                    mEdgeBackActive = false;
                    return true;
                }
                return false;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mEdgeBackActive = false;
                return false;
            }
            return false;
        });
    }

    private void playUrl(String url) {
        if (TextUtils.isEmpty(url))
            return;
        try {
            if (isLikelyMediaUrl(url)) {
                DebugEventLog.add("playUrl: " + url);
                mVideoPath = url;
                mVideoUri = Uri.parse(url);
                mVideoView.stopPlayback();
                mVideoView.release(true);
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
        if (!TextUtils.isEmpty(mVideoPath))
            return mVideoPath;
        return mVideoUri != null ? mVideoUri.toString() : null;
    }

    private void rebuildAndPlayCurrent() {
        String source = getCurrentSource();
        if (TextUtils.isEmpty(source))
            return;

        try {
            DebugEventLog.add("rebuildAndPlayCurrent: " + source);
            mVideoView.stopPlayback();
            mVideoView.release(true);
            if (source.contains("adaptationSet")) {
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
        int next;
        boolean preferExoForHttp;
        if (current == Settings.PV_PLAYER__IjkExoMediaPlayer) {
            next = Settings.PV_PLAYER__IjkMediaPlayer;
            preferExoForHttp = false;
        } else {
            next = Settings.PV_PLAYER__IjkExoMediaPlayer;
            preferExoForHttp = true;
        }

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

    private boolean isLikelyMediaUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.startsWith("rtmp://") || lower.startsWith("rtsp://"))
            return true;
        if (lower.endsWith(".m3u8") || lower.contains(".m3u8?"))
            return true;
        if (lower.endsWith(".mp4") || lower.contains(".mp4?"))
            return true;
        if (lower.endsWith(".flv") || lower.contains(".flv?"))
            return true;
        if (lower.endsWith(".mov") || lower.contains(".mov?"))
            return true;
        // simple HLS master manifest hint
        Pattern p = Pattern.compile("(?i)video|play|stream.*(m3u8|mp4)");
        Matcher m = p.matcher(lower);
        return m.find();
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
        } else if (id == R.id.action_demo_hls) {
            playUrl("https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8");
            return true;
        } else if (id == R.id.action_demo_mp4) {
            playUrl("https://media.w3.org/2010/05/sintel/trailer.mp4");
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
        } else if (id == R.id.action_toggle_speed) {
            float speed = mVideoView.toggleSpeed();
            mToastTextView.setText(String.format(Locale.US, "%s: %.1fx", getString(R.string.playback_speed), speed));
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_snapshot) {
            String path = mVideoView.captureFrame(this);
            if (path != null) {
                mToastTextView.setText(String.format(Locale.US, "%s: %s", getString(R.string.snapshot), path));
            } else {
                mToastTextView.setText(getString(R.string.N_A));
            }
            mMediaController.showOnce(mToastTextView);
            return true;
        }

        return super.onOptionsItemSelected(item);
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
