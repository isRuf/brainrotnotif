package com.brainrotnotif.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.util.Permissions;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        TrackedAppsStore store = new TrackedAppsStore(context);
        if (store.isMonitoringEnabled() && Permissions.hasUsageAccess(context)) {
            MonitorService.start(context);
        }
    }
}
