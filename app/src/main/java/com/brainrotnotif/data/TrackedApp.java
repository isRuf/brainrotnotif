package com.brainrotnotif.data;

public final class TrackedApp {

    public final String packageName;
    public final boolean overlayEnabled;
    public final int limitMinutes;

    public TrackedApp(String packageName, boolean overlayEnabled, int limitMinutes) {
        this.packageName = packageName;
        this.overlayEnabled = overlayEnabled;
        this.limitMinutes = limitMinutes;
    }

    public TrackedApp withOverlayEnabled(boolean value) {
        return new TrackedApp(packageName, value, limitMinutes);
    }

    public TrackedApp withLimitMinutes(int value) {
        return new TrackedApp(packageName, overlayEnabled, value);
    }
}
