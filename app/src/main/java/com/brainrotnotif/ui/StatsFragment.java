package com.brainrotnotif.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.util.Formats;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StatsFragment extends Fragment {

    private StatsAdapter adapter;
    private TextView total;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new StatsAdapter(requireContext());
        total = view.findViewById(R.id.total);

        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!Permissions.hasUsageAccess(requireContext())) {
            total.setText(R.string.no_usage_access);
            adapter.submit(Collections.emptyList());
            return;
        }

        UsageTracker tracker = new UsageTracker(
                new SystemUsageEventReader(requireContext()),
                ZoneId.systemDefault(),
                60_000L);
        tracker.advanceTo(System.currentTimeMillis());
        Map<String, Long> totals = tracker.getTodayTotals();

        List<StatsAdapter.Row> rows = new ArrayList<>();
        long sum = 0;
        for (TrackedApp app : new TrackedAppsStore(requireContext()).getAll()) {
            Long value = totals.get(app.packageName);
            long ms = value == null ? 0 : value;
            rows.add(new StatsAdapter.Row(app.packageName, ms));
            sum += ms;
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.totalMs, a.totalMs));

        total.setText("Сегодня всего: " + Formats.duration(sum));
        adapter.submit(rows);
    }
}
