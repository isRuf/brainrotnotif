package com.brainrotnotif.data;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SystemUsageEventReader implements UsageEventReader {

    private final UsageStatsManager usageStatsManager;

    public SystemUsageEventReader(Context context) {
        this.usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    @Override
    public List<AppEvent> read(long fromMs, long toMs) {
        List<AppEvent> out = new ArrayList<>();
        if (usageStatsManager == null) {
            return out;
        }
        UsageEvents events = usageStatsManager.queryEvents(fromMs, toMs);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.getNextEvent(event)) {
            AppEvent mapped = map(event);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        return out;
    }

    private static AppEvent map(UsageEvents.Event event) {
        int type = event.getEventType();
        long ts = event.getTimeStamp();
        if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
            return new AppEvent(event.getPackageName(), ts, AppEvent.FOREGROUND);
        }
        if (type == UsageEvents.Event.ACTIVITY_PAUSED
                || type == UsageEvents.Event.ACTIVITY_STOPPED) {
            return new AppEvent(event.getPackageName(), ts, AppEvent.BACKGROUND);
        }
        if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                || type == UsageEvents.Event.KEYGUARD_SHOWN
                || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
            return new AppEvent(null, ts, AppEvent.BACKGROUND);
        }
        return null;
    }
}
