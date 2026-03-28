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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.activities.VideoActivity;

public class SampleMediaListFragment extends Fragment {
    private static final String ARG_GROUP_BY = "group_by";
    private ListView mFileListView;
    private SampleMediaAdapter mAdapter;

    public static SampleMediaListFragment newInstance(String groupBy) {
        SampleMediaListFragment f = new SampleMediaListFragment();
        Bundle args = new Bundle();
        if (groupBy != null)
            args.putString(ARG_GROUP_BY, groupBy);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_file_list, container, false);
        mFileListView = (ListView) viewGroup.findViewById(R.id.file_list_view);
        return viewGroup;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Activity activity = getActivity();

        mAdapter = new SampleMediaAdapter(activity);
        mFileListView.setAdapter(mAdapter);
        mFileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, final long id) {
                SampleMediaItem item = mAdapter.getItem(position);
                if (item == null || item.mIsHeader)
                    return;
                String name = item.mName;
                String url = item.mPlayUrl;
                VideoActivity.intentTo(activity, url, name);
            }
        });
        loadSamplesFromJson(activity);
    }

    private void loadSamplesFromJson(Context context) {
        String json = readRawText(context, R.raw.sample_media);
        if (json == null)
            return;

        try {
            String groupBy = "category";
            Bundle args = getArguments();
            if (args != null) {
                String value = args.getString(ARG_GROUP_BY);
                if (value != null && !value.trim().isEmpty())
                    groupBy = value;
            }

            LinkedHashMap<String, List<SampleMediaItem>> grouped = new LinkedHashMap<>();
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null)
                    continue;

                String name = obj.optString("name", "");
                String url = obj.optString("url", "");
                String type = obj.optString("type", "");
                String manifestRes = obj.optString("manifestRes", "");
                String category = obj.optString("category", "");
                String recommendedPlayer = obj.optString("recommendedPlayer", "");
                boolean requiresExo = obj.optBoolean("requiresExo", false);

                String groupKey = category;
                if ("type".equalsIgnoreCase(groupBy)) {
                    groupKey = formatTypeGroup(type);
                }
                if (groupKey == null || groupKey.trim().isEmpty())
                    groupKey = "Other";

                if (!grouped.containsKey(groupKey))
                    grouped.put(groupKey, new ArrayList<>());

                if ("ijklas_manifest".equals(type) && manifestRes != null && !manifestRes.isEmpty()) {
                    int resId = context.getResources().getIdentifier(manifestRes, "raw", context.getPackageName());
                    String manifestString = resId != 0 ? readRawText(context, resId) : null;
                    if (manifestString != null) {
                        grouped.get(groupKey).add(SampleMediaItem.createSample(manifestString, "ijklas:(manifest_string)", formatName(name, recommendedPlayer, requiresExo)));
                    }
                    continue;
                }

                if (url != null && !url.isEmpty()) {
                    String displayUrl = obj.optString("displayUrl", url);
                    grouped.get(groupKey).add(SampleMediaItem.createSample(url, displayUrl, formatName(name, recommendedPlayer, requiresExo)));
                }
            }

            for (Map.Entry<String, List<SampleMediaItem>> entry : grouped.entrySet()) {
                String category = entry.getKey();
                List<SampleMediaItem> items = entry.getValue();
                if (items == null || items.isEmpty())
                    continue;

                mAdapter.add(SampleMediaItem.createHeader(category));
                for (SampleMediaItem item : items) {
                    mAdapter.add(item);
                }
            }
        } catch (JSONException e) {
            // ignore
        }
    }

    private String formatTypeGroup(String type) {
        if (type == null)
            return "Other";
        String lower = type.toLowerCase();
        if (lower.startsWith("ijklas"))
            return "LAS";
        if ("hls".equals(lower))
            return "HLS";
        if ("mp4".equals(lower))
            return "MP4";
        return type.toUpperCase();
    }

    private String formatName(String name, String recommendedPlayer, boolean requiresExo) {
        StringBuilder sb = new StringBuilder();
        sb.append(name != null ? name : "");
        if (recommendedPlayer != null && !recommendedPlayer.isEmpty()) {
            sb.append("  [推荐:");
            sb.append(recommendedPlayer.toUpperCase());
            sb.append("]");
        }
        if (requiresExo) {
            sb.append("  [需EXO]");
        }
        return sb.toString();
    }

    private String readRawText(Context context, int resId) {
        InputStream in = null;
        try {
            in = context.getResources().openRawResource(resId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Resources.NotFoundException | IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static final class SampleMediaItem {
        String mPlayUrl;
        String mDisplayUrl;
        String mName;
        boolean mIsHeader;

        public SampleMediaItem(String playUrl, String displayUrl, String name) {
            mPlayUrl = playUrl;
            mDisplayUrl = displayUrl;
            mName = name;
        }

        public static SampleMediaItem createHeader(String title) {
            SampleMediaItem item = new SampleMediaItem("", "", title);
            item.mIsHeader = true;
            return item;
        }

        public static SampleMediaItem createSample(String playUrl, String displayUrl, String name) {
            return new SampleMediaItem(playUrl, displayUrl, name);
        }
    }

    final class SampleMediaAdapter extends ArrayAdapter<SampleMediaItem> {
        public SampleMediaAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_2);
        }

        public void addItem(String url, String name) {
            add(new SampleMediaItem(url, url, name));
        }

        public void addItem(String playUrl, String displayUrl, String name) {
            add(new SampleMediaItem(playUrl, displayUrl, name));
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            SampleMediaItem item = getItem(position);
            return (item != null && item.mIsHeader) ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SampleMediaItem item = getItem(position);
            if (item != null && item.mIsHeader) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                    view = inflater.inflate(R.layout.list_item_header, parent, false);
                }
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setText(item.mName);
                return view;
            }

            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                view = inflater.inflate(R.layout.list_item_two_line, parent, false);
            }

            ViewHolder viewHolder = (ViewHolder) view.getTag();
            if (viewHolder == null) {
                viewHolder = new ViewHolder();
                viewHolder.mNameTextView = (TextView) view.findViewById(android.R.id.text1);
                viewHolder.mUrlTextView = (TextView) view.findViewById(android.R.id.text2);
            }

            viewHolder.mNameTextView.setText(item.mName);
            viewHolder.mUrlTextView.setText(item.mDisplayUrl);

            return view;
        }

        final class ViewHolder {
            public TextView mNameTextView;
            public TextView mUrlTextView;
        }
    }
}
