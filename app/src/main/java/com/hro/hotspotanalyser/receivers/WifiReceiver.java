package com.hro.hotspotanalyser.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;

import com.hro.hotspotanalyser.events.WifiScanResultsEvent;
import com.hro.hotspotanalyser.models.AnalyzerResult;
import com.hro.hotspotanalyser.models.HotspotInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import de.greenrobot.event.EventBus;

public class WifiReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = WifiReceiver.class.getSimpleName();
    private static final String WALLED_GARDEN_URL = "http://clients3.google.com/generate_204";
    private static final int SOCKET_TIMEOUT_MS = 10000;

    private final EventBus mBus = EventBus.getDefault();

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

    private static final class AnalyzeNetworkTask extends AsyncTask<WifiManager, Void, AnalyzerResult> {

        private final Context mContext;
        private Network mWifiNetwork;

        public AnalyzeNetworkTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected AnalyzerResult doInBackground(WifiManager... wifiManagers) {
            WifiManager wifiManager = wifiManagers[0];

            mWifiNetwork = getWifiNetwork();
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
                // Get Hotspot SSID
                String ssid = currentConfig.SSID != null ? currentConfig.SSID.replaceAll("^\"|\"$", "") : null;
                HotspotInfo hotspotInfo = HotspotInfo.getKnownNetworkInfo(ssid);

                // Get the captive portal url, if any
                String captivePortalUrl = getCaptivePortalUrl();
                boolean hasCaptivePortal = !captivePortalUrl.isEmpty() ? true : false;

                // Get the certificates, if any
                X509Certificate[] certificates = getCertificates(captivePortalUrl);
                boolean areValidCerts = false;

                // If there are certificates, check if they're valid
                if (certificates != null && certificates.length > 0) {
                    areValidCerts = areValidCertificates(certificates);
                }

                // Compare the gathered information against possibly known information about this hotspot ssid
                boolean matchesKnown = matchesKnownInfo(hotspotInfo, captivePortalUrl, areValidCerts ? certificates[0] : null);

                return new AnalyzerResult(
                        hasCaptivePortal,
                        areValidCerts,
                        hotspotInfo != null,
                        matchesKnown
                );
            }

            return null;
        }

        @Override
        protected void onPostExecute(AnalyzerResult analyzerResult) {
            // Generate a notification based on the result
        }

        public static String hexifyBytes (byte bytes[]) {

            char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

            StringBuffer buf = new StringBuffer(bytes.length * 2);

            for (int i = 0; i < bytes.length; ++i) {
                buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
                buf.append(hexDigits[bytes[i] & 0x0f]);
            }

            return buf.toString();
        }

        private String getCaptivePortalUrl() {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) getUrlConnection(WALLED_GARDEN_URL);
                conn.setInstanceFollowRedirects(false);
                conn.getInputStream();

                int responseCode = conn.getResponseCode();

                // The check url generates a 204, if it doesn't we most likely have a captive portal
                if (responseCode != 204) {
                    return conn.getHeaderField("Location");
                }

                return null;
            } catch (IOException e) {
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        // Certificate check
        private X509Certificate[] getCertificates(String portalUrl) {
            //If there was a redirect
            if (!portalUrl.isEmpty() && portalUrl.startsWith("https://")) {
                HttpsURLConnection conn = null;
                try {
                    conn = (HttpsURLConnection) getUrlConnection(portalUrl);
                    conn.getInputStream();

                    //Get server certificates
                    Certificate[] certificates = conn.getServerCertificates();
                    return Arrays.copyOf(certificates, certificates.length, X509Certificate[].class);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }

            return null;
        }

        private boolean areValidCertificates(X509Certificate[] certificates) {
            if (certificates.length > 0) {
                try {
                    for (X509Certificate cert : certificates) {
                        // Checks if the certificates aren't outdated
                        cert.checkValidity();
                    }

                    return true;
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    e.printStackTrace();
                }
            }

            return false;
        }

        private boolean matchesKnownInfo(HotspotInfo knownInfo, String portalUrl, X509Certificate siteCert) {
            // If knownInfo is null, the ssid didn't match
            if (knownInfo == null) {
                return false;
            }

            try {
                boolean portalMatches = portalUrl.startsWith(knownInfo.captivePortalUrl);
                boolean certMatches = false;

                if (siteCert == null) {
                    certMatches = knownInfo.certificateFingerprint == null;
                } else {
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    byte[] der = siteCert.getEncoded();
                    md.update(der);
                    byte[] digest = md.digest();
                    String hexedFingerprint = hexifyBytes(digest);

                    certMatches = knownInfo.certificateFingerprint.equals(hexedFingerprint);
                }
                return portalMatches && certMatches;


            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return false;
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
                return false;
            }
        }

        private URLConnection getUrlConnection(String target) throws IOException {
            URLConnection conn;
            URL url= new URL(target);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mWifiNetwork != null) {
                conn = mWifiNetwork.openConnection(url);
            } else {
                conn = url.openConnection();
            }

            conn.setConnectTimeout(SOCKET_TIMEOUT_MS);
            conn.setReadTimeout(SOCKET_TIMEOUT_MS);
            conn.setUseCaches(false);

            return conn;
        }

        private Network getWifiNetwork() {
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

            return null;
        }
    }
}
