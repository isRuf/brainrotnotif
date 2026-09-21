package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BrainRotCatalogTest {

    private static InstalledApps.Entry app(String pkg) {
        return new InstalledApps.Entry(pkg, pkg);
    }

    @Test
    public void detectsKnownPackages() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("com.google.android.youtube"),
                app("com.example.calculator"),
                app("com.instagram.android")));

        assertEquals(2, found.size());
        assertTrue(found.contains("com.google.android.youtube"));
        assertTrue(found.contains("com.instagram.android"));
    }

    @Test
    public void detectsByPackagePrefix() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("com.vk.im"),
                app("ru.zen.android")));

        assertEquals(2, found.size());
    }

    @Test
    public void prefixMatchIsAnchoredAtStart() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("org.example.com.vk.clone"),
                app("com.vkbeautify")));

        assertTrue(found.isEmpty());
    }

    @Test
    public void ignoresUnknownApps() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("com.android.settings"),
                app("org.mozilla.firefox")));

        assertFalse(found.contains("com.android.settings"));
        assertTrue(found.isEmpty());
    }
}
