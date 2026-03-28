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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

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
    private EditText mSearchInput;
    private final List<CaseItem> mAllCases = new ArrayList<>();

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

        View header = LayoutInflater.from(activity).inflate(R.layout.header_case_search, mListView, false);
        mSearchInput = header.findViewById(R.id.input_search);
        mListView.addHeaderView(header, null, false);

        mAdapter = new CaseAdapter(activity);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int adapterPosition = position - mListView.getHeaderViewsCount();
                if (adapterPosition < 0) {
                    return;
                }
                CaseItem item = mAdapter.getItem(adapterPosition);
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
        bindSearch();
    }

    private void loadCasesFromJson(Context context, int rawResId) {
        if (rawResId == 0)
            return;

        String json = readRawText(context, rawResId);
        if (json == null)
            return;

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null)
                    continue;

                String category = obj.optString("category", "");
                String group = obj.optString("group", "");
                String name = obj.optString("name", "");
                String url = obj.optString("url", "");
                String hint = obj.optString("hint", "");
                String vf0 = obj.optString("vf0", "");
                String expected = obj.optString("expected", "");
                JSONArray logKeywordsArray = obj.optJSONArray("logKeywords");
                List<String> logKeywords = toStringList(logKeywordsArray);
                JSONArray tagsArray = obj.optJSONArray("tags");
                List<String> tags = toStringList(tagsArray);

                String groupKey = !TextUtils.isEmpty(category) ? category : group;
                if (TextUtils.isEmpty(groupKey)) {
                    groupKey = "Other";
                }

                if (!TextUtils.isEmpty(url)) {
                    mAllCases.add(CaseItem.caseItem(groupKey, name, url, hint, vf0, expected, logKeywords, tags));
                }
            }
            rebuildAdapter(null);
        } catch (JSONException e) {
        }
    }

    private void bindSearch() {
        if (mSearchInput == null) {
            return;
        }
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                rebuildAdapter(s != null ? s.toString() : null);
            }
        });
    }

    private void rebuildAdapter(String query) {
        String q = query != null ? query.trim() : "";
        LinkedHashMap<String, List<CaseItem>> grouped = new LinkedHashMap<>();
        for (CaseItem c : mAllCases) {
            if (c == null || c.isHeader) {
                continue;
            }
            if (!TextUtils.isEmpty(q) && !c.matches(q)) {
                continue;
            }
            if (!grouped.containsKey(c.group)) {
                grouped.put(c.group, new ArrayList<>());
            }
            grouped.get(c.group).add(c);
        }

        mAdapter.clear();
        for (Map.Entry<String, List<CaseItem>> entry : grouped.entrySet()) {
            List<CaseItem> items = entry.getValue();
            if (items == null || items.isEmpty()) {
                continue;
            }
            mAdapter.add(CaseItem.header(entry.getKey()));
            for (CaseItem item : items) {
                mAdapter.add(item);
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private List<String> toStringList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (!TextUtils.isEmpty(s)) {
                out.add(s);
            }
        }
        return out;
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
        final String group;
        final String name;
        final String url;
        final String hint;
        final String vf0;
        final String subtitle;
        final List<String> tags;

        private CaseItem(boolean isHeader, String group, String name, String url, String hint, String vf0, String subtitle, List<String> tags) {
            this.isHeader = isHeader;
            this.group = group;
            this.name = name;
            this.url = url;
            this.hint = hint;
            this.vf0 = vf0;
            this.subtitle = subtitle;
            this.tags = tags != null ? tags : new ArrayList<>();
        }

        static CaseItem header(String title) {
            return new CaseItem(true, title, title, "", "", "", "", new ArrayList<>());
        }

        static CaseItem caseItem(String group, String name, String url, String hint, String vf0, String expected, List<String> logKeywords, List<String> tags) {
            String primary;
            if (!TextUtils.isEmpty(expected)) {
                primary = "预期: " + expected;
            } else if (!TextUtils.isEmpty(hint)) {
                primary = hint;
            } else {
                primary = url;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(primary);
            if (logKeywords != null && !logKeywords.isEmpty()) {
                sb.append('\n').append("关键日志: ").append(join(logKeywords, ", "));
            }
            if (tags != null && !tags.isEmpty()) {
                sb.append('\n').append("#").append(join(tags, " #"));
            }
            return new CaseItem(false, group, name, url, hint, vf0, sb.toString(), tags);
        }

        boolean matches(String q) {
            String query = q != null ? q.toLowerCase() : "";
            if (TextUtils.isEmpty(query)) {
                return true;
            }
            if (!TextUtils.isEmpty(name) && name.toLowerCase().contains(query)) {
                return true;
            }
            if (!TextUtils.isEmpty(group) && group.toLowerCase().contains(query)) {
                return true;
            }
            if (!TextUtils.isEmpty(hint) && hint.toLowerCase().contains(query)) {
                return true;
            }
            if (!TextUtils.isEmpty(subtitle) && subtitle.toLowerCase().contains(query)) {
                return true;
            }
            if (tags != null) {
                for (String t : tags) {
                    if (!TextUtils.isEmpty(t) && t.toLowerCase().contains(query)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static String join(List<String> list, String sep) {
            if (list == null || list.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(sep);
                }
                sb.append(list.get(i));
            }
            return sb.toString();
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
                    view = inflater.inflate(R.layout.list_item_header, parent, false);
                }
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setText(item.name);
                return view;
            }

            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                view = inflater.inflate(R.layout.list_item_two_line, parent, false);
            }

            TextView title = view.findViewById(android.R.id.text1);
            TextView subtitle = view.findViewById(android.R.id.text2);
            title.setText(item != null ? item.name : "");
            subtitle.setText(item != null ? item.subtitle : "");
            return view;
        }
    }
}
