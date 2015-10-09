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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

import de.greenrobot.event.EventBus;

public class WifiReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = WifiReceiver.class.getSimpleName();
    private static final String WALLED_GARDEN_URL = "http://clients3.google.com/generate_204";
    private static final int WALLED_GARDEN_SOCKET_TIMEOUT_MS = 10000;
    private static String sRedirectLink;

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
                //Check captive portal
                boolean isCaptivePortal = checkCaptivePortal(wifiManager);
                //Check server certificates only when there is an captive portal
                boolean isValidCertificate = isCaptivePortal && checkServerCertificates(sRedirectLink);

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
                    sRedirectLink = urlConnection.getHeaderField("Location");
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

        // Certificate check
        public boolean checkServerCertificates(String url){
            //If there was a redirect
            if (!url.isEmpty()) {
                HttpsURLConnection conn;
                try {
                    URL obj = new URL(sRedirectLink);
                    conn = (HttpsURLConnection) obj.openConnection();
                    if (conn != null) {

                        try {
                            conn.connect();
                            //Get server certificates
                            Certificate[] certificates = conn.getServerCertificates();
                            //Loop over the certificates
                            for( Certificate cert : certificates){
                                X509Certificate x509cert = (X509Certificate)cert;
                                //Check if certificate is valid
                                x509cert.checkValidity();
                                //return something if one or all are valid
                                return true;
                            }
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        } catch (SSLPeerUnverifiedException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (CertificateExpiredException e) {
                            e.printStackTrace();
                        } catch (CertificateNotYetValidException e) {
                            e.printStackTrace();
                        } finally {
                            conn.disconnect();
                        }
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }
}
