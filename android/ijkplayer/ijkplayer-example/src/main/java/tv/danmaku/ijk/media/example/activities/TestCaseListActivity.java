package tv.danmaku.ijk.media.example.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.AppActivity;
import tv.danmaku.ijk.media.example.fragments.TestCaseListFragment;

public class TestCaseListActivity extends AppActivity {
    public static final String EXTRA_RAW_RES_ID = "extra_raw_res_id";
    public static final String EXTRA_TITLE = "extra_title";

    public static Intent newIntent(Context context, int rawResId, String title) {
        Intent intent = new Intent(context, TestCaseListActivity.class);
        intent.putExtra(EXTRA_RAW_RES_ID, rawResId);
        intent.putExtra(EXTRA_TITLE, title);
        return intent;
    }

    public static void intentTo(Context context, int rawResId, String title) {
        context.startActivity(newIntent(context, rawResId, title));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int rawResId = getIntent() != null ? getIntent().getIntExtra(EXTRA_RAW_RES_ID, 0) : 0;
        String title = getIntent() != null ? getIntent().getStringExtra(EXTRA_TITLE) : null;
        if (title != null) {
            setTitle(title);
        }

        Fragment newFragment = TestCaseListFragment.newInstance(rawResId);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.body, newFragment);
        transaction.commit();
    }
}

