package com.hro.hotspotanalyser.models;

import com.hro.hotspotanalyser.exceptions.AbstractWrapperException;

import java.util.List;

public class AnalyzerResult {

    public final String SSID;
    public final boolean analysisFailed;
    public final boolean hasCaptivePortal;
    public final boolean hasCertificates;
    public final boolean hasValidCertificates;
    public final boolean isKnown;
    public final boolean matchesKnown;
    public final List<AbstractWrapperException> exceptions;

    public AnalyzerResult(String ssid, boolean analysisFailed, boolean hasCaptivePortal,
                          boolean hasCertificates, boolean hasValidCertificates, boolean isKnown,
                          boolean matchesKnown, List<AbstractWrapperException> exceptions) {
        this.SSID = ssid;
        this.analysisFailed = analysisFailed;
        this.hasCaptivePortal = hasCaptivePortal;
        this.hasCertificates = hasCertificates;
        this.hasValidCertificates = hasValidCertificates;
        this.isKnown = isKnown;
        this.matchesKnown = matchesKnown;
        this.exceptions = exceptions;
    }

    public SafetyLevel getSafetyLevel() {
        if (analysisFailed) {
            return SafetyLevel.Error;
        } else if (matchesKnown) {
            return SafetyLevel.Safe;
        } else if (hasCaptivePortal && hasValidCertificates) {
            return SafetyLevel.PrettySafe;
        } else if (hasCaptivePortal && !hasCertificates) {
            return SafetyLevel.Warning;
        } else {
            return SafetyLevel.Dangerous;
        }
    }

}
