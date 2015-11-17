package com.hro.hotspotanalyser.exceptions;

public class CertificateCheckException extends AbstractWrapperException {
    public CertificateCheckException(Exception e) {
        super(e);
    }
}
