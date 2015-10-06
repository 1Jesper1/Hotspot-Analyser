package com.hro.hotspotanalyser.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.hro.hotspotanalyser.events.WifiScanResultsEvent;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import de.greenrobot.event.EventBus;

public class WifiReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = WifiReceiver.class.getSimpleName();
    private static final String WALLED_GARDEN_URL = "http://clients3.google.com/generate_204";
    private static final int WALLED_GARDEN_SOCKET_TIMEOUT_MS = 10000;

    private final EventBus mBus = EventBus.getDefault();
    private final Handler mTimeoutHandler = new Handler();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            WifiScanResultsEvent event = new WifiScanResultsEvent(
                    wifiManager.getScanResults()
            );

            mBus.post(event);
        }

        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action) && wifiManager.isWifiEnabled()) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

            if (info != null && info.isConnected()) {
                new AnalyzeNetworkTask().execute(wifiManager);
            }
        }

    }

    private static final class AnalyzeNetworkTask extends AsyncTask<WifiManager, Void, Void> {
        @Override
        protected Void doInBackground(WifiManager... wifiManagers) {
            WifiManager wifiManager = wifiManagers[0];

            double safetyMeter = 0.0;
            WifiConfiguration currentConfig = null;

            for (WifiConfiguration conf : wifiManager.getConfiguredNetworks()) {
                if (conf.status == WifiConfiguration.Status.CURRENT) {
                    currentConfig = conf;
                }
            }

            // Only analyze when there's no protection
            if (currentConfig != null && currentConfig.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
                boolean isCaptivePortal = checkCaptivePortal(wifiManager);
                Log.d(LOG_TAG, "Is captive portal: " + isCaptivePortal);
            }

            return null;
        }

        private boolean checkCaptivePortal(WifiManager wifiManager) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            NetworkInfo.DetailedState detailedState = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());

            if (detailedState == NetworkInfo.DetailedState.CAPTIVE_PORTAL_CHECK) {
                return true;
            } else {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(WALLED_GARDEN_URL); // "http://clients3.google.com/generate_204"

                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setInstanceFollowRedirects(false);
                    urlConnection.setConnectTimeout(WALLED_GARDEN_SOCKET_TIMEOUT_MS);
                    urlConnection.setReadTimeout(WALLED_GARDEN_SOCKET_TIMEOUT_MS);
                    urlConnection.setUseCaches(false);
                    urlConnection.getInputStream();

                    // We got a valid response, but not from the real google
                    return urlConnection.getResponseCode() != 204;
                } catch (IOException e) {
                    return false;
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }
            }
        }
    }

}
