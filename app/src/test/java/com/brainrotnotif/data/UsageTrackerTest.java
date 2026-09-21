package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Map;

public class UsageTrackerTest {

    static final ZoneId UTC = ZoneId.of("UTC");
    static final long DAY = 86_400_000L;
    static final long MIN = 60_000L;
    static final long GRACE = 60_000L;

    /** Полдень условных суток. */
    static long t(long offsetMs) {
        return 1_000 * DAY + 12 * 60 * MIN + offsetMs;
    }

    static UsageTracker tracker(FakeUsageEventReader reader) {
        return new UsageTracker(reader, UTC, GRACE);
    }

    @Test
    public void reportsForegroundPackage() {
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", t(0));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        assertEquals("yt", tracker.getForegroundPackage());
    }

    @Test
    public void reportsNothingAfterBackground() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        assertNull(tracker.getForegroundPackage());
    }

    @Test
    public void totalsIncludeRunningSegment() {
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", t(0));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        assertEquals(Long.valueOf(5 * MIN), tracker.getTodayTotals().get("yt"));
    }

    @Test
    public void totalsSumSeparateSegments() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(3 * MIN))
                .fg("yt", t(10 * MIN))
                .bg("yt", t(14 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(20 * MIN));

        assertEquals(Long.valueOf(7 * MIN), tracker.getTodayTotals().get("yt"));
    }

    @Test
    public void totalsTrackSeveralPackages() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .fg("ig", t(2 * MIN))
                .bg("ig", t(5 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        Map<String, Long> totals = tracker.getTodayTotals();
        assertEquals(Long.valueOf(2 * MIN), totals.get("yt"));
        assertEquals(Long.valueOf(3 * MIN), totals.get("ig"));
    }

    @Test
    public void screenOffEndsForegroundSegment() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .screenOff(t(4 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(20 * MIN));

        assertNull(tracker.getForegroundPackage());
        assertEquals(Long.valueOf(4 * MIN), tracker.getTodayTotals().get("yt"));
    }
}
