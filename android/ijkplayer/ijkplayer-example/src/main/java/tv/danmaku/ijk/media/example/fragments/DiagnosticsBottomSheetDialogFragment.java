package tv.danmaku.ijk.media.example.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.util.NativeFFmpegDiagnostics;

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

        View toggleNative = view.findViewById(R.id.btn_toggle_native_diagnostics);
        updateNativeDiagnosticsButtonText(toggleNative);
        toggleNative.setOnClickListener(v -> {
            if (!NativeFFmpegDiagnostics.isAvailable()) {
                try {
                    Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, getString(R.string.native_diagnostics_unavailable_hint), Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable ignored) {
                }
                updateNativeDiagnosticsButtonText(v);
                return;
            }
            boolean next = !NativeFFmpegDiagnostics.isDiagnosticsEnabledSafe();
            boolean ok = NativeFFmpegDiagnostics.setDiagnosticsEnabledSafe(next);
            try {
                Context ctx = getContext();
                if (ctx != null) {
                    new Settings(ctx).setNativeDiagnosticsEnabled(next);
                }
            } catch (Throwable ignored) {
            }
            updateNativeDiagnosticsButtonText(v);
            try {
                Context ctx = getContext();
                if (ctx != null && !ok) {
                    Toast.makeText(ctx, getString(R.string.native_diagnostics_unavailable_hint), Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) {
            }
        });

        view.findViewById(R.id.btn_copy_diagnostics).setOnClickListener(v -> {
            copyText(summary);
            toastCopied();
        });
        view.findViewById(R.id.btn_copy_logs).setOnClickListener(v -> {
            copyText(logs);
            toastCopied();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            android.app.Dialog dialog = getDialog();
            if (dialog == null) {
                return;
            }
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) {
                return;
            }
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } catch (Throwable ignored) {
        }
    }

    private void updateNativeDiagnosticsButtonText(View button) {
        if (!(button instanceof com.google.android.material.button.MaterialButton)) {
            return;
        }
        com.google.android.material.button.MaterialButton b = (com.google.android.material.button.MaterialButton) button;
        String state;
        if (!NativeFFmpegDiagnostics.isAvailable()) {
            state = getString(R.string.native_diagnostics_unavailable);
            b.setAlpha(0.6f);
        } else {
            state = NativeFFmpegDiagnostics.isDiagnosticsEnabledSafe() ? getString(R.string.native_diagnostics_on) : getString(R.string.native_diagnostics_off);
            b.setAlpha(1.0f);
        }
        b.setText(getString(R.string.native_diagnostics) + ": " + state);
    }

    private void toastCopied() {
        try {
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, getString(R.string.copied), Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
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
