package tv.danmaku.ijk.media.example.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

public class TestCaseListFragment extends Fragment {
    private static final String ARG_RAW_RES_ID = "raw_res_id";

    private ListView mListView;
    private CaseAdapter mAdapter;

    public static TestCaseListFragment newInstance(int rawResId) {
        TestCaseListFragment f = new TestCaseListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_RAW_RES_ID, rawResId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_file_list, container, false);
        mListView = viewGroup.findViewById(R.id.file_list_view);
        return viewGroup;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = getActivity();
        if (activity == null)
            return;

        int rawResId = 0;
        Bundle args = getArguments();
        if (args != null) {
            rawResId = args.getInt(ARG_RAW_RES_ID, 0);
        }

        mAdapter = new CaseAdapter(activity);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CaseItem item = mAdapter.getItem(position);
                if (item == null || item.isHeader)
                    return;
                if (item.vf0 != null && !item.vf0.isEmpty()) {
                    VideoActivity.intentToWithHintAndVf0(activity, item.url, item.name, item.hint, item.vf0);
                } else {
                    VideoActivity.intentToWithHint(activity, item.url, item.name, item.hint);
                }
            }
        });

        loadCasesFromJson(activity, rawResId);
    }

    private void loadCasesFromJson(Context context, int rawResId) {
        if (rawResId == 0)
            return;

        String json = readRawText(context, rawResId);
        if (json == null)
            return;

        try {
            LinkedHashMap<String, List<CaseItem>> grouped = new LinkedHashMap<>();
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null)
                    continue;

                String group = obj.optString("group", "");
                String name = obj.optString("name", "");
                String url = obj.optString("url", "");
                String hint = obj.optString("hint", "");
                String vf0 = obj.optString("vf0", "");
                if (group == null || group.trim().isEmpty())
                    group = "Other";
                if (!grouped.containsKey(group))
                    grouped.put(group, new ArrayList<>());
                if (url != null && !url.isEmpty()) {
                    grouped.get(group).add(CaseItem.caseItem(name, url, hint, vf0));
                }
            }

            for (Map.Entry<String, List<CaseItem>> entry : grouped.entrySet()) {
                String group = entry.getKey();
                List<CaseItem> items = entry.getValue();
                if (items == null || items.isEmpty())
                    continue;
                mAdapter.add(CaseItem.header(group));
                for (CaseItem item : items) {
                    mAdapter.add(item);
                }
            }
        } catch (JSONException e) {
        }
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

    static final class CaseItem {
        final boolean isHeader;
        final String name;
        final String url;
        final String hint;
        final String vf0;

        private CaseItem(boolean isHeader, String name, String url, String hint, String vf0) {
            this.isHeader = isHeader;
            this.name = name;
            this.url = url;
            this.hint = hint;
            this.vf0 = vf0;
        }

        static CaseItem header(String title) {
            return new CaseItem(true, title, "", "", "");
        }

        static CaseItem caseItem(String name, String url, String hint, String vf0) {
            return new CaseItem(false, name, url, hint, vf0);
        }
    }

    final class CaseAdapter extends ArrayAdapter<CaseItem> {
        CaseAdapter(Activity activity) {
            super(activity, android.R.layout.simple_list_item_2, new ArrayList<>());
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            CaseItem item = getItem(position);
            return item != null && item.isHeader ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CaseItem item = getItem(position);
            if (item != null && item.isHeader) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                    view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
                }
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setText(item.name);
                return view;
            }

            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                view = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            TextView title = view.findViewById(android.R.id.text1);
            TextView subtitle = view.findViewById(android.R.id.text2);
            title.setText(item != null ? item.name : "");
            subtitle.setText(item != null ? item.url : "");
            return view;
        }
    }
}
