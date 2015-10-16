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
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
        private String mSSID;
        private HotspotCertificates mCertificates;

        public AnalyzeNetworkTask(Context context) {
            this.mContext = context;
        }

        public class HotspotInfo{
            String SSID;
            String captivePortal;
            HotspotCertificates certificates;

            public HotspotInfo(String SSID, String captivePortal, HotspotCertificates certificates)
            {
                this.SSID = SSID;
                this.captivePortal = captivePortal;
                this.certificates = certificates;
            }
        }

        public class HotspotCertificates{
            List<String> certificateInfo;

            public HotspotCertificates(ArrayList<String> certificates){
                this.certificateInfo = certificates;
            }
        }

        private HashMap <String, HotspotInfo> DBNETWORK = new HashMap <String, HotspotInfo>()
        {{
                put("KPN Fon", new HotspotInfo("KPN Fon", "https://kpn.portal.fon.com/",
                        new HotspotCertificates(
                                new ArrayList<>(Arrays.asList("OpenSSLRSAPublicKey{modulus=91e85492d20a56b1ac0d24ddc5cf446774992b37a37d23700071bc53dfc4fa2a128f4b7f1056bd9f7072b7617fc94b0f17a73de3b00461eeff1197c7f4863e0afa3e5cf993e6347ad9146be79cb385a0827a76af7190d7ecfd0dfa9c6cfadfb082f4147ef9bec4a62f4f7f997fb5fc674372bd0c00d689eb6b2cd3ed8f981c14ab7ee5e36efcd8a8e49224da436b62b855fdeac1bc6cb68bf30e8d9ae49b6c6999f878483045d5ade10d3c4560fc32965127bc67c3ca2eb66bea46c7c720a0b11f65de4808baa44ea9f283463784ebe8cc814843674e722a9b5cbd4c1b288a5c227bb4ab98d9eee05183c309464e6d3e99fa9517da7c3357413c8d51ed0bb65caf2c631adf57c83fbce95dc49baf4599e2a35a24b4baa9563dcf6faaff4958bef0a8fff4b8ade937fbbab8f40b3af9e843421e89d884cb13f1d9bbe18960b88c2856ac141d9c0ae771ebcf0edd3da996a148bd3cf7afb50d224cc01181ec563bf6d3a2e25bb7b204225295809369e88e4c65f191032d707402ea8b671529695202bbd7df506a5546bfa0a328617f70d0c3a2aa2c21aa47ce289c064576bf821827b4d5aeb4cb50e66bf44c867130e9a6df1686e0d8ff40ddfbd042887fa3333a2e5c1e41118163ce18716b2beca68ab7315c3a6a47e0c37959d6201aaff26a98aa72bc574ad24b9dbb10fcb04c41e5ed1d3d5e289d9cccbfb351daa747e58453,publicExponent=10001}",
                                        "OpenSSLRSAPublicKey{modulus=b914d985f2414457ff30441edc3c44a317b86e01f8a35fc2a9211dce59f4ecf388a909323cb18b63a43e2736f38ff938662e0797418f4ba6ddc35f9e733ce7ca200d4f7c3205cfc12e48654a85d01f56316d8ee5c632d41bbc9f7d96fc98d74ff8f45856f8e345be911882e48abeafcd523751874f1e97c1e83aaef9ff46e4653f3fc347832fccb8425e2d7ef75a68ae5d4bc0a63521f586a3c8498b9863600dc92148c292306546b286350442257eada74e4b1240007a88685c6f9fa3a4781121ae3d0b0ebe451423cfeb75d7f6a0f1bc456c5ebca132ecf3587842280b3a0176f0c5a09ec16970de8f4ba679dff276b6e30f137c183bb1516c6a2039ce9e69,publicExponent=10001}",
                                        "OpenSSLRSAPublicKey{modulus=bf7106180bc52ddc822f4ec6267984c0d0b01ee11e163b9f08ed04ca503ac5c84780fe8664e5e91ca587408fc5e2963f71efd40893c31a79580f15e3b8239ea5cdcef6a22959f8150d3b70a357853a92556d4471b73f5c4585697d3f5322143305c15f3a966680bde18bf2ec7379b06402efe7ca2879687332c559122247b2b7fe5b565f2f3a80b2c11133a0d6a7bd04d4ab5c76df3a4d0ee61884eead651e0a656e4cc190fef0ddcca4978c9e0753fe1a95a2a8e9e71973a9d0c4fa9979bfaab759e083d5a2b63f97fd55d368123d91d8b7af60a52e1d453268847b278d3da2e98e53b6fd47799ed7496e64acbb26b9629a51a31ee7fd6b189be7cfe3aed555,publicExponent=10001}")))));
            }};

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
                //Check if portal is a known host
                boolean isKnownHost = isKnownNetwork();
                //Check server certificates only when there is an captive portal
                boolean isValidCertificate = isCaptivePortal && hasValidCertificates(mRedirectUrl);

                Log.d(LOG_TAG, "Is captive portal: " + isCaptivePortal);
            }

            return null;
        }
        private boolean isKnownNetwork(){
            if(!mSSID.isEmpty() && !mRedirectUrl.isEmpty()){
                HotspotInfo hotspotInfo = DBNETWORK.get(mSSID);
                if(hotspotInfo != null) {
                    if(mRedirectUrl.startsWith(hotspotInfo.captivePortal)){
                        mCertificates = hotspotInfo.certificates;
                        return true;
                    }
                    return false;
                }
                return false;
            }
        return false;
        }

        private boolean isCaptivePortal(WifiManager wifiManager) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            //Get Hotspot SSID
            mSSID = wifiInfo.getSSID() != null ? wifiInfo.getSSID().replaceAll("^\"|\"$", "") : null;
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
                        ArrayList<String>  certificateKeys = new ArrayList<>();
                        //Loop over the certificates
                        for (Certificate cert : certificates) {
                            X509Certificate x509cert = (X509Certificate) cert;
                            //Check if certificate is valid
                            x509cert.checkValidity();
                            //Public key
                            PublicKey publicKey = x509cert.getPublicKey();
                            certificateKeys.add(publicKey.toString());
                            Log.d(LOG_TAG, "Key: " + publicKey);
                        }
                        if(!mCertificates.certificateInfo.isEmpty()){
                            if(mCertificates.certificateInfo.containsAll(certificateKeys)){
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }
                    return false;
                } catch (IOException | CertificateExpiredException | CertificateNotYetValidException e) {
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
