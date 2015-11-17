package com.hro.hotspotanalyser.events;

import com.hro.hotspotanalyser.models.AnalyzerResult;

public class WifiAnalysisResultsEvent {

    private final AnalyzerResult mResult;

    public WifiAnalysisResultsEvent(AnalyzerResult result) {
        this.mResult = result;
    }

    public AnalyzerResult getResult() {
        return mResult;
    }
}
