package tv.danmaku.ijk.media.example.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.activities.SampleMediaActivity;
import tv.danmaku.ijk.media.example.activities.TestHubActivity;
import tv.danmaku.ijk.media.example.activities.VideoActivity;
import tv.danmaku.ijk.media.example.application.Settings;

public class TestHubFragment extends Fragment {
    private static final String ARG_MODE = "mode";

    private ListView mListView;
    private HubAdapter mAdapter;

    public static TestHubFragment newInstance(String mode) {
        TestHubFragment f = new TestHubFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode);
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

        String mode = null;
        Bundle args = getArguments();
        if (args != null) {
            mode = args.getString(ARG_MODE);
        }

        if (TextUtils.isEmpty(mode)) {
            mode = TestHubActivity.MODE_CONTENT;
        }

        if (TestHubActivity.MODE_CONTENT.equals(mode)) {
            activity.setTitle(R.string.test_hub_content_title);
        } else {
            activity.setTitle(R.string.test_hub_activity_title);
        }

        mAdapter = new HubAdapter(activity);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                HubItem item = mAdapter.getItem(position);
                if (item == null || item.isHeader)
                    return;
                item.action.run();
            }
        });

        if (TestHubActivity.MODE_CONTENT.equals(mode)) {
            buildContentItems(activity);
        }
    }

    private void buildContentItems(Activity activity) {
        mAdapter.add(HubItem.header(activity.getString(R.string.test_hub_content_title)));

        mAdapter.add(HubItem.item("本地视频（文件浏览）", "在当前页面打开本地文件浏览器", () -> openLocalBrowser(activity)));

        mAdapter.add(HubItem.item("网络视频（样例列表）", "内置可复现的网络样例（HLS/MP4/LAS等）", () -> SampleMediaActivity.intentTo(activity)));

        mAdapter.add(HubItem.item("格式样例（按格式分组）", "按 HLS / MP4 / LAS 分组展示网络样例", () -> SampleMediaActivity.intentToByGroup(activity, SampleMediaActivity.GROUP_BY_TYPE)));

        mAdapter.add(HubItem.item("打开URL", "输入网络地址并直接播放", () -> showOpenUrlDialog(activity)));
    }

    private void openLocalBrowser(Activity activity) {
        String path = "/";
        Settings settings = new Settings(activity);
        String lastDirectory = settings.getLastDirectory();
        if (!TextUtils.isEmpty(lastDirectory) && new File(lastDirectory).isDirectory()) {
            path = lastDirectory;
        }

        Fragment fragment = FileListFragment.newInstance(path, false);
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.body, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showOpenUrlDialog(Activity activity) {
        EditText editText = new EditText(activity);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.open_url)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String url = editText.getText() != null ? editText.getText().toString() : null;
                    if (!TextUtils.isEmpty(url)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.setClass(activity, VideoActivity.class);
                        intent.setData(Uri.parse(url));
                        activity.startActivity(intent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static final class HubItem {
        final boolean isHeader;
        final String title;
        final String subtitle;
        final Runnable action;

        private HubItem(boolean isHeader, String title, String subtitle, Runnable action) {
            this.isHeader = isHeader;
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
        }

        static HubItem header(String title) {
            return new HubItem(true, title, "", null);
        }

        static HubItem item(String title, String subtitle, Runnable action) {
            return new HubItem(false, title, subtitle, action);
        }
    }

    final class HubAdapter extends ArrayAdapter<HubItem> {
        HubAdapter(Activity activity) {
            super(activity, android.R.layout.simple_list_item_2, new ArrayList<>());
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            HubItem item = getItem(position);
            return item != null && item.isHeader ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HubItem item = getItem(position);
            if (item != null && item.isHeader) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                    view = inflater.inflate(R.layout.list_item_header, parent, false);
                }
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setText(item.title);
                return view;
            }

            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                view = inflater.inflate(R.layout.list_item_two_line, parent, false);
            }

            TextView title = view.findViewById(android.R.id.text1);
            TextView subtitle = view.findViewById(android.R.id.text2);
            title.setText(item != null ? item.title : "");
            subtitle.setText(item != null ? item.subtitle : "");
            return view;
        }
    }
}
