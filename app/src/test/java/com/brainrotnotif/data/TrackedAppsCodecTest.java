package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrackedAppsCodecTest {

    @Test
    public void roundTripPreservesEveryMode() {
        List<TrackedApp> apps = Arrays.asList(
                new TrackedApp("com.google.android.youtube", AlertMode.BANNER, 15),
                new TrackedApp("com.instagram.android", AlertMode.OFF, 45),
                new TrackedApp("com.zhiliaoapp.musically", AlertMode.FULLSCREEN, 5));

        List<TrackedApp> decoded = TrackedAppsCodec.decode(TrackedAppsCodec.encode(apps));

        assertEquals(3, decoded.size());
        assertEquals("com.google.android.youtube", decoded.get(0).packageName);
        assertEquals(AlertMode.BANNER, decoded.get(0).mode);
        assertEquals(15, decoded.get(0).limitMinutes);
        assertEquals("com.instagram.android", decoded.get(1).packageName);
        assertEquals(AlertMode.OFF, decoded.get(1).mode);
        assertEquals(45, decoded.get(1).limitMinutes);
        assertEquals("com.zhiliaoapp.musically", decoded.get(2).packageName);
        assertEquals(AlertMode.FULLSCREEN, decoded.get(2).mode);
        assertEquals(5, decoded.get(2).limitMinutes);
    }

    @Test
    public void readsRecordsWrittenBeforeFullscreenExisted() {
        List<TrackedApp> decoded = TrackedAppsCodec.decode("com.a\t1\t10\ncom.b\t0\t30");

        assertEquals(2, decoded.size());
        assertEquals(AlertMode.BANNER, decoded.get(0).mode);
        assertEquals(AlertMode.OFF, decoded.get(1).mode);
    }

    @Test
    public void decodesEmptyAndNullToEmptyList() {
        assertTrue(TrackedAppsCodec.decode(null).isEmpty());
        assertTrue(TrackedAppsCodec.decode("").isEmpty());
        assertTrue(TrackedAppsCodec.decode("\n\n").isEmpty());
    }

    @Test
    public void skipsMalformedLines() {
        String raw = "com.a\t1\t10\nсовсем не запись\ncom.b\t1\tне число\ncom.c\t0\t30";

        List<TrackedApp> decoded = TrackedAppsCodec.decode(raw);

        assertEquals(2, decoded.size());
        assertEquals("com.a", decoded.get(0).packageName);
        assertEquals("com.c", decoded.get(1).packageName);
    }

    @Test
    public void skipsLinesWithAnUnknownMode() {
        List<TrackedApp> decoded = TrackedAppsCodec.decode("com.a\t7\t10\ncom.b\t2\t30");

        assertEquals(1, decoded.size());
        assertEquals("com.b", decoded.get(0).packageName);
        assertEquals(AlertMode.FULLSCREEN, decoded.get(0).mode);
    }

    @Test
    public void encodesEmptyListToEmptyString() {
        assertEquals("", TrackedAppsCodec.encode(new ArrayList<>()));
    }
}
