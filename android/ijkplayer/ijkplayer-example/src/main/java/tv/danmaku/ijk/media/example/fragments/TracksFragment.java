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

package tv.danmaku.ijk.media.example.fragments;

import android.os.Build;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.example.R;

public class TracksFragment extends Fragment {
    private ListView mTrackListView;
    private TrackAdapter mAdapter;

    public static TracksFragment newInstance() {
        return new TracksFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_track_list, container, false);
        mTrackListView = (ListView) viewGroup.findViewById(R.id.track_list_view);
        return viewGroup;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Activity activity = requireActivity();

        mAdapter = new TrackAdapter(activity);
        mTrackListView.setAdapter(mAdapter);

        if (activity instanceof ITrackHolder) {
            final ITrackHolder trackHolder = (ITrackHolder) activity;
            mAdapter.setTrackHolder(trackHolder);

            mTrackListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, final int position, final long id) {
                    ListEntry entry = mAdapter.getEntry(position);
                    if (entry == null || entry.isHeader) return;

                    TrackItem trackItem = entry.trackItem;
                    // Deselect other tracks of same type
                    for (int i = 0; i < mAdapter.getCount(); i++) {
                        ListEntry other = mAdapter.getEntry(i);
                        if (other == null || other.isHeader) continue;
                        if (other.trackItem.mIndex == trackItem.mIndex) continue;
                        if (other.trackItem.mTrackInfo.getTrackType() != trackItem.mTrackInfo.getTrackType()) continue;
                        other.selected = false;
                    }

                    entry.selected = !entry.selected;
                    mAdapter.notifyDataSetChanged();

                    if (entry.selected) {
                        trackHolder.selectTrack(trackItem.mIndex);
                    } else {
                        trackHolder.deselectTrack(trackItem.mIndex);
                    }
                }
            });
        } else {
            Log.e("TracksFragment", "activity is not an instance of ITrackHolder.");
        }
    }

    public interface ITrackHolder {
        ITrackInfo[] getTrackInfo();
        int getSelectedTrack(int trackType);
        void selectTrack(int stream);
        void deselectTrack(int stream);
    }

    // -----------------------------------------------------------------------
    // Data model
    // -----------------------------------------------------------------------

    static final class TrackItem {
        public int mIndex;
        public ITrackInfo mTrackInfo;

        public TrackItem(int index, ITrackInfo trackInfo) {
            mIndex = index;
            mTrackInfo = trackInfo;
        }

        public String getInfoInline() {
            return String.format(Locale.US, "# %d: %s", mIndex, mTrackInfo.getInfoInline());
        }
    }

    static final class ListEntry {
        public boolean isHeader;
        public String  headerTitle;   // valid when isHeader == true
        public TrackItem trackItem;   // valid when isHeader == false
        public boolean selected;      // valid when isHeader == false

        static ListEntry header(String title) {
            ListEntry e = new ListEntry();
            e.isHeader = true;
            e.headerTitle = title;
            return e;
        }

        static ListEntry track(TrackItem item, boolean selected) {
            ListEntry e = new ListEntry();
            e.isHeader = false;
            e.trackItem = item;
            e.selected = selected;
            return e;
        }
    }

    // -----------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------

    final class TrackAdapter extends BaseAdapter {
        private final Context mContext;
        private final ArrayList<ListEntry> mEntries = new ArrayList<>();

        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_TRACK  = 1;

        public TrackAdapter(Context context) {
            mContext = context;
        }

        public void setTrackHolder(ITrackHolder trackHolder) {
            mEntries.clear();
            ITrackInfo[] infos = trackHolder.getTrackInfo();
            if (infos == null) {
                notifyDataSetChanged();
                return;
            }

            int selVideo    = trackHolder.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_VIDEO);
            int selAudio    = trackHolder.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
            int selSubtitle = trackHolder.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);

            // Collect tracks by type
            ArrayList<TrackItem> videoTracks    = new ArrayList<>();
            ArrayList<TrackItem> audioTracks    = new ArrayList<>();
            ArrayList<TrackItem> subtitleTracks = new ArrayList<>();
            ArrayList<TrackItem> otherTracks    = new ArrayList<>();

            for (int i = 0; i < infos.length; i++) {
                TrackItem item = new TrackItem(i, infos[i]);
                switch (infos[i].getTrackType()) {
                    case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:     videoTracks.add(item);    break;
                    case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:     audioTracks.add(item);    break;
                    case ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT: subtitleTracks.add(item); break;
                    default:                                     otherTracks.add(item);    break;
                }
            }

            // Build grouped list
            addGroup(mContext.getString(R.string.track_section_video),    videoTracks,    selVideo);
            addGroup(mContext.getString(R.string.track_section_audio),    audioTracks,    selAudio);
            addGroup(mContext.getString(R.string.track_section_subtitle), subtitleTracks, selSubtitle);
            addGroup(mContext.getString(R.string.track_section_other),    otherTracks,    -1);

            notifyDataSetChanged();
        }

        private void addGroup(String title, ArrayList<TrackItem> items, int selectedIndex) {
            if (items.isEmpty()) return;
            mEntries.add(ListEntry.header(title));
            for (TrackItem item : items) {
                boolean sel = (item.mIndex == selectedIndex);
                mEntries.add(ListEntry.track(item, sel));
            }
        }

        public ListEntry getEntry(int position) {
            if (position < 0 || position >= mEntries.size()) return null;
            return mEntries.get(position);
        }

        @Override public int getCount()              { return mEntries.size(); }
        @Override public Object getItem(int position){ return mEntries.get(position); }
        @Override public long getItemId(int position){ return position; }
        @Override public int getViewTypeCount()      { return 2; }

        @Override
        public int getItemViewType(int position) {
            return mEntries.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_TRACK;
        }

        @Override
        public boolean isEnabled(int position) {
            return !mEntries.get(position).isHeader;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListEntry entry = mEntries.get(position);
            if (entry.isHeader) {
                // Section header — ListView recycles by view type, so convertView is always a header view
                if (convertView == null) {
                    convertView = LayoutInflater.from(mContext)
                            .inflate(android.R.layout.simple_list_item_1, parent, false);
                    TextView headerTv = (TextView) convertView.findViewById(android.R.id.text1);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        headerTv.setTextAppearance(android.R.style.TextAppearance_Medium);
                    } else {
                        //noinspection deprecation
                        headerTv.setTextAppearance(mContext, android.R.style.TextAppearance_Medium);
                    }
                    headerTv.setEnabled(false);
                }
                ((TextView) convertView.findViewById(android.R.id.text1))
                        .setText(entry.headerTitle);
                return convertView;
            } else {
                // Track item with checkmark
                if (convertView == null) {
                    convertView = LayoutInflater.from(mContext)
                            .inflate(android.R.layout.simple_list_item_checked, parent, false);
                }
                CheckedTextView ctv = (CheckedTextView) convertView.findViewById(android.R.id.text1);
                String suffix = entry.selected ? mContext.getString(R.string.track_selected_suffix) : "";
                ctv.setText(entry.trackItem.getInfoInline() + suffix);
                ctv.setChecked(entry.selected);
                return convertView;
            }
        }
    }
}
