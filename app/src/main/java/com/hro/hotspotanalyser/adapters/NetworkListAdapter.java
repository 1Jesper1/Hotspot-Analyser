package com.hro.hotspotanalyser.adapters;

import android.content.Context;
import android.net.wifi.ScanResult;
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

        viewHolder.ssid.setText(result.SSID);

        return convertView;
    }

    public final static class NetworkItemViewHolder {

        @Bind(R.id.network_ssid)
        TextView ssid;

        public NetworkItemViewHolder(View itemView) {
            ButterKnife.bind(this, itemView);
        }

    }

}
