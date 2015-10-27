package com.hro.hotspotanalyser.models;

public class AnalyzerResult {

    public final String SSID;
    public final boolean hasCaptivePortal;
    public final boolean hasCertificates;
    public final boolean hasValidCertificates;
    public final boolean isKnown;
    public final boolean matchesKnown;

    public AnalyzerResult(String ssid, boolean hasCaptivePortal, boolean hasCertificates,
                          boolean hasValidCertificates, boolean isKnown, boolean matchesKnown) {
        this.SSID = ssid;
        this.hasCaptivePortal = hasCaptivePortal;
        this.hasCertificates = hasCertificates;
        this.hasValidCertificates = hasValidCertificates;
        this.isKnown = isKnown;
        this.matchesKnown = matchesKnown;
    }

}
