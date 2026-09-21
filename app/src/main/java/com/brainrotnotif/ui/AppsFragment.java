package com.brainrotnotif.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;

public class AppsFragment extends Fragment implements TrackedAppsAdapter.Callbacks {

    private TrackedAppsStore store;
    private TrackedAppsAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        store = new TrackedAppsStore(requireContext());
        adapter = new TrackedAppsAdapter(requireContext(), this);
        empty = view.findViewById(R.id.empty);

        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.add).setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(), AppPickerActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Map<String, Long> totals = Collections.emptyMap();
        if (Permissions.hasUsageAccess(requireContext())) {
            UsageTracker tracker = new UsageTracker(
                    new SystemUsageEventReader(requireContext()),
                    ZoneId.systemDefault(),
                    60_000L);
            tracker.advanceTo(System.currentTimeMillis());
            totals = tracker.getTodayTotals();
        }
        adapter.submit(store.getAll(), totals);
        empty.setVisibility(store.getAll().isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOverlayToggled(TrackedApp app, boolean enabled) {
        store.put(app.withOverlayEnabled(enabled));
    }

    @Override
    public void onLimitClicked(TrackedApp app) {
        LimitDialog.show(requireContext(), app.limitMinutes, minutes -> {
            store.put(app.withLimitMinutes(minutes));
            refresh();
        });
    }

    @Override
    public void onRemoveRequested(TrackedApp app) {
        new AlertDialog.Builder(requireContext())
                .setMessage("Убрать " + InstalledApps.label(requireContext(), app.packageName)
                        + " из списка?")
                .setPositiveButton("Убрать", (dialog, which) -> {
                    store.remove(app.packageName);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
