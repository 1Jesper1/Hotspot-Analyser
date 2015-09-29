package com.hro.hotspotanalyser.events;

import android.net.wifi.ScanResult;

import java.util.List;

public class WifiScanResultsEvent {

    private List<ScanResult> mScanResults;

    public WifiScanResultsEvent(List<ScanResult> scanResults) {
        this.mScanResults = scanResults;
    }

    public List<ScanResult> getScanResults() {
        return mScanResults;
    }

}
