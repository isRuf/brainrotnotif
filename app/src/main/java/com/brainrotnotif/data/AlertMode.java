package com.brainrotnotif.data;

import androidx.annotation.Nullable;

/** Как приложение напоминает о превышенном лимите. Код пишется в хранилище, менять его нельзя. */
public enum AlertMode {

    OFF(0),
    BANNER(1),
    FULLSCREEN(2);

    public final int code;

    AlertMode(int code) {
        this.code = code;
    }

    @Nullable
    public static AlertMode fromCode(int code) {
        for (AlertMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return null;
    }
}
