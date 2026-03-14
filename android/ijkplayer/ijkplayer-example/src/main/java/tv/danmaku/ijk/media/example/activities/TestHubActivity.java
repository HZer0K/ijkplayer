package tv.danmaku.ijk.media.example.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.AppActivity;
import tv.danmaku.ijk.media.example.fragments.TestHubFragment;

public class TestHubActivity extends AppActivity {
    public static final String EXTRA_MODE = "extra_mode";

    public static final String MODE_CONTENT = "content";

    public static Intent newIntent(Context context, String mode) {
        Intent intent = new Intent(context, TestHubActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        return intent;
    }

    public static void intentTo(Context context, String mode) {
        context.startActivity(newIntent(context, mode));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String mode = getIntent() != null ? getIntent().getStringExtra(EXTRA_MODE) : null;
        Fragment newFragment = TestHubFragment.newInstance(mode);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.body, newFragment);
        transaction.commit();
    }
}

