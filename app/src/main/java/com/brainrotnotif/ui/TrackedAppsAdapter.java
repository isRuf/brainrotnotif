package com.brainrotnotif.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.util.Formats;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackedAppsAdapter extends RecyclerView.Adapter<TrackedAppsAdapter.Holder> {

    public interface Callbacks {
        void onOverlayToggled(TrackedApp app, boolean enabled);

        void onLimitClicked(TrackedApp app);

        void onRemoveRequested(TrackedApp app);
    }

    private final Context context;
    private final Callbacks callbacks;
    private final List<TrackedApp> items = new ArrayList<>();
    private final Map<String, Long> todayTotals = new HashMap<>();

    public TrackedAppsAdapter(Context context, Callbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }

    public void submit(List<TrackedApp> apps, Map<String, Long> totals) {
        items.clear();
        items.addAll(apps);
        todayTotals.clear();
        todayTotals.putAll(totals);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tracked_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TrackedApp app = items.get(position);
        Long total = todayTotals.get(app.packageName);

        holder.icon.setImageDrawable(InstalledApps.icon(context, app.packageName));
        holder.label.setText(InstalledApps.label(context, app.packageName));
        holder.subtitle.setText("Лимит " + app.limitMinutes + " мин · сегодня "
                + Formats.duration(total == null ? 0 : total));

        holder.overlay.setOnCheckedChangeListener(null);
        holder.overlay.setChecked(app.overlayEnabled);
        holder.overlay.setOnCheckedChangeListener(
                (button, checked) -> callbacks.onOverlayToggled(app, checked));

        holder.itemView.setOnClickListener(v -> callbacks.onLimitClicked(app));
        holder.itemView.setOnLongClickListener(v -> {
            callbacks.onRemoveRequested(app);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView subtitle;
        final MaterialSwitch overlay;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            subtitle = itemView.findViewById(R.id.subtitle);
            overlay = itemView.findViewById(R.id.overlay);
        }
    }
}
