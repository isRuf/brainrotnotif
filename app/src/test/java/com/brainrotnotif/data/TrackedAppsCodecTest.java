package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrackedAppsCodecTest {

    @Test
    public void roundTripPreservesEverything() {
        List<TrackedApp> apps = Arrays.asList(
                new TrackedApp("com.google.android.youtube", true, 15),
                new TrackedApp("com.instagram.android", false, 45));

        List<TrackedApp> decoded = TrackedAppsCodec.decode(TrackedAppsCodec.encode(apps));

        assertEquals(2, decoded.size());
        assertEquals("com.google.android.youtube", decoded.get(0).packageName);
        assertTrue(decoded.get(0).overlayEnabled);
        assertEquals(15, decoded.get(0).limitMinutes);
        assertEquals("com.instagram.android", decoded.get(1).packageName);
        assertEquals(false, decoded.get(1).overlayEnabled);
        assertEquals(45, decoded.get(1).limitMinutes);
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
    public void encodesEmptyListToEmptyString() {
        assertEquals("", TrackedAppsCodec.encode(new ArrayList<>()));
    }
}
