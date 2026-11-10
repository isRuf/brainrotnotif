package com.brainrotnotif.ui;

import android.content.Context;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.brainrotnotif.R;
import com.brainrotnotif.data.AlertMode;

public final class ModeDialog {

    public interface OnPicked {
        void onPicked(AlertMode mode);
    }

    private ModeDialog() {
    }

    @StringRes
    public static int labelRes(AlertMode mode) {
        switch (mode) {
            case BANNER:
                return R.string.mode_banner;
            case FULLSCREEN:
                return R.string.mode_fullscreen;
            case OFF:
            default:
                return R.string.mode_off;
        }
    }

    public static void show(Context context, AlertMode current, OnPicked callback) {
        AlertMode[] modes = AlertMode.values();
        String[] labels = new String[modes.length];
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = context.getString(labelRes(modes[i]));
            if (modes[i] == current) {
                checked = i;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.mode_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    callback.onPicked(modes[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
