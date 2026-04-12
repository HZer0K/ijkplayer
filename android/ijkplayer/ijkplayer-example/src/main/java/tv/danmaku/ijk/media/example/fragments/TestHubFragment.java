package tv.danmaku.ijk.media.example.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.File;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.activities.SampleMediaActivity;
import tv.danmaku.ijk.media.example.activities.TestHubActivity;
import tv.danmaku.ijk.media.example.activities.VideoActivity;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.util.NativeFFmpegDiagnostics;

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
        } else {
            buildFeatureItems(activity);
        }
    }

    private void buildContentItems(Activity activity) {
        mAdapter.add(HubItem.header(activity.getString(R.string.test_hub_content_title)));

        mAdapter.add(HubItem.item("本地视频（文件浏览）", "在当前页面打开本地文件浏览器", () -> openLocalBrowser(activity)));

        mAdapter.add(HubItem.item("网络视频（样例列表）", "内置可复现的网络样例（HLS/MP4/LAS等）", () -> SampleMediaActivity.intentTo(activity)));

        mAdapter.add(HubItem.item("格式样例（按格式分组）", "按 HLS / MP4 / LAS 分组展示网络样例", () -> SampleMediaActivity.intentToByGroup(activity, SampleMediaActivity.GROUP_BY_TYPE)));

        mAdapter.add(HubItem.item("打开URL", "输入网络地址并直接播放", () -> showOpenUrlDialog(activity)));
    }

    private void buildFeatureItems(Activity activity) {
        mAdapter.add(HubItem.header(activity.getString(R.string.test_hub_feature_title)));

        // Vulkan capability check
        mAdapter.add(HubItem.header(activity.getString(R.string.test_hub_vulkan_title)));
        mAdapter.add(HubItem.item(
                activity.getString(R.string.test_hub_vulkan_title),
                activity.getString(R.string.test_hub_vulkan_desc),
                () -> showVulkanCapabilityDialog(activity)));
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

    private void showVulkanCapabilityDialog(Activity activity) {
        StringBuilder sb = new StringBuilder();

        // 1. Device Vulkan hardware support
        boolean deviceVulkan = false;
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            boolean hasLevel = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);
            boolean hasVersion = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
            deviceVulkan = hasLevel || hasVersion;
        } catch (Throwable ignored) {}
        sb.append(activity.getString(R.string.vulkan_check_device_support)).append(": ")
          .append(deviceVulkan ? activity.getString(R.string.vulkan_check_yes) : activity.getString(R.string.vulkan_check_no))
          .append("\n");

        // 2. Build-time Vulkan / filter flags from native diagnostics
        String capsJson = NativeFFmpegDiagnostics.getCapabilitiesJsonOrNull();
        if (capsJson == null) {
            sb.append(activity.getString(R.string.vulkan_check_build_enabled)).append(": ")
              .append(activity.getString(R.string.vulkan_check_unknown)).append("\n");
            sb.append(activity.getString(R.string.vulkan_check_filters_enabled)).append(": ")
              .append(activity.getString(R.string.vulkan_check_unknown)).append("\n");
        } else {
            try {
                JSONObject caps = new JSONObject(capsJson);
                boolean buildVulkan = caps.optBoolean("build_vulkan_enabled", false);
                boolean buildFilters = caps.optBoolean("build_vulkan_filters_enabled", false);
                int filtersCount = caps.optInt("filters_count", -1);

                sb.append(activity.getString(R.string.vulkan_check_build_enabled)).append(": ")
                  .append(buildVulkan ? activity.getString(R.string.vulkan_check_yes) : activity.getString(R.string.vulkan_check_no))
                  .append("\n");
                sb.append(activity.getString(R.string.vulkan_check_filters_enabled)).append(": ")
                  .append(buildFilters ? activity.getString(R.string.vulkan_check_yes) : activity.getString(R.string.vulkan_check_no))
                  .append("\n");
                if (filtersCount >= 0) {
                    sb.append(activity.getString(R.string.vulkan_check_filters_count)).append(": ")
                      .append(filtersCount).append("\n");
                }

                // Per-filter presence table
                JSONObject fp = caps.optJSONObject("filter_presence");
                if (fp != null) {
                    sb.append("\n--- 滤镜存在表 ---\n");
                    // Key filters to highlight
                    String[] checkFilters = {
                        "hflip", "vflip", "gblur", "eq", "scale", "format",
                        "hwupload", "hwdownload",
                        "scale_vulkan", "hflip_vulkan", "vflip_vulkan",
                        "gblur_vulkan", "avgblur_vulkan", "chromaber_vulkan"
                    };
                    for (String f : checkFilters) {
                        if (fp.has(f)) {
                            boolean present = fp.optBoolean(f, false);
                            sb.append("  ").append(present ? "✓" : "✗")
                              .append(" ").append(f).append("\n");
                        }
                    }
                }
            } catch (Throwable e) {
                sb.append("(JSON 解析失败: ").append(e.getMessage()).append(")\n");
            }
        }

        final String report = sb.toString();
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.test_hub_vulkan_title))
                .setMessage(report)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(android.R.string.copy, (dialog, which) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("vulkan_report", report));
                            Toast.makeText(activity, R.string.vulkan_check_copied, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable ignored) {}
                })
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
