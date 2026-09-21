package com.brainrotnotif.ui;

import android.content.Context;
import android.widget.NumberPicker;

import androidx.appcompat.app.AlertDialog;

import com.brainrotnotif.R;

public final class LimitDialog {

    public interface OnPicked {
        void onPicked(int minutes);
    }

    private LimitDialog() {
    }

    public static void show(Context context, int currentMinutes, OnPicked callback) {
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(1);
        picker.setMaxValue(240);
        picker.setValue(Math.max(1, Math.min(240, currentMinutes)));
        picker.setWrapSelectorWheel(false);

        new AlertDialog.Builder(context)
                .setTitle(R.string.limit_title)
                .setView(picker)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> callback.onPicked(picker.getValue()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
