package com.hro.hotspotanalyser.models;

import java.util.HashMap;

public class HotspotInfo {

    private static final HashMap<String, HotspotInfo> mKnownNetworks;
    static {
        mKnownNetworks = new HashMap<>();
        mKnownNetworks.put("KPN Fon", new HotspotInfo("KPN Fon", "https://kpn.portal.fon.com/", "cb922c1965f60a9c6561ffd9fc851d5960642c4c"));
    }

    public final String SSID;
    public final String captivePortalUrl;
    public final byte[] certificateFingerprint;

    public HotspotInfo(String ssid, String captivePortal, String certificateFingerprint) {
        this.SSID = ssid;
        this.captivePortalUrl = captivePortal;
        this.certificateFingerprint = hexStringToByteArray(certificateFingerprint);
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static HotspotInfo getKnownNetworkInfo(String ssid) {
        return mKnownNetworks.get(ssid);
    }

}
