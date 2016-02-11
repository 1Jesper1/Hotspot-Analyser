package com.hro.hotspotanalyser.models;

import java.util.HashMap;

public class HotspotInfo {

    private static final HashMap<String, HotspotInfo> mKnownNetworks;
    static {
        mKnownNetworks = new HashMap<>();
        mKnownNetworks.put("SSID_Example", new HotspotInfo("SSID_Example", "https://www.example.com/", "1111111111111111111111111111111111111111"));
    }

    public final String SSID;
    public final String captivePortalUrl;
    public final String certificateFingerprint;

    public HotspotInfo(String ssid, String captivePortal, String certificateFingerprint) {
        this.SSID = ssid;
        this.captivePortalUrl = captivePortal;
        this.certificateFingerprint = certificateFingerprint;
    }

    public static HotspotInfo getKnownNetworkInfo(String ssid) {
        return mKnownNetworks.get(ssid);
    }

}
