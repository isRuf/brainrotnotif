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
import com.brainrotnotif.util.Formats;

import java.util.ArrayList;
import java.util.List;

public class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.Holder> {

    public static final class Row {
        public final String packageName;
        public final long totalMs;

        public Row(String packageName, long totalMs) {
            this.packageName = packageName;
            this.totalMs = totalMs;
        }
    }

    private final Context context;
    private final List<Row> rows = new ArrayList<>();

    public StatsAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        holder.icon.setImageDrawable(InstalledApps.icon(context, row.packageName));
        holder.label.setText(InstalledApps.label(context, row.packageName));
        holder.time.setText(Formats.duration(row.totalMs));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView time;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            time = itemView.findViewById(R.id.time);
        }
    }
}
