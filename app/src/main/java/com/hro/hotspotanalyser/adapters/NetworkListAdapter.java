package com.hro.hotspotanalyser.adapters;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.hro.hotspotanalyser.R;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

public class NetworkListAdapter extends ArrayAdapter<ScanResult> {

    WifiManager wifi;
    SupplicantState supState;
    Boolean captivePortal;

    public NetworkListAdapter(Context context, List<ScanResult> objects) {
        super(context, 0, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ScanResult result = getItem(position);
        NetworkItemViewHolder viewHolder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.li_network, parent, false);

            viewHolder = new NetworkItemViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (NetworkItemViewHolder) convertView.getTag();
        }

        boolean captivePortal = hasCaptivePortal(result);


        viewHolder.ssid.setText(result.SSID);
        viewHolder.bssid.setText(result.BSSID);
        viewHolder.rssi.setText(String.valueOf(result.level));
        viewHolder.auth.setText(getAuthenticationType(result.capabilities));

        return convertView;
    }

    public final static class NetworkItemViewHolder {

        @Bind(R.id.network_ssid)
        TextView ssid;
        @Bind(R.id.network_bssid)
        TextView bssid;
        @Bind(R.id.network_rssi)
        TextView rssi;
        @Bind(R.id.network_auth)
        TextView auth;

        public NetworkItemViewHolder(View itemView) {
            ButterKnife.bind(this, itemView);
        }
    }

    //Funtion to check for captive portal
    public Boolean hasCaptivePortal(ScanResult result){
        //If network is not open
        if(getAuthenticationType(result.capabilities) != "OPEN") {
            return false;
        }
        //If network is open
        else {
            WifiConfiguration wifiConfiguration = new WifiConfiguration();
            wifiConfiguration.SSID = result.SSID;
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfiguration.BSSID = result.BSSID;
            wifiConfiguration.hiddenSSID = false;

            wifi = (WifiManager) this.getContext().getSystemService(Context.WIFI_SERVICE);
            int networkID = wifi.addNetwork(wifiConfiguration);
            wifi.disconnect();
            wifi.enableNetwork(networkID, true);
            wifi.reconnect();
            //Check for captive portal
            /*if(CAPTIVE PORTAL}}{
                WifiInfo wifiInfo = wifi.getConnectionInfo();
                supState = wifiInfo.getSupplicantState();
                return true;
            }
            //Not captive portal
            else{
                return false;
            }*/

        }
    }

    // Function to check for authentication type of hotspot
    public String getAuthenticationType(String cap){
        if (cap.contains("WEP")) {
            return "WEP";
        }
        else if (cap.contains("WPA2")) {
            return "WPA2";
        }
        else if (cap.contains("WPA")) {
            return "WPA";
        }
        else {
            return "OPEN";
        }
    }
}
