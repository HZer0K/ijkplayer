/*
 * Lightweight ExoPlayer v2 bridge for IJK
 */
package tv.danmaku.ijk.media.exo;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.video.VideoSize;

import java.io.FileDescriptor;
import java.util.Map;

import tv.danmaku.ijk.media.player.AbstractMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;

public class IjkExoMediaPlayer extends AbstractMediaPlayer {
    private final Context mAppContext;
    private ExoPlayer mInternalPlayer;
    private String mDataSource;
    private int mVideoWidth;
    private int mVideoHeight;
    private Surface mSurface;
    private boolean mDidPrepare;

    public IjkExoMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void setDisplay(SurfaceHolder sh) {
        if (sh == null)
            setSurface(null);
        else
            setSurface(sh.getSurface());
    }

    @Override
    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mInternalPlayer != null)
            mInternalPlayer.setVideoSurface(surface);
    }

    @Override
    public void setDataSource(Context context, Uri uri) {
        mDataSource = uri.toString();
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers) {
        setDataSource(context, uri);
    }

    @Override
    public void setDataSource(String path) {
        setDataSource(mAppContext, Uri.parse(path));
    }

    @Override
    public void setDataSource(FileDescriptor fd) {
        throw new UnsupportedOperationException("no support");
    }

    @Override
    public String getDataSource() {
        return mDataSource;
    }

    @Override
    public void prepareAsync() throws IllegalStateException {
        if (mInternalPlayer != null)
            throw new IllegalStateException("can't prepare a prepared player");

        DefaultDataSource.Factory dsf = new DefaultDataSource.Factory(mAppContext);
        DefaultMediaSourceFactory msf = new DefaultMediaSourceFactory(dsf);
        mInternalPlayer = new ExoPlayer.Builder(mAppContext)
                .setMediaSourceFactory(msf)
                .build();
        mDidPrepare = false;
        mInternalPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        notifyOnInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                        break;
                    case Player.STATE_READY:
                        if (!mDidPrepare) {
                            mDidPrepare = true;
                            notifyOnPrepared();
                        }
                        notifyOnInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                        break;
                    case Player.STATE_ENDED:
                        notifyOnCompletion();
                        break;
                    case Player.STATE_IDLE:
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                notifyOnError(IMediaPlayer.MEDIA_ERROR_UNKNOWN, IMediaPlayer.MEDIA_ERROR_UNKNOWN);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                mVideoWidth = videoSize.width;
                mVideoHeight = videoSize.height;
                notifyOnVideoSizeChanged(mVideoWidth, mVideoHeight, 1, 1);
            }
        });

        if (mSurface != null)
            mInternalPlayer.setVideoSurface(mSurface);

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mDataSource));
        mInternalPlayer.setMediaItem(mediaItem);
        mInternalPlayer.prepare();
        mInternalPlayer.setPlayWhenReady(false);
    }

    @Override
    public void start() throws IllegalStateException {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.play();
    }

    @Override
    public void stop() throws IllegalStateException {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.stop();
    }

    @Override
    public void pause() throws IllegalStateException {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.pause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setWakeMode(Context context, int mode) {
    }

    @Override
    public void setScreenOnWhilePlaying(boolean screenOn) {
    }

    @Override
    public IjkTrackInfo[] getTrackInfo() {
        return null;
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null)
            return false;
        return mInternalPlayer.isPlaying();
    }

    @Override
    public void seekTo(long msec) throws IllegalStateException {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.seekTo(msec);
    }

    @Override
    public long getCurrentPosition() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getDuration();
    }

    @Override
    public int getVideoSarNum() {
        return 1;
    }

    @Override
    public int getVideoSarDen() {
        return 1;
    }

    @Override
    public void reset() {
        if (mInternalPlayer != null) {
            mInternalPlayer.release();
            mInternalPlayer = null;
        }

        mSurface = null;
        mDataSource = null;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mDidPrepare = false;
    }

    @Override
    public void setLooping(boolean looping) {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    @Override
    public boolean isLooping() {
        if (mInternalPlayer == null)
            return false;
        return mInternalPlayer.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setVolume((leftVolume + rightVolume) / 2f);
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public MediaInfo getMediaInfo() {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setLogEnabled(boolean enable) {
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPlayable() {
        return true;
    }

    @Override
    public void setAudioStreamType(int streamtype) {
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setKeepInBackground(boolean keepInBackground) {
    }

    @Override
    public void release() {
        reset();
    }

    public int getBufferedPercentage() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getBufferedPercentage();
    }
}
