package com.brainrotnotif.data;

public final class AppEvent {

    public static final int FOREGROUND = 1;
    public static final int BACKGROUND = 2;

    /** Пакет приложения. null в BACKGROUND означает «всё ушло с переднего плана». */
    public final String packageName;
    public final long timestamp;
    public final int type;

    public AppEvent(String packageName, long timestamp, int type) {
        this.packageName = packageName;
        this.timestamp = timestamp;
        this.type = type;
    }
}
