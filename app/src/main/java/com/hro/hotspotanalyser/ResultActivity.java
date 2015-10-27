package com.hro.hotspotanalyser;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.hro.hotspotanalyser.events.WifiAnalysisResultsEvent;
import com.hro.hotspotanalyser.models.AnalyzerResult;

import de.greenrobot.event.EventBus;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        WifiAnalysisResultsEvent event = EventBus.getDefault().getStickyEvent(WifiAnalysisResultsEvent.class);
        if (event != null) {
            AnalyzerResult result = event.getResult();
        }

    }

}
