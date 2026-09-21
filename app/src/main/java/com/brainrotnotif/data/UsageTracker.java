package com.brainrotnotif.data;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class UsageTracker {

    private final UsageEventReader reader;
    private final ZoneId zone;
    private final long graceMs;

    private final Map<String, Long> todayTotals = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();

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
        if (nowMs > cursorMs) {
            List<AppEvent> events = reader.read(cursorMs, nowMs);
            for (AppEvent e : events) {
                maybeRollDay(e.timestamp);
                apply(e);
            }
            maybeRollDay(nowMs);
            cursorMs = nowMs;
        }
        this.nowMs = Math.max(this.nowMs, nowMs);
        pruneSessions(this.nowMs);
    }

    public String getForegroundPackage() {
        return currentPkg;
    }

    public long getSessionDurationMs(String pkg) {
        Session s = sessions.get(pkg);
        if (s == null) {
            return 0;
        }
        long duration = s.accumulatedMs;
        if (s.resumedAtMs > 0) {
            duration += Math.max(0, nowMs - s.resumedAtMs);
        }
        return duration;
    }

    public long getSessionStartMs(String pkg) {
        Session s = sessions.get(pkg);
        return s == null ? 0 : s.startMs;
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
            openSession(e.packageName, e.timestamp);
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
        suspendSession(currentPkg, atMs);
        currentSinceMs = 0;
    }

    /**
     * Обрезка сегментов по началу суток уже делается в closeCurrent и
     * getTodayTotals, поэтому здесь достаточно обнулить накопленное и сдвинуть
     * границу дня. Сессии смену суток переживают.
     */
    private void maybeRollDay(long atMs) {
        long start = startOfDay(atMs);
        if (start != dayStartMs) {
            todayTotals.clear();
            dayStartMs = start;
        }
    }

    private void openSession(String pkg, long atMs) {
        Session s = sessions.get(pkg);
        if (s != null && s.resumedAtMs == 0 && atMs - s.suspendedAtMs > graceMs) {
            s = null;
        }
        if (s == null) {
            s = new Session();
            s.startMs = atMs;
            sessions.put(pkg, s);
        }
        s.resumedAtMs = atMs;
        s.suspendedAtMs = 0;
    }

    private void suspendSession(String pkg, long atMs) {
        Session s = sessions.get(pkg);
        if (s == null || s.resumedAtMs == 0) {
            return;
        }
        s.accumulatedMs += atMs - s.resumedAtMs;
        s.resumedAtMs = 0;
        s.suspendedAtMs = atMs;
    }

    private void pruneSessions(long atMs) {
        Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            if (s.resumedAtMs == 0 && atMs - s.suspendedAtMs > graceMs) {
                it.remove();
            }
        }
    }

    private long startOfDay(long ms) {
        return Instant.ofEpochMilli(ms)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
    }

    private static final class Session {
        long startMs;
        long accumulatedMs;
        long resumedAtMs;
        long suspendedAtMs;
    }

    private static void add(Map<String, Long> map, String key, long value) {
        Long prev = map.get(key);
        map.put(key, prev == null ? value : prev + value);
    }
}
