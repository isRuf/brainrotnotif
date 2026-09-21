package com.brainrotnotif.data;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UsageTracker {

    private final UsageEventReader reader;
    private final ZoneId zone;
    private final long graceMs;

    private final Map<String, Long> todayTotals = new HashMap<>();

    private boolean initialized = false;
    private long cursorMs;
    private long dayStartMs;
    private long nowMs;

    private String currentPkg;
    private long currentSinceMs;

    public UsageTracker(UsageEventReader reader, ZoneId zone, long graceMs) {
        this.reader = reader;
        this.zone = zone;
        this.graceMs = graceMs;
    }

    public void advanceTo(long nowMs) {
        if (!initialized) {
            dayStartMs = startOfDay(nowMs);
            cursorMs = dayStartMs;
            this.nowMs = dayStartMs;
            initialized = true;
        }
        if (nowMs <= cursorMs) {
            this.nowMs = Math.max(this.nowMs, nowMs);
            return;
        }
        List<AppEvent> events = reader.read(cursorMs, nowMs);
        for (AppEvent e : events) {
            apply(e);
        }
        cursorMs = nowMs;
        this.nowMs = nowMs;
    }

    public String getForegroundPackage() {
        return currentPkg;
    }

    public Map<String, Long> getTodayTotals() {
        Map<String, Long> out = new HashMap<>(todayTotals);
        if (currentPkg != null) {
            long from = Math.max(currentSinceMs, dayStartMs);
            if (nowMs > from) {
                add(out, currentPkg, nowMs - from);
            }
        }
        return out;
    }

    private void apply(AppEvent e) {
        if (e.type == AppEvent.FOREGROUND) {
            if (e.packageName == null || e.packageName.equals(currentPkg)) {
                return;
            }
            closeCurrent(e.timestamp);
            currentPkg = e.packageName;
            currentSinceMs = e.timestamp;
        } else {
            if (e.packageName == null || e.packageName.equals(currentPkg)) {
                closeCurrent(e.timestamp);
                currentPkg = null;
            }
        }
    }

    private void closeCurrent(long atMs) {
        if (currentPkg == null) {
            return;
        }
        long from = Math.max(currentSinceMs, dayStartMs);
        if (atMs > from) {
            add(todayTotals, currentPkg, atMs - from);
        }
        currentSinceMs = 0;
    }

    private long startOfDay(long ms) {
        return Instant.ofEpochMilli(ms)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
    }

    private static void add(Map<String, Long> map, String key, long value) {
        Long prev = map.get(key);
        map.put(key, prev == null ? value : prev + value);
    }
}
