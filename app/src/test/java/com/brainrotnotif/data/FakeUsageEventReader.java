package com.brainrotnotif.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeUsageEventReader implements UsageEventReader {

    private final List<AppEvent> all = new ArrayList<>();
    public int readCalls = 0;

    public FakeUsageEventReader fg(String pkg, long ts) {
        all.add(new AppEvent(pkg, ts, AppEvent.FOREGROUND));
        return this;
    }

    public FakeUsageEventReader bg(String pkg, long ts) {
        all.add(new AppEvent(pkg, ts, AppEvent.BACKGROUND));
        return this;
    }

    public FakeUsageEventReader screenOff(long ts) {
        all.add(new AppEvent(null, ts, AppEvent.BACKGROUND));
        return this;
    }

    @Override
    public List<AppEvent> read(long fromMs, long toMs) {
        readCalls++;
        List<AppEvent> out = new ArrayList<>();
        for (AppEvent e : all) {
            if (e.timestamp >= fromMs && e.timestamp < toMs) {
                out.add(e);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        return out;
    }
}
