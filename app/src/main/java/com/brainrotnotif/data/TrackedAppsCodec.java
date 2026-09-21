package com.brainrotnotif.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Построчный формат: packageName \t enabled(0|1) \t limitMinutes.
 * Имена пакетов не содержат табов и переводов строки, поэтому формат однозначен.
 */
public final class TrackedAppsCodec {

    private TrackedAppsCodec() {
    }

    public static String encode(List<TrackedApp> apps) {
        StringBuilder sb = new StringBuilder();
        for (TrackedApp app : apps) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(app.packageName)
                    .append('\t')
                    .append(app.overlayEnabled ? '1' : '0')
                    .append('\t')
                    .append(app.limitMinutes);
        }
        return sb.toString();
    }

    public static List<TrackedApp> decode(String raw) {
        List<TrackedApp> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length != 3 || parts[0].isEmpty()) {
                continue;
            }
            int limit;
            try {
                limit = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(new TrackedApp(parts[0], "1".equals(parts[1]), limit));
        }
        return out;
    }
}
