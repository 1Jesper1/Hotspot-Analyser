package com.hro.hotspotanalyser;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import com.hro.hotspotanalyser.adapters.NetworkListAdapter;
import com.hro.hotspotanalyser.events.WifiScanResultsEvent;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int REFRESH_RATE = 5000;

    private final EventBus mBus = EventBus.getDefault();
    private final Handler mRefreshHandler = new Handler();

    private WifiManager mWifiManager;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    @Bind(R.id.list_networks)
    ListView mNetworkList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setSupportActionBar(mToolbar);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mBus.register(this);
        mRefreshHandler.post(mRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mBus.unregister(this);
        mRefreshHandler.removeCallbacks(mRefreshRunnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onEvent(WifiScanResultsEvent event) {
        List<ScanResult> results = event.getScanResults();

        NetworkListAdapter adapter;

        if (mNetworkList.getAdapter() == null) {
            adapter = new NetworkListAdapter(this, results);
            mNetworkList.setAdapter(adapter);
        } else {
            adapter = (NetworkListAdapter) mNetworkList.getAdapter();
            adapter.clear();
            adapter.addAll(results);
        }
    }

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (mWifiManager != null) {
                mWifiManager.startScan();
            }

            mRefreshHandler.postDelayed(this, REFRESH_RATE);
        }
    };
}
