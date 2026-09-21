package com.brainrotnotif.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class TrackedAppsStore {

    public static final int DEFAULT_LIMIT_MINUTES = 15;

    private static final String PREFS = "brainrot";
    private static final String KEY_APPS = "apps";
    private static final String KEY_MONITORING = "monitoring_enabled";
    private static final String KEY_FIRST_RUN_DONE = "first_run_done";

    private final SharedPreferences prefs;

    public TrackedAppsStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<TrackedApp> getAll() {
        return TrackedAppsCodec.decode(prefs.getString(KEY_APPS, ""));
    }

    public TrackedApp get(String packageName) {
        for (TrackedApp app : getAll()) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    public void put(TrackedApp app) {
        List<TrackedApp> updated = new ArrayList<>();
        boolean replaced = false;
        for (TrackedApp existing : getAll()) {
            if (existing.packageName.equals(app.packageName)) {
                updated.add(app);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) {
            updated.add(app);
        }
        save(updated);
    }

    public void remove(String packageName) {
        List<TrackedApp> updated = new ArrayList<>();
        for (TrackedApp existing : getAll()) {
            if (!existing.packageName.equals(packageName)) {
                updated.add(existing);
            }
        }
        save(updated);
    }

    public boolean isMonitoringEnabled() {
        return prefs.getBoolean(KEY_MONITORING, true);
    }

    public void setMonitoringEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_MONITORING, value).apply();
    }

    public boolean isFirstRunDone() {
        return prefs.getBoolean(KEY_FIRST_RUN_DONE, false);
    }

    public void setFirstRunDone() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void save(List<TrackedApp> apps) {
        prefs.edit().putString(KEY_APPS, TrackedAppsCodec.encode(apps)).apply();
    }
}
