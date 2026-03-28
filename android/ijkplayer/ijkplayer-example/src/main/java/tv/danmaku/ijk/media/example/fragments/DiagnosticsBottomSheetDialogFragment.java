package tv.danmaku.ijk.media.example.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import tv.danmaku.ijk.media.example.R;

public class DiagnosticsBottomSheetDialogFragment extends BottomSheetDialogFragment {
    private static final String ARG_SUMMARY = "summary";
    private static final String ARG_LOGS = "logs";

    public static DiagnosticsBottomSheetDialogFragment newInstance(String summary, String logs) {
        DiagnosticsBottomSheetDialogFragment f = new DiagnosticsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SUMMARY, summary);
        args.putString(ARG_LOGS, logs);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_diagnostics, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        String summary = args != null ? args.getString(ARG_SUMMARY, "") : "";
        String logs = args != null ? args.getString(ARG_LOGS, "") : "";

        TextView summaryText = view.findViewById(R.id.text_summary);
        TextView logsText = view.findViewById(R.id.text_logs);
        summaryText.setText(summary);
        logsText.setText(logs);

        view.findViewById(R.id.btn_copy_diagnostics).setOnClickListener(v -> copyText(summary));
        view.findViewById(R.id.btn_copy_logs).setOnClickListener(v -> copyText(logs));
    }

    private void copyText(String text) {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("diagnostics", text != null ? text : ""));
            }
        } catch (Throwable ignored) {
        }
    }
}
