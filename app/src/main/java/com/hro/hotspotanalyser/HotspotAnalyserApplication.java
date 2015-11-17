package com.hro.hotspotanalyser;

import android.app.Application;

import de.greenrobot.event.EventBus;

public class HotspotAnalyserApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Configure EventBus
        EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus();
        
    }

}
