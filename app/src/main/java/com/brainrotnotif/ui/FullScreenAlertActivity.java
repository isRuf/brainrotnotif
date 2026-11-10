package com.brainrotnotif.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.util.Formats;
import com.google.android.material.button.MaterialButton;

/**
 * Напоминание во весь экран. Выходит на передний план из {@link
 * com.brainrotnotif.monitor.MonitorService} и тем самым выкидывает из залипательного
 * приложения. Кнопка «Ок» и системная «назад» заблокированы {@link #UNLOCK_DELAY_MS},
 * иначе экран закрывается рефлексом и ничего не меняет.
 */
public class FullScreenAlertActivity extends AppCompatActivity {

    /** Пакет приложения задач: если оно установлено, на экране появляется кнопка «Задачи». */
    public static final String SKIPPER_TASKS_PACKAGE = "com.isruf.skippertasks";

    private static final long UNLOCK_DELAY_MS = 5_000L;
    private static final String EXTRA_PACKAGE = "com.brainrotnotif.extra.PACKAGE";
    private static final String EXTRA_ELAPSED_MS = "com.brainrotnotif.extra.ELAPSED_MS";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MaterialButton ok;
    private long unlockAtMs;

    private final OnBackPressedCallback blockBack = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            // Заблокировано до конца отсчёта; дальше колбэк выключается и «назад» работает как обычно.
        }
    };

    public static Intent intent(Context context, String packageName, long elapsedMs) {
        return new Intent(context, FullScreenAlertActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_ELAPSED_MS, elapsedMs);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_alert);
        // Фон заливает экран целиком, а содержимое держим вне системных панелей.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ok = findViewById(R.id.ok);
        ok.setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, blockBack);
        render(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        render(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void render(Intent intent) {
        String packageName = intent.getStringExtra(EXTRA_PACKAGE);
        long elapsedMs = intent.getLongExtra(EXTRA_ELAPSED_MS, 0L);

        ((ImageView) findViewById(R.id.icon))
                .setImageDrawable(InstalledApps.icon(this, packageName));
        ((TextView) findViewById(R.id.label))
                .setText(InstalledApps.label(this, packageName));
        ((TextView) findViewById(R.id.duration)).setText(Formats.duration(elapsedMs));

        MaterialButton tasks = findViewById(R.id.tasks);
        Intent launch = getPackageManager().getLaunchIntentForPackage(SKIPPER_TASKS_PACKAGE);
        if (launch == null) {
            tasks.setVisibility(android.view.View.GONE);
        } else {
            tasks.setVisibility(android.view.View.VISIBLE);
            tasks.setOnClickListener(v -> {
                startActivity(launch);
                finish();
            });
        }

        unlockAtMs = SystemClock.elapsedRealtime() + UNLOCK_DELAY_MS;
        blockBack.setEnabled(true);
        handler.removeCallbacksAndMessages(null);
        countdown.run();
    }

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            long leftMs = unlockAtMs - SystemClock.elapsedRealtime();
            if (leftMs <= 0) {
                ok.setEnabled(true);
                ok.setText(R.string.alert_ok);
                blockBack.setEnabled(false);
                return;
            }
            int seconds = (int) ((leftMs + 999) / 1000);
            ok.setEnabled(false);
            ok.setText(getString(R.string.alert_ok_countdown, seconds));
            handler.postDelayed(this, Math.min(leftMs, 250L));
        }
    };
}
