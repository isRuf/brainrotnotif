package com.brainrotnotif.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;

public class AppPickerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        TrackedAppsStore store = new TrackedAppsStore(this);
        AppPickerAdapter adapter = new AppPickerAdapter(this, entry -> {
            if (store.get(entry.packageName) != null) {
                Toast.makeText(this, "Уже в списке", Toast.LENGTH_SHORT).show();
                return;
            }
            store.put(new TrackedApp(entry.packageName, true,
                    TrackedAppsStore.DEFAULT_LIMIT_MINUTES));
            Toast.makeText(this, entry.label + " добавлено", Toast.LENGTH_SHORT).show();
            finish();
        });

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        adapter.submit(InstalledApps.launchable(this));

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
