package tv.danmaku.ijk.media.example.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import tv.danmaku.ijk.media.example.R;

public class TracksBottomSheetDialogFragment extends BottomSheetDialogFragment {
    public static TracksBottomSheetDialogFragment newInstance() {
        return new TracksBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_tracks, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            TracksFragment f = TracksFragment.newInstance();
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.tracks_container, f)
                    .commit();
        }
    }
}

