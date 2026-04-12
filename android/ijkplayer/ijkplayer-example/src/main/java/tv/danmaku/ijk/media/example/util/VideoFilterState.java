/*
 * Copyright (C) 2015 Bilibili
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

package tv.danmaku.ijk.media.example.util;

import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;

/**
 * Lightweight state container for video filter selection.
 *
 * Tracks the current render-layer filter type and the current FFmpeg vf0 filter
 * string independently.  Business logic (applying to player / showing toasts)
 * stays in VideoActivity to avoid deep callback coupling; this class only holds
 * the state so it can be queried without scattering fields across the Activity.
 */
public class VideoFilterState {

    /** Currently selected render-layer filter (see {@link IjkVideoView}.RENDER_FILTER_*) */
    private int mRenderFilterType = IjkVideoView.RENDER_FILTER_NONE;

    /** Currently active FFmpeg vf0 filter string; null = none */
    private String mVf0Filter = null;

    public int getRenderFilterType() {
        return mRenderFilterType;
    }

    public void setRenderFilterType(int type) {
        mRenderFilterType = type;
    }

    public String getVf0Filter() {
        return mVf0Filter;
    }

    public void setVf0Filter(String vf0) {
        mVf0Filter = vf0;
    }

    /** Clear both render-layer and vf0 filter state. */
    public void clearAll() {
        mRenderFilterType = IjkVideoView.RENDER_FILTER_NONE;
        mVf0Filter = null;
    }

    public boolean hasRenderFilter() {
        return mRenderFilterType != IjkVideoView.RENDER_FILTER_NONE;
    }

    public boolean hasVf0Filter() {
        return mVf0Filter != null;
    }
}
