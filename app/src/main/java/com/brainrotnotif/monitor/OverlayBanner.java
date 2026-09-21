package com.brainrotnotif.monitor;

import android.content.Context;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.util.Formats;

public final class OverlayBanner {

    private final Context themed;
    private final WindowManager windowManager;
    private View view;

    public OverlayBanner(Context context) {
        this.themed = new ContextThemeWrapper(
                context.getApplicationContext(), R.style.Theme_BrainRotNotif);
        this.windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(String packageName, long elapsedMs) {
        hide();
        if (windowManager == null || !Settings.canDrawOverlays(themed)) {
            return;
        }

        View banner = LayoutInflater.from(themed).inflate(R.layout.overlay_banner, null);
        ((ImageView) banner.findViewById(R.id.icon))
                .setImageDrawable(InstalledApps.icon(themed, packageName));
        ((TextView) banner.findViewById(R.id.text)).setText(
                "Ты в " + InstalledApps.label(themed, packageName)
                        + " уже " + Formats.duration(elapsedMs));
        banner.findViewById(R.id.ok).setOnClickListener(v -> hide());

        ViewCompat.setOnApplyWindowInsetsListener(banner, (target, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            target.setPadding(
                    target.getPaddingLeft(),
                    bars.top + target.getPaddingTop(),
                    target.getPaddingRight(),
                    target.getPaddingBottom());
            return insets;
        });

        // Окно touchable: у баннера своя кнопка. FLAG_NOT_TOUCHABLE ставить нельзя —
        // Android 12+ блокирует сквозные касания при непрозрачности выше 0.8.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        try {
            windowManager.addView(banner, params);
            view = banner;
        } catch (WindowManager.BadTokenException | IllegalStateException e) {
            view = null;
        }
    }

    public void hide() {
        if (view == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException e) {
            // Окно уже снято системой.
        }
        view = null;
    }
}
