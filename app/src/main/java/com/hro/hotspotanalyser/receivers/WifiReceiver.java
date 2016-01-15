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
import com.hro.hotspotanalyser.models.SafetyLevel;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
    private static final String WALLED_GARDEN_URL = "http://www.google.com/blank.html";
    private static final int SOCKET_TIMEOUT_MS = 10000;
    private static final boolean HOTSPOT_LOGGER_ENABLED = true;

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
                boolean hasCertificates = captivePortalUrl != null && captivePortalUrl.startsWith("https://");
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


                if (exceptions.size() == 0 && HOTSPOT_LOGGER_ENABLED) {
                    String hexedFingerprint = null;
                    if (areValidCerts) {
                        MessageDigest md = null;
                        try {
                            md = MessageDigest.getInstance("SHA-1");
                            md.update(certificates[0].getEncoded());
                            byte[] digest = md.digest();
                            hexedFingerprint = hexifyBytes(digest);
                        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
                        }
                    }
                    logNetwork(ssid, captivePortalUrl, hexedFingerprint, hasCertificates, areValidCerts);
                }

                return new AnalyzerResult(
                        ssid,
                        exceptions.size() > 0 && exceptions.get(0) instanceof CaptivePortalCheckException,
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

            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.addLine(
                    res.getString(R.string.result_intro, analyzerResult.SSID)
            );

            int resId;
            if (analyzerResult.analysisFailed) {
                resId = R.string.result_portal_error;
            } else if (analyzerResult.hasCaptivePortal) {
                resId = R.string.result_portal_present;
            } else {
                resId = R.string.result_portal_absent;
            }
            inboxStyle.addLine("- " + res.getString(resId));

            if (!analyzerResult.analysisFailed) {
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
            }

            inboxStyle.addLine(res.getString(R.string.result_more_details));

            int summaryResource;
            int iconResource;
            int colorResource;
            SafetyLevel safetyLevel = analyzerResult.getSafetyLevel();

            switch (safetyLevel) {
                case Safe:
                    colorResource = R.color.green;
                    iconResource = R.drawable.ic_done_white_24dp;
                    summaryResource = R.string.result_safe;
                    break;
                case PrettySafe:
                    colorResource = R.color.green;
                    iconResource = R.drawable.ic_done_white_24dp;
                    summaryResource = R.string.result_probably_safe;
                    break;
                case Warning:
                    colorResource = R.color.amber;
                    iconResource = R.drawable.ic_warning_white_24dp;
                    summaryResource = R.string.result_probably_unsafe;
                    break;
                case Dangerous:
                    colorResource = R.color.red;
                    iconResource = R.drawable.ic_exclamation_white_24dp;
                    summaryResource = R.string.result_unsafe;
                    break;
                case Error:
                default:
                    colorResource = R.color.red;
                    iconResource = R.drawable.ic_exclamation_white_24dp;
                    summaryResource = R.string.result_error;
            }

            inboxStyle.setBigContentTitle(res.getString(summaryResource));
            notificationBuilder.setSmallIcon(iconResource)
                    .setColor(res.getColor(colorResource))
                    .setContentText(res.getString(summaryResource))
                    .setStyle(inboxStyle);

            Intent resultIntent = new Intent(mContext, ResultActivity.class);
            PendingIntent contentIntent =
                    PendingIntent.getActivity(
                            mContext,
                            (int) System.currentTimeMillis(),
                            resultIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );

            notificationBuilder.setContentIntent(contentIntent);

            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, notificationBuilder.build());

            EventBus.getDefault().postSticky(new WifiAnalysisResultsEvent(analyzerResult));
        }

        private String getCaptivePortalUrl() throws CaptivePortalCheckException {
            HttpURLConnection conn = null;
            int bodyLength = 0;

            try {
                conn = (HttpURLConnection) getUrlConnection(WALLED_GARDEN_URL);
                conn.setInstanceFollowRedirects(false);

                InputStream inputStream = new BufferedInputStream(conn.getInputStream());
                try {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    int i = inputStream.read();
                    while (i != -1) {
                        bo.write(i);
                        i = inputStream.read();
                    }
                    bodyLength = bo.toString().length();
                    ;
                } catch (IOException e) {
                }
                int responseCode = conn.getResponseCode();

                // The check url generates a 200, if it doesn't we most likely have a captive portal
                if (responseCode != 200 && bodyLength != 0) {
                    if (responseCode == 301 || responseCode == 302 || responseCode == 303) {
                        return conn.getHeaderField("Location");
                    } else {
                        //test
                        if (!conn.getURL().toString().equals(WALLED_GARDEN_URL)) {
                            return conn.getURL().toString();
                        }
                    }
                }
                if (responseCode != 200 && bodyLength == 0) {
                    if (responseCode == 301 || responseCode == 302 || responseCode == 303) {
                        return conn.getHeaderField("Location");
                    }
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

        private void logNetwork(String ssid, String portalUrl, String fingerPrint, Boolean hasCertificates, Boolean areValidCerts) {
            URL parseUrl = null;
            String parsedUrl = null;
            try {
                if (portalUrl != null) {
                    parseUrl = new URL(portalUrl);
                    //Only parse protocol and host
                    parsedUrl = parseUrl.getProtocol() + "://" + parseUrl.getHost();
                }
                //Check if file is null
                String fileContents = readFromFile();
                //If file does not contain the network
                if (fileContents != null && !fileContents.contains(ssid + ", " + parsedUrl + ", " + fingerPrint + ", " + hasCertificates + ", " + areValidCerts)) {
                    writeToFile("\n" + ssid + ", " + parsedUrl + ", " + fingerPrint + ", " + hasCertificates + ", " + areValidCerts);
                }
                //If file is null
                if (fileContents == null) {
                    writeToFile(ssid + ", " + parsedUrl + ", " + fingerPrint + ", " + hasCertificates + ", " + areValidCerts);
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
                    return text.toString();
                } catch (IOException e) {
                }
            }

            return null;
        }
    }
}
