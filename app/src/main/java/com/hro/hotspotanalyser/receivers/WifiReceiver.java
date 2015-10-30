package com.hro.hotspotanalyser.receivers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import com.hro.hotspotanalyser.MainActivity;
import com.hro.hotspotanalyser.R;
import com.hro.hotspotanalyser.ResultActivity;
import com.hro.hotspotanalyser.events.WifiAnalysisResultsEvent;
import com.hro.hotspotanalyser.events.WifiScanResultsEvent;
import com.hro.hotspotanalyser.exceptions.AbstractWrapperException;
import com.hro.hotspotanalyser.exceptions.CaptivePortalCheckException;
import com.hro.hotspotanalyser.exceptions.CertificateCheckException;
import com.hro.hotspotanalyser.exceptions.CertificateRetrievalException;
import com.hro.hotspotanalyser.exceptions.MatchKnownException;
import com.hro.hotspotanalyser.models.AnalyzerResult;
import com.hro.hotspotanalyser.models.HotspotInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
        public String mFingerPrint;

        public AnalyzeNetworkTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected AnalyzerResult doInBackground(WifiManager... wifiManagers) {
            WifiManager wifiManager = wifiManagers[0];

            mWifiNetwork = getWifiNetwork();
            mFingerPrint = null;
            WifiConfiguration currentConfig = null;

            List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
            if (configuredNetworks != null) {
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
                List<AbstractWrapperException> exceptions = new ArrayList<>();

                // Get the captive portal url, if any
                String captivePortalUrl = null;
                try {
                    captivePortalUrl = getCaptivePortalUrl();
                } catch (AbstractWrapperException e) {
                    exceptions.add(e);
                }
                boolean hasCaptivePortal = captivePortalUrl != null;

                // Get the certificates, if any
                X509Certificate[] certificates = null;
                try {
                    certificates = getCertificates(captivePortalUrl);
                } catch (AbstractWrapperException e) {
                    exceptions.add(e);
                }

                // If there are certificates, check if they're valid
                boolean areValidCerts = false;
                try {
                    if (certificates != null && certificates.length > 0) {
                        areValidCerts = areValidCertificates(certificates);
                    }
                } catch (AbstractWrapperException e) {
                    exceptions.add(e);
                }

                // Compare the gathered information against possibly known information about this hotspot ssid
                boolean matchesKnown = false;
                try {
                    matchesKnown = matchesKnownInfo(hotspotInfo, captivePortalUrl, areValidCerts ? certificates[0] : null);
                } catch (AbstractWrapperException e) {
                    exceptions.add(e);
                }

                //Only log network if network has valid certificates, a captive portal and is not yet known
                if (hasCaptivePortal && areValidCerts && !matchesKnown) {
                    logNetwork(ssid, captivePortalUrl, mFingerPrint);
                }

                return new AnalyzerResult(
                        ssid,
                        hasCaptivePortal,
                        certificates != null && certificates.length > 0,
                        areValidCerts,
                        hotspotInfo != null,
                        matchesKnown,
                        exceptions
                );
            }

            return null;
        }

        @Override
        protected void onPostExecute(AnalyzerResult analyzerResult) {
            if (analyzerResult == null) {
                return;
            }

            Resources res = mContext.getResources();

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext)
                    .setContentTitle(res.getString(R.string.app_name));

            NotificationCompat.InboxStyle inboxStyle =
                    new NotificationCompat.InboxStyle();

            inboxStyle.addLine(
                    res.getString(R.string.result_intro, analyzerResult.SSID)
            );

            int resId;
            if (analyzerResult.hasCaptivePortal) {
                resId = R.string.result_portal_present;
            } else {
                resId = R.string.result_portal_absent;
            }
            inboxStyle.addLine("- " + res.getString(resId));

            if (analyzerResult.hasCaptivePortal && analyzerResult.hasCertificates) {
                if (analyzerResult.hasValidCertificates) {
                    resId = R.string.result_certificate_valid;
                } else {
                    resId = R.string.result_certificate_invalid;
                }
            } else {
                resId = R.string.result_certificate_missing;
            }
            inboxStyle.addLine("- " + res.getString(resId));

            if (analyzerResult.isKnown) {
                if (analyzerResult.matchesKnown) {
                    resId = R.string.result_known_match;
                } else {
                    resId = R.string.result_known_mismatch;
                }
            } else {
                resId = R.string.result_unknown;
            }
            inboxStyle.addLine("- " + res.getString(resId));

            int summaryResource;
            int iconResource;
            int colorResource;
            if (analyzerResult.matchesKnown) {
                colorResource = R.color.green;
                iconResource = R.drawable.ic_done_white_24dp;
                summaryResource = R.string.result_safe;
            } else if (analyzerResult.hasCaptivePortal && analyzerResult.hasValidCertificates) {
                colorResource = R.color.amber;
                iconResource = R.drawable.ic_warning_white_24dp;
                summaryResource = R.string.result_probably_safe;
            } else {
                colorResource = R.color.red;
                iconResource = R.drawable.ic_exclamation_white_24dp;
                summaryResource = R.string.result_probably_unsafe;
            }

            inboxStyle.setBigContentTitle(res.getString(summaryResource));
            notificationBuilder.setSmallIcon(iconResource)
                    .setColor(res.getColor(colorResource))
                    .setContentText(res.getString(summaryResource))
                    .setStyle(inboxStyle);

            Intent resultIntent = new Intent(mContext, ResultActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(mContext, (int) System.currentTimeMillis(), resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notificationBuilder.setContentIntent(contentIntent);

            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, notificationBuilder.build());

            EventBus.getDefault().postSticky(new WifiAnalysisResultsEvent(analyzerResult));
        }

        private String getCaptivePortalUrl() throws CaptivePortalCheckException {
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
                throw new CaptivePortalCheckException(e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        // Certificate check
        private X509Certificate[] getCertificates(String portalUrl) throws CertificateRetrievalException {
            //If there was a redirect
            if (portalUrl != null && portalUrl.startsWith("https://")) {
                HttpsURLConnection conn = null;
                try {
                    conn = (HttpsURLConnection) getUrlConnection(portalUrl);
                    conn.getInputStream();

                    //Get server certificates
                    Certificate[] certificates = conn.getServerCertificates();
                    return Arrays.copyOf(certificates, certificates.length, X509Certificate[].class);
                } catch (IOException e) {
                    throw new CertificateRetrievalException(e);
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }

            return null;
        }

        private boolean areValidCertificates(X509Certificate[] certificates) throws CertificateCheckException {
            if (certificates.length > 0) {
                try {
                    for (X509Certificate cert : certificates) {
                        // Checks if the certificates aren't outdated
                        cert.checkValidity();
                    }

                    return true;
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    throw new CertificateCheckException(e);
                }
            }

            return false;
        }

        private boolean matchesKnownInfo(HotspotInfo knownInfo, String portalUrl, X509Certificate siteCert) throws MatchKnownException {
            // If knownInfo is null, the ssid didn't match
            if (knownInfo == null) {
                return false;
            }

            try {
                boolean portalMatches = portalUrl != null && portalUrl.startsWith(knownInfo.captivePortalUrl);
                boolean certMatches;

                if (siteCert == null) {
                    certMatches = knownInfo.certificateFingerprint == null;
                } else {
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    md.update(siteCert.getEncoded());

                    byte[] digest = md.digest();
                    String hexedFingerprint = hexifyBytes(digest);
                    mFingerPrint = hexedFingerprint;

                    certMatches = knownInfo.certificateFingerprint.equals(hexedFingerprint);
                }

                return portalMatches && certMatches;
            } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
                throw new MatchKnownException(e);
            }
        }

        private URLConnection getUrlConnection(String target) throws IOException {
            URLConnection conn;
            URL url = new URL(target);

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
                    if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                        return n;
                    }
                }
            }

            return null;
        }

        private static String hexifyBytes(byte bytes[]) {
            char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

            StringBuilder buf = new StringBuilder(bytes.length * 2);

            for (byte aByte : bytes) {
                buf.append(hexDigits[(aByte & 0xf0) >> 4]);
                buf.append(hexDigits[aByte & 0x0f]);
            }

            return buf.toString();
        }

        private void logNetwork(String ssid, String portalUrl, String fingerPrint) {
            URL parseUrl = null;
            try {
                parseUrl = new URL(portalUrl);
                //Only parse protocol and host
                String parsedUrl = parseUrl.getProtocol() + "://" + parseUrl.getHost();
                //Check if file is null
                String fileContents = readFromFile();
                //If file does not contain the network
                if (fileContents != null && !fileContents.contains(ssid + ", " + parsedUrl + ", " + fingerPrint)) {
                    writeToFile("\n" + ssid + ", " + parsedUrl + ", " + fingerPrint);
                }
                //If file is null
                if (fileContents == null) {
                    writeToFile(ssid + ", " + parsedUrl + ", " + fingerPrint);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        private void writeToFile(String data) {
            try {
                File path = new File(mContext.getFilesDir(), "");
                if (!path.exists()) {
                    path.mkdirs();
                }
                File mypath = new File(path, "hotspots.txt");
                BufferedWriter br = new BufferedWriter(new FileWriter(mypath, true));
                br.append(data);
                br.close();
            } catch (IOException e) {
            }
        }

        private String readFromFile() {
            File path = new File(mContext.getFilesDir(), "");
            if (path.exists()) {
                try {
                    File mypath = new File(path, "hotspots.txt");
                    StringBuilder text = new StringBuilder();

                    BufferedReader br = new BufferedReader(new FileReader(mypath));
                    String line;

                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                    //Return StringBuilder to String
                    return text.toString();
                } catch (IOException e) {
                    //You'll need to add proper error handling here
                }
            }

            return null;
        }
    }
}
