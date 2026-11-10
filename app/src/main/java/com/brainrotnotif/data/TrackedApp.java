package com.brainrotnotif.data;

public final class TrackedApp {

    public final String packageName;
    public final AlertMode mode;
    public final int limitMinutes;

    public TrackedApp(String packageName, AlertMode mode, int limitMinutes) {
        this.packageName = packageName;
        this.mode = mode;
        this.limitMinutes = limitMinutes;
    }

    public TrackedApp withMode(AlertMode value) {
        return new TrackedApp(packageName, value, limitMinutes);
    }

    public TrackedApp withLimitMinutes(int value) {
        return new TrackedApp(packageName, mode, value);
    }
}
