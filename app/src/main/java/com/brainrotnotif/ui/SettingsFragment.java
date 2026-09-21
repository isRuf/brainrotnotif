package com.brainrotnotif.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.brainrotnotif.R;
import com.brainrotnotif.data.BrainRotCatalog;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.monitor.MonitorService;
import com.brainrotnotif.util.Permissions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {

    private TrackedAppsStore store;
    private View root;
    private ActivityResultLauncher<String> notificationsLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> refresh());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        store = new TrackedAppsStore(requireContext());

        MaterialSwitch monitoring = view.findViewById(R.id.monitoring);
        monitoring.setChecked(store.isMonitoringEnabled());
        monitoring.setOnCheckedChangeListener((button, checked) -> {
            store.setMonitoringEnabled(checked);
            if (checked) {
                MonitorService.start(requireContext());
            } else {
                MonitorService.stop(requireContext());
            }
        });

        view.findViewById(R.id.rescan).setOnClickListener(v -> {
            int added = BrainRotCatalog.seed(requireContext(), store);
            Toast.makeText(requireContext(),
                    added == 0 ? "Новых соцсетей не найдено" : "Добавлено: " + added,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean usage = Permissions.hasUsageAccess(requireContext());
        boolean overlay = Permissions.canDrawOverlays(requireContext());

        root.findViewById(R.id.warning)
                .setVisibility(usage && overlay ? View.GONE : View.VISIBLE);

        bind(R.id.perm_usage, "Доступ к статистике использования", usage,
                v -> Permissions.openUsageAccessSettings(requireContext()));
        bind(R.id.perm_overlay, "Показ поверх других приложений", overlay,
                v -> Permissions.openOverlaySettings(requireContext()));
        bind(R.id.perm_notifications, "Уведомления", notificationsGranted(),
                v -> notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS));
        bind(R.id.perm_battery, "Оптимизация батареи отключена",
                Permissions.isIgnoringBatteryOptimizations(requireContext()),
                v -> Permissions.requestIgnoreBatteryOptimizations(requireContext()));
    }

    private boolean notificationsGranted() {
        return ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void bind(int includeId, String title, boolean granted, View.OnClickListener action) {
        View card = root.findViewById(includeId);
        ((TextView) card.findViewById(R.id.title)).setText(title);
        ((TextView) card.findViewById(R.id.status)).setText(granted ? "Выдано" : "Не выдано");
        MaterialButton button = card.findViewById(R.id.action);
        button.setVisibility(granted ? View.GONE : View.VISIBLE);
        button.setOnClickListener(action);
    }
}
