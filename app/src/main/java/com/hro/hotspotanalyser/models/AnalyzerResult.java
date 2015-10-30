package com.hro.hotspotanalyser.models;

import com.hro.hotspotanalyser.exceptions.AbstractWrapperException;

import java.util.List;

public class AnalyzerResult {

    public final String SSID;
    public final boolean hasCaptivePortal;
    public final boolean hasCertificates;
    public final boolean hasValidCertificates;
    public final boolean isKnown;
    public final boolean matchesKnown;
    public final List<AbstractWrapperException> exeptions;

    public AnalyzerResult(String ssid, boolean hasCaptivePortal, boolean hasCertificates,
                          boolean hasValidCertificates, boolean isKnown, boolean matchesKnown,
                          List<AbstractWrapperException> exceptions) {
        this.SSID = ssid;
        this.hasCaptivePortal = hasCaptivePortal;
        this.hasCertificates = hasCertificates;
        this.hasValidCertificates = hasValidCertificates;
        this.isKnown = isKnown;
        this.matchesKnown = matchesKnown;
        this.exeptions = exceptions;
    }

}
