package com.hro.hotspotanalyser;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.ImageView;
import android.widget.TextView;

import com.hro.hotspotanalyser.events.WifiAnalysisResultsEvent;
import com.hro.hotspotanalyser.models.AnalyzerResult;

import butterknife.Bind;
import de.greenrobot.event.EventBus;

public class ResultActivity extends AppCompatActivity {

    @Bind(R.id.result_main_title)
    TextView mMainTitle;
    @Bind(R.id.result_main_details)
    TextView mMainDetails;
    @Bind(R.id.result_portal_icon)
    ImageView mPortalIcon;
    @Bind(R.id.result_portal_details)
    TextView mPortalDetails;
    @Bind(R.id.result_certificate_icon)
    ImageView mCertificateIcon;
    @Bind(R.id.result_certificate_details)
    TextView mCertificateDetails;
    @Bind(R.id.result_known_icon)
    ImageView mKnownIcon;
    @Bind(R.id.result_known_details)
    TextView mKnownDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        WifiAnalysisResultsEvent event = EventBus.getDefault().getStickyEvent(WifiAnalysisResultsEvent.class);
        if (event != null) {
            AnalyzerResult result = event.getResult();

            mMainTitle.setText(R.string.result_intro);
        }

    }

}
