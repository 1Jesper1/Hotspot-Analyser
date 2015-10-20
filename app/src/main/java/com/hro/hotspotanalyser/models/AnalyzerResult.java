package com.hro.hotspotanalyser.models;

public class AnalyzerResult {

    private final boolean hasCaptivePortal;
    private final boolean hasValidCertificates;
    private final boolean isKnown;
    private final boolean matchesKnown;

    public AnalyzerResult(boolean hasCaptivePortal, boolean hasValidCertificates, boolean isKnown, boolean matchesKnown) {
        this.hasCaptivePortal = hasCaptivePortal;
        this.hasValidCertificates = hasValidCertificates;
        this.isKnown = isKnown;
        this.matchesKnown = matchesKnown;
    }

}
