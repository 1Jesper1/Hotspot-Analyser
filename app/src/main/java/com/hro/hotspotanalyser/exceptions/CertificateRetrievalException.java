package com.hro.hotspotanalyser.exceptions;

public class CertificateRetrievalException extends AbstractWrapperException {
    public CertificateRetrievalException(Exception e) {
        super(e);
    }
}
