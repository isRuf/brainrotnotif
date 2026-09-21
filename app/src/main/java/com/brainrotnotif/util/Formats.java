package com.brainrotnotif.util;

public final class Formats {

    private Formats() {
    }

    public static String duration(long ms) {
        long totalMinutes = ms / 60_000L;
        if (totalMinutes <= 0) {
            return "меньше минуты";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + " мин";
        }
        return hours + " ч " + minutes + " мин";
    }
}
