package com.hro.hotspotanalyser.exceptions;

public abstract class AbstractWrapperException extends Exception {

    private final Exception mException;

    public AbstractWrapperException(Exception e) {
        this.mException = e;
    }

    public Exception getException() {
        return mException;
    }

}
