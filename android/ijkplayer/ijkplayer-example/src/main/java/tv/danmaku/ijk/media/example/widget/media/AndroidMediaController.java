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

package tv.danmaku.ijk.media.example.widget.media;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import android.util.AttributeSet;
import android.view.View;
import android.widget.MediaController;

import java.util.ArrayList;

public class AndroidMediaController extends MediaController implements IMediaController {
    private static final int DEFAULT_TIMEOUT_MS = 8000;
    private ActionBar mActionBar;
    private int mDefaultTimeoutMs = DEFAULT_TIMEOUT_MS;
    private boolean mImmersiveEnabled = true;

    public AndroidMediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public AndroidMediaController(Context context, boolean useFastForward) {
        super(context, useFastForward);
        initView(context);
    }

    public AndroidMediaController(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
    }

    public void setSupportActionBar(@Nullable ActionBar actionBar) {
        mActionBar = actionBar;
        if (mActionBar == null)
            return;
        if (isShowing())
            mActionBar.show();
        else
            mActionBar.hide();
    }

    @Override
    public void show() {
        super.show(mDefaultTimeoutMs);
        if (mActionBar != null)
            mActionBar.show();
        updateSystemUiVisibility(true);
    }

    @Override
    public void show(int timeout) {
        super.show(timeout);
        if (mActionBar != null)
            mActionBar.show();
        updateSystemUiVisibility(true);
    }

    @Override
    public void hide() {
        super.hide();
        if (mActionBar != null)
            mActionBar.hide();
        for (View view : mShowOnceArray)
            view.setVisibility(View.GONE);
        mShowOnceArray.clear();
        updateSystemUiVisibility(false);
    }

    //----------
    // Immersive mode
    //----------
    /**
     * Toggle system UI (status bar / navigation bar) visibility.
     * When the media controller is visible, system bars are shown;
     * when hidden, system bars are hidden (immersive sticky mode).
     */
    private void updateSystemUiVisibility(boolean controllerVisible) {
        if (!mImmersiveEnabled) return;
        Context ctx = getContext();
        if (!(ctx instanceof Activity)) return;
        View decorView = ((Activity) ctx).getWindow().getDecorView();
        if (controllerVisible) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /**
     * Enable or disable immersive mode.
     * When disabled, system UI is restored to its normal visible state.
     */
    public void setImmersiveEnabled(boolean enabled) {
        mImmersiveEnabled = enabled;
        if (!enabled) {
            Context ctx = getContext();
            if (ctx instanceof Activity) {
                ((Activity) ctx).getWindow().getDecorView()
                        .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }

    //----------
    // Extends
    //----------
    private ArrayList<View> mShowOnceArray = new ArrayList<View>();

    public void showOnce(@NonNull View view) {
        mShowOnceArray.add(view);
        view.setVisibility(View.VISIBLE);
        show();
    }

    public void setDefaultTimeoutMs(int timeoutMs) {
        mDefaultTimeoutMs = timeoutMs;
    }
}
