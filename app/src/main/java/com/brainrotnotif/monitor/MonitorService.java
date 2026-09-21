package com.brainrotnotif.monitor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.ui.MainActivity;
import com.brainrotnotif.util.Formats;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MonitorService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final long GRACE_MS = 60_000L;

    private static final long TICK_MAX_MS = 5_000L;
    private static final long TICK_MIN_MS = 250L;
    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIFICATION_ID = 1;

    private HandlerThread thread;
    private Handler handler;
    private UsageTracker tracker;
    private TrackedAppsStore store;
    private volatile List<TrackedApp> cache;
    private final Map<String, Integer> fired = new HashMap<>();
    private String lastNotificationText;
    private Handler mainHandler;
    private OverlayBanner banner;
    private volatile String bannerPackage;

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, MonitorService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, MonitorService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        store = new TrackedAppsStore(this);
        store.registerListener(this);
        cache = store.getAll();
        tracker = new UsageTracker(
                new SystemUsageEventReader(this), ZoneId.systemDefault(), GRACE_MS);
        thread = new HandlerThread("monitor");
        thread.start();
        handler = new Handler(thread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        banner = new OverlayBanner(this);
    }

    @SuppressLint("InlinedApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : 0;
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(notificationText(null, 0)),
                type);

        if (!Permissions.hasUsageAccess(this) || !store.isMonitoringEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        scheduleTick(0);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideBanner();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (thread != null) {
            thread.quitSafely();
        }
        store.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, @Nullable String key) {
        cache = store.getAll();
    }

    protected void showBanner(String packageName, long elapsedMs) {
        bannerPackage = packageName;
        mainHandler.post(() -> banner.show(packageName, elapsedMs));
    }

    protected void hideBanner() {
        if (bannerPackage == null) {
            return;
        }
        bannerPackage = null;
        if (mainHandler != null) {
            mainHandler.post(() -> banner.hide());
        }
    }

    protected void scheduleTick(long delayMs) {
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delayMs);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            tracker.advanceTo(now);

            String pkg = tracker.getForegroundPackage();
            TrackedApp app = pkg == null ? null : find(pkg);
            long delay = TICK_MAX_MS;
            long elapsed = 0;

            if (app != null) {
                elapsed = tracker.getSessionDurationMs(pkg);
            }

            if (app != null && app.overlayEnabled) {
                long limitMs = app.limitMinutes * 60_000L;
                String key = pkg + "@" + tracker.getSessionStartMs(pkg);
                Integer stored = fired.get(key);
                int count = stored == null ? 0 : stored;
                long threshold = (count + 1) * limitMs;
                if (elapsed >= threshold) {
                    fired.put(key, count + 1);
                    threshold += limitMs;
                    showBanner(pkg, elapsed);
                }
                delay = Math.max(TICK_MIN_MS, Math.min(TICK_MAX_MS, threshold - elapsed));
            } else {
                hideBanner();
            }

            pruneFired();
            updateNotification(pkg, elapsed);
            handler.postDelayed(this, delay);
        }
    };

    private TrackedApp find(String packageName) {
        for (TrackedApp app : cache) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    private void pruneFired() {
        Iterator<String> it = fired.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            int at = key.lastIndexOf('@');
            String pkg = key.substring(0, at);
            long start;
            try {
                start = Long.parseLong(key.substring(at + 1));
            } catch (NumberFormatException e) {
                it.remove();
                continue;
            }
            if (tracker.getSessionStartMs(pkg) != start) {
                it.remove();
            }
        }
    }

    private String notificationText(String packageName, long elapsedMs) {
        if (packageName == null) {
            int count = cache == null ? 0 : cache.size();
            return "Отслеживаю приложений: " + count;
        }
        return InstalledApps.label(this, packageName) + " — " + Formats.duration(elapsedMs);
    }

    private void updateNotification(String packageName, long elapsedMs) {
        String text = notificationText(packageName, elapsedMs);
        if (text.equals(lastNotificationText)) {
            return;
        }
        lastNotificationText = text;
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text));
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS не выдано — сервис работает без видимого уведомления.
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("BrainRotNotif")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Мониторинг", NotificationManager.IMPORTANCE_LOW));
    }
}
