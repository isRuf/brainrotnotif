package com.brainrotnotif.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.brainrotnotif.R;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.util.Formats;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        TextView dump = findViewById(R.id.dump);
        if (!Permissions.hasUsageAccess(this)) {
            dump.setText("Нет доступа к статистике использования. Открываю настройки.");
            Permissions.openUsageAccessSettings(this);
            return;
        }
        UsageTracker tracker = new UsageTracker(
                new SystemUsageEventReader(this), ZoneId.systemDefault(), 60_000L);
        tracker.advanceTo(System.currentTimeMillis());

        StringBuilder sb = new StringBuilder();
        sb.append("Сейчас: ").append(tracker.getForegroundPackage()).append("\n\n");
        for (Map.Entry<String, Long> e : tracker.getTodayTotals().entrySet()) {
            sb.append(e.getKey()).append(" — ").append(Formats.duration(e.getValue())).append('\n');
        }
        dump.setText(sb.toString());
    }
}
