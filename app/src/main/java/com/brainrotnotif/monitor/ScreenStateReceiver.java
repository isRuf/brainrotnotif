package com.brainrotnotif.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenStateReceiver extends BroadcastReceiver {

    public interface Listener {
        void onScreenOn();

        void onScreenOff();
    }

    private final Listener listener;

    public ScreenStateReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            listener.onScreenOn();
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            listener.onScreenOff();
        }
    }
}
