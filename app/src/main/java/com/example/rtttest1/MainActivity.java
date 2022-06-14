package com.example.rtttest1;
import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
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

//TODO public or private

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private boolean LocationPermission = false;

    ArrayList<ScanResult> AP_list_support_RTT;

    private WifiManager myWifiManager;
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
    }

    //TODO make this class a common service
    public class WifiScanReceiver extends BroadcastReceiver {
        private List<ScanResult> findRTTAPs(@NonNull List<ScanResult> WiFiScanResults) {
            List<ScanResult> RTT_APs = new ArrayList<>();
            for (ScanResult scanresult:WiFiScanResults) {
                if (scanresult.is80211mcResponder()) {
                    RTT_APs.add(scanresult);
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
            Log.d(TAG, "All WiFi points"+"("+scanResults.size()+")"+": "+scanResults);
            Log.d(TAG, "RTT APs"+"("+AP_list_support_RTT.size()+")"+": "+AP_list_support_RTT);

            if (!AP_list_support_RTT.isEmpty()){
                mainActivityAdapter.swapData(AP_list_support_RTT);

            } else{
                Log.d(TAG,"No RTT APs available");
            }
        }
        //TODO maxpeers
    }

    public void onClickScanAPs(View view) {
        if (LocationPermission) {
            Log.d(TAG, "Scanning...");
            myWifiManager.startScan();

            Snackbar.make(view, "Scanning...", Snackbar.LENGTH_SHORT).show();

        } else {
            // request permission
            Intent IntentRequestPermission = new Intent(this,
                    LocationPermissionRequest.class);
            startActivity(IntentRequestPermission);
        }
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

    //Start ranging in a new screen
    public void onClickRangingAPs(View view) {
        Log.d(TAG,"onClickRangingAPs()");

        Intent IntentRanging = new Intent(getApplicationContext(), RangingActivity.class);

        //Pass AP_list_support_RTT to RangingActivity
        IntentRanging.putParcelableArrayListExtra("SCAN_RESULT", AP_list_support_RTT);
        startActivity(IntentRanging);
    }

    public void onClickStartPositioning(View view){
        Log.d(TAG,"onClickStartPositioning()");

        Intent intentPositioning = new Intent(getApplicationContext(), LocalisationActivity.class);
        intentPositioning.putParcelableArrayListExtra("SCAN_RESULT",AP_list_support_RTT);
        startActivity(intentPositioning);
    }

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