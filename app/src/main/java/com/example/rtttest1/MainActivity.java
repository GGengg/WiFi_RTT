package com.example.rtttest1;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import android.util.Log;
import android.view.View;

import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Check RTT availability, scan surrounding APs and display
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private boolean LocationPermission = false;

    ArrayList<ScanResult> AP_list_support_RTT;

    public WifiManager myWifiManager;
    private WifiScanReceiver myWifiReceiver;
    private MainActivityAdapter mainActivityAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView myRecyclerView = findViewById(R.id.RecyclerViewAPs);
        myRecyclerView.setHasFixedSize(true);

        LayoutManager layoutManager = new LinearLayoutManager(this);
        myRecyclerView.setLayoutManager((layoutManager));

        AP_list_support_RTT = new ArrayList<>();

        mainActivityAdapter = new MainActivityAdapter(AP_list_support_RTT);
        myRecyclerView.setAdapter(mainActivityAdapter);

        myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        myWifiReceiver = new WifiScanReceiver();

        //Scan_result_textview = findViewById(R.id.ScanResult);
    }

    public class WifiScanReceiver extends BroadcastReceiver {
        private static final String TAG = "WifiScanReceiver";

        //Only keep RTT supported APs from the original scan list
        private List<ScanResult> findRTTAPs(@NonNull List<ScanResult> OriginalList) {
            List<ScanResult> RTT_APs = new ArrayList<>();

            for (ScanResult scanResult : OriginalList) {
                if (scanResult.is80211mcResponder()) {
                    RTT_APs.add(scanResult);
                }
            }
            return RTT_APs;
        }

        //Add to avoid permission check for each scan
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() MainActivity");

            List<ScanResult> scanResults = myWifiManager.getScanResults();
            AP_list_support_RTT = (ArrayList<ScanResult>) findRTTAPs(scanResults);
            Log.d(TAG, "All WiFi points: " + scanResults);
            Log.d(TAG, "RTT APs: " + AP_list_support_RTT);

            if (!AP_list_support_RTT.isEmpty()){
                mainActivityAdapter.swapData(AP_list_support_RTT);

            } else{
                Log.d(TAG,"No RTT APs available");
            }
            //TODO Handle getmaxpeers (10)
            //Log.d(TAG, String.valueOf(RangingRequest.getMaxPeers()));
        }
    }

    //Scan surrounding WiFi points
    public void onClickScanAPs(View view) {
        Log.d(TAG,"onClickScanAPs()");

        if (LocationPermission) {
            Log.d(TAG, "Scanning...");
            myWifiManager.startScan();

            Snackbar.make(view, "Scanning...", Snackbar.LENGTH_LONG).show();

        } else {
            // request permission
            Intent IntentRequestPermission = new Intent(this,
                    LocationPermissionRequest.class);
            startActivity(IntentRequestPermission);
        }
    }

    //Start ranging in a new screen
    public void onClickRangingAPs(View view) {
        Log.d(TAG,"onClickRangingAPs()");

        Intent IntentRanging = new Intent(getApplicationContext(), RangingActivity.class);
        IntentRanging.putParcelableArrayListExtra("SCAN_RESULT", AP_list_support_RTT);
        startActivity(IntentRanging);
    }

    //Check RTT availability of the device
    public void onClickCheckRTTAvailability(View view){
        Log.d(TAG,"Checking RTT Availability...");

        boolean RTT_availability = getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);

        if (RTT_availability) {
            Snackbar.make(view, "RTT supported on this device :)",
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(view, "RTT not supported on this device :(",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    public void onClickStartPositioning(View view){
        Log.d(TAG,"onClickStartPositioning()");

        Intent intentPositioning = new Intent(getApplicationContext(), LocalizationActivity.class);
        intentPositioning.putParcelableArrayListExtra("SCAN_RESULT",AP_list_support_RTT);
        startActivity(intentPositioning);
    }

    //TODO make this class a common service

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() MainActivity");
        super.onStop();
        unregisterReceiver(myWifiReceiver);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume() MainActivity");
        super.onResume();


        // each time resume back in onResume state, check location permission
        LocationPermission = ActivityCompat.checkSelfPermission(
                this, permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        Log.d(TAG, "Location permission:" + LocationPermission);

        //register a Broadcast receiver to run in the main activity thread
        registerReceiver(
                myWifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }
}