package com.hro.hotspotanalyser.models;

import java.util.HashMap;

public class HotspotInfo {

    private static final HashMap<String, HotspotInfo> mKnownNetworks;
    static {
        mKnownNetworks = new HashMap<>();
        mKnownNetworks.put("KPN Fon", new HotspotInfo("KPN Fon", "https://kpn.portal.fon.com/", "cb922c1965f60a9c6561ffd9fc851d5960642c4c"));
        mKnownNetworks.put("Schamper0", new HotspotInfo("Schamper0", "https://bitbangers.co.uk/", "1a473f19b9181d7a40aed4cac4260a4fbf4d7dce"));
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
