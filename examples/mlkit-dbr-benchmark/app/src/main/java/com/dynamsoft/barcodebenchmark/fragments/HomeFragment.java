package com.dynamsoft.barcodebenchmark.fragments;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.dynamsoft.barcodebenchmark.MainViewModel;
import com.dynamsoft.barcodebenchmark.R;
import com.dynamsoft.barcodebenchmark.server.BenchmarkWebServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int SERVER_PORT = 8080;

    private MainViewModel viewModel;
    private BenchmarkWebServer webServer;
    private SwitchCompat switchServer;
    private LinearLayout serverStatusPanel;
    private TextView tvServerUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel.reset();

        // Resolution selection
        android.widget.RadioGroup radioGroup = view.findViewById(R.id.radio_group_resolution);
        if (viewModel.resolutionIndex == 0) {
            radioGroup.check(R.id.radio_720p);
        } else {
            radioGroup.check(R.id.radio_1080p);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_720p) {
                viewModel.resolutionIndex = 0;
            } else if (checkedId == R.id.radio_1080p) {
                viewModel.resolutionIndex = 1;
            }
        });

        // Image benchmark card
        CardView cardImage = view.findViewById(R.id.card_image);
        cardImage.setOnClickListener(v -> {
            viewModel.benchmarkMode = "image";
            Navigation.findNavController(v).navigate(R.id.action_home_to_imageBenchmark);
        });

        // Video benchmark card
        CardView cardVideo = view.findViewById(R.id.card_video);
        cardVideo.setOnClickListener(v -> {
            viewModel.benchmarkMode = "video";
            Navigation.findNavController(v).navigate(R.id.action_home_to_videoBenchmark);
        });

        // Dynamsoft camera card
        CardView cardDynamsoft = view.findViewById(R.id.card_dynamsoft);
        cardDynamsoft.setOnClickListener(v -> {
            viewModel.benchmarkMode = "camera";
            Navigation.findNavController(v).navigate(R.id.action_home_to_dynamsoftScanner);
        });

        // MLkit camera card
        CardView cardGoogle = view.findViewById(R.id.card_google);
        cardGoogle.setOnClickListener(v -> {
            viewModel.benchmarkMode = "camera";
            Navigation.findNavController(v).navigate(R.id.action_home_to_mlkitScanner);
        });

        // Web Server
        switchServer = view.findViewById(R.id.switch_server);
        serverStatusPanel = view.findViewById(R.id.server_status_panel);
        tvServerUrl = view.findViewById(R.id.tv_server_url);

        switchServer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startWebServer();
            } else {
                stopWebServer();
            }
        });
    }

    private void startWebServer() {
        try {
            webServer = new BenchmarkWebServer(requireContext(), SERVER_PORT);
            webServer.start();

            String ipAddress = getLocalIpAddress();
            String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT;
            tvServerUrl.setText(serverUrl);
            serverStatusPanel.setVisibility(View.VISIBLE);

            Toast.makeText(requireContext(), "Web server started on port " + SERVER_PORT, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Web server started: " + serverUrl);

        } catch (IOException e) {
            Log.e(TAG, "Failed to start web server", e);
            Toast.makeText(requireContext(), "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
            switchServer.setChecked(false);
        }
    }

    private void stopWebServer() {
        if (webServer != null) {
            webServer.stop();
            webServer.cleanup();
            webServer = null;
        }
        serverStatusPanel.setVisibility(View.GONE);
        Toast.makeText(requireContext(), "Web server stopped", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Web server stopped");
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        // IPv4
                        if (sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }

        // Fallback to WiFi manager
        try {
            WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int ipInt = wifiManager.getConnectionInfo().getIpAddress();
                return Formatter.formatIpAddress(ipInt);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting WiFi IP address", e);
        }

        return "localhost";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopWebServer();
    }
}
