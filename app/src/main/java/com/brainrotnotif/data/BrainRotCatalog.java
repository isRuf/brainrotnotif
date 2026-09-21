package com.brainrotnotif.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BrainRotCatalog {

    /** Подтверждённые имена пакетов. */
    private static final List<String> PACKAGES = Arrays.asList(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.vkontakte.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.pinterest",
            "org.telegram.messenger");

    /** Префиксы для приложений, точные имена которых не подтверждены. */
    private static final List<String> PREFIXES = Arrays.asList(
            "com.vk.",
            "ru.vk.",
            "ru.zen.",
            "ru.rutube.",
            "video.like");

    private BrainRotCatalog() {
    }

    public static List<String> detect(List<InstalledApps.Entry> installed) {
        List<String> out = new ArrayList<>();
        for (InstalledApps.Entry entry : installed) {
            if (matches(entry.packageName)) {
                out.add(entry.packageName);
            }
        }
        return out;
    }

    public static int seed(Context context, TrackedAppsStore store) {
        int added = 0;
        for (String pkg : detect(InstalledApps.launchable(context))) {
            if (store.get(pkg) == null) {
                store.put(new TrackedApp(pkg, true, TrackedAppsStore.DEFAULT_LIMIT_MINUTES));
                added++;
            }
        }
        return added;
    }

    private static boolean matches(String packageName) {
        if (PACKAGES.contains(packageName)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
