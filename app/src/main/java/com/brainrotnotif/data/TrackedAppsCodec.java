package com.brainrotnotif.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Построчный формат: packageName \t mode \t limitMinutes, где mode — код {@link AlertMode}.
 * Записи, созданные до появления полноэкранного режима, хранят 0 или 1, то есть читаются
 * как OFF и BANNER без миграции. Имена пакетов не содержат табов и переводов строки,
 * поэтому формат однозначен.
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
                    .append(app.mode.code)
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
            int modeCode;
            int limit;
            try {
                modeCode = Integer.parseInt(parts[1]);
                limit = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            AlertMode mode = AlertMode.fromCode(modeCode);
            if (mode == null) {
                continue;
            }
            out.add(new TrackedApp(parts[0], mode, limit));
        }
        return out;
    }
}
