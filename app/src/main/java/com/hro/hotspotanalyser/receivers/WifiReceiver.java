package com.hro.hotspotanalyser.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import com.hro.hotspotanalyser.events.WifiScanResultsEvent;


import java.util.List;

import de.greenrobot.event.EventBus;

public class WifiReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = WifiReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        EventBus bus = EventBus.getDefault();
        String action = intent.getAction();

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            bus.post(new WifiScanResultsEvent(wifiManager.getScanResults()));
            //Get list of all configured networks
            List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
            //Loop through all configured networks
            for(WifiConfiguration config : configuredNetworks){
                //If network is the current active network and Key Management is NONE (OPEN)
                if(config.status == 0 && config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)){
                    Toast.makeText(context, "Open network active: " + config.SSID, Toast.LENGTH_SHORT).show();
                    /* CAPTIVE PORTAL CHECK?
                    WifiInfo info = wifiManager.getConnectionInfo();
                    NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(info.getSupplicantState());*/
                }
            }
        }

        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (info != null && info.isConnected()) {
                //Info of current connection
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                Toast.makeText(context, "You connected to: " + wifiInfo.getSSID(), Toast.LENGTH_SHORT).show();
                List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                //Loop through all configured networks
                for(WifiConfiguration config : configuredNetworks){
                    //If network is the current active network and Key Management is NONE (OPEN)
                    if(config.status == 0 && config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)){
                        Toast.makeText(context, "Open network active: " + config.SSID, Toast.LENGTH_SHORT).show();
                        /* CAPTIVE PORTAL CHECK?
                        wifiInfo.getSupplicantState();*/
                    }
                }
            }
        }

    }

}
