package com.hro.hotspotanalyser.exceptions;

import java.io.IOException;

public class CaptivePortalCheckException extends AbstractWrapperException {
    public CaptivePortalCheckException(Exception e) {
        super(e);
    }
}
