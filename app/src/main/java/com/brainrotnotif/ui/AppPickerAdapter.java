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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.Holder> {

    public interface OnPicked {
        void onPicked(InstalledApps.Entry entry);
    }

    private final Context context;
    private final OnPicked callback;
    private final List<InstalledApps.Entry> all = new ArrayList<>();
    private final List<InstalledApps.Entry> visible = new ArrayList<>();

    public AppPickerAdapter(Context context, OnPicked callback) {
        this.context = context;
        this.callback = callback;
    }

    public void submit(List<InstalledApps.Entry> entries) {
        all.clear();
        all.addAll(entries);
        filter("");
    }

    public void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.getDefault());
        visible.clear();
        for (InstalledApps.Entry entry : all) {
            if (needle.isEmpty()
                    || entry.label.toLowerCase(Locale.getDefault()).contains(needle)) {
                visible.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InstalledApps.Entry entry = visible.get(position);
        holder.icon.setImageDrawable(InstalledApps.icon(context, entry.packageName));
        holder.label.setText(entry.label);
        holder.itemView.setOnClickListener(v -> callback.onPicked(entry));
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
        }
    }
}
