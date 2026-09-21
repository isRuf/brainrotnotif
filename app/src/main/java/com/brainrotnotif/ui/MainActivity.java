package com.brainrotnotif.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.brainrotnotif.R;
import com.brainrotnotif.data.BrainRotCatalog;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.util.Permissions;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private TrackedAppsStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new TrackedAppsStore(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        BottomNavigationView nav = findViewById(R.id.nav);
        nav.setOnItemSelectedListener(item -> {
            show(fragmentFor(item.getItemId()));
            return true;
        });

        if (savedInstanceState == null) {
            seedIfFirstRun();
            boolean ready = Permissions.hasUsageAccess(this) && Permissions.canDrawOverlays(this);
            int startTab = ready ? R.id.tab_apps : R.id.tab_settings;
            show(fragmentFor(startTab));
            nav.setSelectedItemId(startTab);
        }
    }

    @NonNull
    private Fragment fragmentFor(int itemId) {
        // R-класс в AGP 9 не final — switch по id не компилируется.
        if (itemId == R.id.tab_stats) {
            return new StatsFragment();
        }
        if (itemId == R.id.tab_settings) {
            return new SettingsFragment();
        }
        return new AppsFragment();
    }

    private void show(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private void seedIfFirstRun() {
        if (store.isFirstRunDone()) {
            return;
        }
        if (!Permissions.hasUsageAccess(this)) {
            return;
        }
        BrainRotCatalog.seed(this, store);
        store.setFirstRunDone();
    }
}
