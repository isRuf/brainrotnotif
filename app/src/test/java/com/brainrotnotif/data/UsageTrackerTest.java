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

    @Test
    public void sessionDurationEqualsForegroundTime() {
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", t(0));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(7 * MIN));

        assertEquals(7 * MIN, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void shortGapKeepsSessionAndIsNotCounted() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(5 * MIN))
                .fg("tg", t(5 * MIN))
                .bg("tg", t(5 * MIN + 30_000))
                .fg("yt", t(5 * MIN + 30_000));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(8 * MIN));

        // 5 минут до ухода + 2.5 минуты после возврата, пауза в 30 с не считается
        assertEquals(5 * MIN + 150_000, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void longGapStartsNewSession() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(5 * MIN))
                .fg("yt", t(7 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(9 * MIN));

        assertEquals(2 * MIN, tracker.getSessionDurationMs("yt"));
        assertEquals(t(7 * MIN), tracker.getSessionStartMs("yt"));
    }

    @Test
    public void screenOffLongerThanGraceStartsNewSession() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .screenOff(t(3 * MIN))
                .fg("yt", t(10 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(11 * MIN));

        assertEquals(MIN, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void eachPackageHasItsOwnSession() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(2 * MIN))
                .fg("ig", t(2 * MIN))
                .bg("ig", t(3 * MIN))
                .fg("yt", t(3 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(4 * MIN));

        assertEquals(3 * MIN, tracker.getSessionDurationMs("yt"));
        assertEquals(MIN, tracker.getSessionDurationMs("ig"));
        assertEquals(t(0), tracker.getSessionStartMs("yt"));
    }

    @Test
    public void expiredSessionIsForgotten() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(2 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(10 * MIN));

        assertEquals(0, tracker.getSessionDurationMs("yt"));
        assertEquals(0, tracker.getSessionStartMs("yt"));
    }

    @Test
    public void dayRolloverClearsTotalsButKeepsSession() {
        long beforeMidnight = 1_001 * DAY - 10 * MIN;
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", beforeMidnight);
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(beforeMidnight + MIN);
        tracker.advanceTo(1_001 * DAY + 10 * MIN);

        assertEquals(Long.valueOf(10 * MIN), tracker.getTodayTotals().get("yt"));
        assertEquals(20 * MIN, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void totalsCountOnlyTimeSinceStartOfDay() {
        long beforeMidnight = 1_001 * DAY - 10 * MIN;
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", beforeMidnight)
                .bg("yt", 1_001 * DAY + 5 * MIN);
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(beforeMidnight + MIN);
        tracker.advanceTo(1_001 * DAY + 20 * MIN);

        assertEquals(Long.valueOf(5 * MIN), tracker.getTodayTotals().get("yt"));
    }

    @Test
    public void incrementalAdvanceMatchesSingleAdvance() {
        FakeUsageEventReader incrementalReader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(3 * MIN))
                .fg("ig", t(3 * MIN))
                .bg("ig", t(4 * MIN))
                .fg("yt", t(4 * MIN))
                .screenOff(t(9 * MIN))
                .fg("yt", t(12 * MIN));
        FakeUsageEventReader singleReader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(3 * MIN))
                .fg("ig", t(3 * MIN))
                .bg("ig", t(4 * MIN))
                .fg("yt", t(4 * MIN))
                .screenOff(t(9 * MIN))
                .fg("yt", t(12 * MIN));

        UsageTracker incremental = tracker(incrementalReader);
        for (long step = 0; step <= 15; step++) {
            incremental.advanceTo(t(step * MIN));
        }

        UsageTracker single = tracker(singleReader);
        single.advanceTo(t(15 * MIN));

        assertEquals(single.getTodayTotals(), incremental.getTodayTotals());
        assertEquals(single.getSessionDurationMs("yt"), incremental.getSessionDurationMs("yt"));
        assertEquals(single.getSessionStartMs("yt"), incremental.getSessionStartMs("yt"));
        assertEquals(single.getForegroundPackage(), incremental.getForegroundPackage());
    }
}
