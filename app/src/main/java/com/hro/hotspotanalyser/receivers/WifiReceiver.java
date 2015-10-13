package com.hro.hotspotanalyser.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.hro.hotspotanalyser.events.WifiScanResultsEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

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
                new AnalyzeNetworkTask(context).execute(wifiManager);
            }
        }

    }

    private static final class AnalyzeNetworkTask extends AsyncTask<WifiManager, Void, Void> {

        private final Context mContext;
        private String mRedirectUrl;
        private Network mWifiNetwork;

        public AnalyzeNetworkTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected Void doInBackground(WifiManager... wifiManagers) {
            WifiManager wifiManager = wifiManagers[0];

            WifiConfiguration currentConfig = null;

            List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
            if (configuredNetworks != null ) {
                for (WifiConfiguration conf : wifiManager.getConfiguredNetworks()) {
                    if (conf.status == WifiConfiguration.Status.CURRENT) {
                        currentConfig = conf;
                        break;
                    }
                }
            }


            // Only analyze when there's no protection
            if (currentConfig != null && currentConfig.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
                //Check captive portal
                boolean isCaptivePortal = isCaptivePortal(wifiManager);
                //Check server certificates only when there is an captive portal
                boolean isValidCertificate = isCaptivePortal && hasValidCertificates(mRedirectUrl);

                Log.d(LOG_TAG, "Is captive portal: " + isCaptivePortal);
            }

            return null;
        }

        private boolean isCaptivePortal(WifiManager wifiManager) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            NetworkInfo.DetailedState detailedState = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());

            mWifiNetwork = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ConnectivityManager cManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                Network[] networks = null;

                networks = cManager.getAllNetworks();

                for (Network n : networks) {
                    NetworkInfo info = cManager.getNetworkInfo(n);
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        mWifiNetwork = n;
                        break;
                    }
                }
            }

            if (detailedState == NetworkInfo.DetailedState.CAPTIVE_PORTAL_CHECK) {
                return true;
            } else {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(WALLED_GARDEN_URL);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mWifiNetwork != null) {
                        urlConnection = (HttpURLConnection) mWifiNetwork.openConnection(url);
                    } else {
                        urlConnection = (HttpURLConnection) url.openConnection();
                    }

                    urlConnection.setInstanceFollowRedirects(false);
                    urlConnection.setConnectTimeout(WALLED_GARDEN_SOCKET_TIMEOUT_MS);
                    urlConnection.setReadTimeout(WALLED_GARDEN_SOCKET_TIMEOUT_MS);
                    urlConnection.setUseCaches(false);
                    urlConnection.getInputStream();

                    int responseCode = urlConnection.getResponseCode();

                    if (responseCode != 204) {
                        mRedirectUrl = urlConnection.getHeaderField("Location");
                        return true;
                    }

                    return false;
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
        public boolean hasValidCertificates(String redirectUrl){
            //If there was a redirect
            if (!redirectUrl.isEmpty() && redirectUrl.startsWith("https://")) {
                HttpsURLConnection conn = null;
                try {
                    URL url = new URL(redirectUrl);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mWifiNetwork != null) {
                        conn = (HttpsURLConnection) mWifiNetwork.openConnection(url);
                    } else {
                        conn = (HttpsURLConnection) url.openConnection();
                    }

                    conn.connect();
                    conn.getInputStream();

                    //Get server certificates
                    Certificate[] certificates = conn.getServerCertificates();
                    if(certificates.length > 0) {
                        //Loop over the certificates
                        for (Certificate cert : certificates) {
                            X509Certificate x509cert = (X509Certificate) cert;
                            //Check if certificate is valid
                            x509cert.checkValidity();
                        }
                        return true;
                    }
                    return false;
                } catch (IOException | CertificateExpiredException | CertificateNotYetValidException e) {
                    e.printStackTrace();
                    return false;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            return false;
        }
    }
}
