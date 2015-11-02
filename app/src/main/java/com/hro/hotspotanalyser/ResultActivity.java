package com.hro.hotspotanalyser;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.hro.hotspotanalyser.events.WifiAnalysisResultsEvent;
import com.hro.hotspotanalyser.models.AnalyzerResult;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;

public class ResultActivity extends AppCompatActivity {

    @Bind(R.id.result_main_title)           TextView mMainTitle;
    @Bind(R.id.result_main_details)         TextView mMainDetails;
    @Bind(R.id.result_portal)               RelativeLayout mPortalLayout;
    @Bind(R.id.result_portal_icon)          ImageView mPortalIcon;
    @Bind(R.id.result_portal_details)       TextView mPortalDetails;
    @Bind(R.id.result_certificate)          RelativeLayout mCertificateLayout;
    @Bind(R.id.result_certificate_icon)     ImageView mCertificateIcon;
    @Bind(R.id.result_certificate_details)  TextView mCertificateDetails;
    @Bind(R.id.result_known)                RelativeLayout mKnownLayout;
    @Bind(R.id.result_known_icon)           ImageView mKnownIcon;
    @Bind(R.id.result_known_details)        TextView mKnownDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        EventBus bus = EventBus.getDefault();

        WifiAnalysisResultsEvent event = bus.getStickyEvent(WifiAnalysisResultsEvent.class);

        if (event != null) {
            AnalyzerResult result = event.getResult();

            // MAIN TEXT
            mMainTitle.setText(getString(R.string.result_intro, result.SSID));

            int textResId;
            int iconResId;
            switch (result.getSafetyLevel()) {
                case Safe:
                    textResId = R.string.result_detailed_safe;
                    break;
                case PrettySafe:
                    textResId = R.string.result_detailed_probably_safe;
                    break;
                case Warning:
                    textResId = R.string.result_detailed_probably_unsafe;
                    break;
                case Dangerous:
                    textResId = R.string.result_detailed_unsafe;
                    break;
                case Error:
                default:
                    textResId = R.string.result_detailed_error;
            }

            mMainDetails.setText(textResId);

            // PORTAL
            if (result.analysisFailed) {
                textResId = R.string.result_detailed_portal_error;
                iconResId = R.drawable.ic_exclamation_black_48dp;
            } else if (result.hasCaptivePortal) {
                textResId = R.string.result_detailed_portal_present;
                iconResId = R.drawable.ic_done_black_48dp;
            } else {
                textResId = R.string.result_detailed_portal_absent;
                iconResId = R.drawable.ic_exclamation_black_48dp;
            }

            mPortalDetails.setText(textResId);
            mPortalIcon.setImageResource(iconResId);

            if (!result.analysisFailed) {
                // CERTIFICATE
                mCertificateLayout.setVisibility(View.VISIBLE);
                if (result.hasValidCertificates) {
                    textResId = R.string.result_detailed_certificate_valid;
                    iconResId = R.drawable.ic_done_black_48dp;
                } else if (result.hasCertificates) {
                    textResId = R.string.result_detailed_certificate_invalid;
                    iconResId = R.drawable.ic_exclamation_black_48dp;
                } else {
                    textResId = R.string.result_detailed_certificate_missing;
                    iconResId = R.drawable.ic_exclamation_black_48dp;
                }

                mCertificateDetails.setText(textResId);
                mCertificateIcon.setImageResource(iconResId);

                // KNOWN
                mKnownLayout.setVisibility(View.VISIBLE);
                if (result.matchesKnown) {
                    textResId = R.string.result_detailed_known_match;
                    iconResId = R.drawable.ic_done_black_48dp;
                } else if (result.isKnown) {
                    textResId = R.string.result_detailed_known_mismatch;
                    iconResId = R.drawable.ic_exclamation_black_48dp;
                } else {
                    textResId = R.string.result_detailed_unknown;
                    iconResId = R.drawable.ic_exclamation_black_48dp;
                }

                mKnownDetails.setText(textResId);
                mKnownIcon.setImageResource(iconResId);
            } else {
                mCertificateLayout.setVisibility(View.INVISIBLE);
                mKnownLayout.setVisibility(View.INVISIBLE);
            }

        }

    }

}
