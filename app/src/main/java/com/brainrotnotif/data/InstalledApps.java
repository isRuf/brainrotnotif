package com.brainrotnotif.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstalledApps {

    public static final class Entry {
        public final String packageName;
        public final String label;

        public Entry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private InstalledApps() {
    }

    public static List<Entry> launchable(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        List<Entry> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || seen.contains(pkg)) {
                continue;
            }
            seen.add(pkg);
            out.add(new Entry(pkg, info.loadLabel(pm).toString()));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static String label(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    public static Drawable icon(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
