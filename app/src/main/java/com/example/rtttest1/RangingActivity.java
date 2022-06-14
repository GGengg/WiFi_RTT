package com.example.rtttest1;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

//TODO put common class in service?
//TODO Try different layout view(linear?)
/**
 * Send ranging requests and display distance and RSSI values
 */
public class RangingActivity extends AppCompatActivity implements SensorEventListener{

    private static final String TAG = "RangingActivity";

    //RTT
    private WifiRttManager myWifiRTTManager;
    private RTTRangingResultCallback myRTTResultCallback;
    private WifiManager myWifiManager;
    private WifiScanReceiver myWifiScanReceiver;
    private RangingActivityAdapter rangingActivityAdapter;

    //TODO make this list global
    private List<ScanResult> RTT_APs = new ArrayList<>();
    private final List<RangingResult> Ranging_Results = new ArrayList<>();
    private final List<String> APs_MacAddress = new ArrayList<>();
    private List<RangingResult> Synchronised_RTT = new ArrayList<>();

    private long RTT_timestamp;

    final Handler RangingRequestDelayHandler = new Handler();

    //IMU
    private SensorManager sensorManager;
    private final HashMap<String,Sensor> sensors = new HashMap<>();

    private final float[] rotationMatrix = new float[9];
    private final float[] inclinationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private float[] Synchronised_orientationAngles = new float[3];

    private final float[] LastAccReading = new float[3];
    private final float[] LastMagReading = new float[3];
    private final float[] LastGyroReading = new float[3];
    private float[] Synchronised_LastAccReading = new float[3];
    private float[] Synchronised_LastMagReading = new float[3];
    private float[] Synchronised_LastGyroReading = new float[3];

    private long IMU_timestamp;
    private long Closest_IMU_timestamp;

    private Boolean Running = true;

    //Ranging layout

    private EditText RangingDelayEditText;
    private static final int RangingDelayDefault = 100;
    private int RangingDelay;

    private TextView textAccx, textAccy, textAccz;
    private TextView textGrox, textGroy, textGroz;
    private TextView textMagx, textMagy, textMagz;

    private TextView Counts;

    int Check_Point_Counts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate() RangingActivity");

        //receive RTT_APs from main activity
        Intent intent = getIntent();
        RTT_APs = intent.getParcelableArrayListExtra("SCAN_RESULT");

        if (RTT_APs == null || RTT_APs.isEmpty()) {
            Log.d(TAG,"RTT_APs null");
            Toast.makeText(getApplicationContext(),
                    "Please scan for available APs first",
                    Toast.LENGTH_SHORT).show();
            finish();
        } else {
            setContentView(R.layout.activity_ranging);

            RecyclerView myRecyclerView = findViewById(R.id.recyclerViewResults);
            myRecyclerView.setHasFixedSize(true);
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
            myRecyclerView.setLayoutManager((layoutManager));

            RangingDelayEditText = findViewById(R.id.delayValue);
            RangingDelayEditText.setText(String.format(
                    Locale.getDefault(),"%d", RangingDelayDefault));

            //RTT
            myWifiRTTManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
            myRTTResultCallback = new RTTRangingResultCallback();
            myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            myWifiScanReceiver = new WifiScanReceiver();
            registerReceiver(myWifiScanReceiver,
                    new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            for (ScanResult AP:RTT_APs){
                APs_MacAddress.add(AP.BSSID);
            }

            rangingActivityAdapter = new RangingActivityAdapter(Ranging_Results);
            myRecyclerView.setAdapter(rangingActivityAdapter);

            //IMU
            textAccx = findViewById(R.id.textViewAccX);
            textAccy = findViewById(R.id.textViewAccY);
            textAccz = findViewById(R.id.textViewAccZ);
            textGrox = findViewById(R.id.textViewGroX);
            textGroy = findViewById(R.id.textViewGroY);
            textGroz = findViewById(R.id.textViewGroZ);
            textMagx = findViewById(R.id.textViewMagX);
            textMagy = findViewById(R.id.textViewMagY);
            textMagz = findViewById(R.id.textViewMagZ);

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

            sensors.put("Magnetic",sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
            sensors.put("Accelerometer",sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensors.put("Gyroscope",sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));

            Counts = findViewById(R.id.textViewCounts);

            //Start
            registerSensors();
            startRangingRequest();
        }
    }

    private void registerSensors(){
        for (Sensor eachSensor:sensors.values()){
            sensorManager.registerListener(this,
                    eachSensor,SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    private void unregisterSensors(){
        for (Sensor eachSensor:sensors.values()){
            sensorManager.unregisterListener(this,eachSensor);
        }
    }

    @SuppressLint("MissingPermission")
    private void startRangingRequest() {
        RangingRequest rangingRequest =
                new RangingRequest.Builder().addAccessPoints(RTT_APs).build();

        myWifiRTTManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), myRTTResultCallback);

        String delay = RangingDelayEditText.getText().toString();
        if (!delay.equals("")){
            RangingDelay = Integer.parseInt(RangingDelayEditText.getText().toString());
        }else{
            Snackbar.make(findViewById(R.id.textViewDelayBeforeNextRequest),
                    "Please enter a valid number",Snackbar.LENGTH_SHORT).show();
            //TODO edit snackbar
        }
    }

    public void onClickBackgroundScan(View view){
        Snackbar.make(view,"Start scanning in background",Snackbar.LENGTH_SHORT).show();
        Handler Update_Handler = new Handler();
        Runnable Update_Runnable = new Runnable() {
            @Override
            public void run() {
                if (Running && (APs_MacAddress.size() < 8)){
                    //background scan rate
                    Update_Handler.postDelayed(this,3000);
                    myWifiManager.startScan();
                } else{
                    Update_Handler.removeCallbacks(this);
                }
            }
        };
        Update_Handler.postDelayed(Update_Runnable,1000);
        //TODO this handler affects http performance?
    }

    public void onClickLogData(View view){
        //TODO editText
        EditText url_text = findViewById(R.id.editTextServer);
        String url_bit = url_text.getText().toString();
        String url = "http://192.168.86." + url_bit + ":5000/server";
        Snackbar.make(view,"Start sending to" + url,Snackbar.LENGTH_SHORT).show();

        final OkHttpClient client = new OkHttpClient();

        //TODO will AsyncTask/Thread/Queue work better?

        Handler LogRTT_Handler = new Handler();
        Runnable LogRTT_Runnable = new Runnable() {
            @Override
            public void run() {
                if (!Running){
                    LogRTT_Handler.removeCallbacks(this);
                } else{
                    //rate of RTT packet sending(optimal is 200)
                    LogRTT_Handler.postDelayed(this,200);

                    List<String> RangingInfo = new ArrayList<>();
                    for (RangingResult rangingResult: Synchronised_RTT){
                        RangingInfo.add(String.valueOf(rangingResult.getMacAddress()));
                        RangingInfo.add(String.valueOf(rangingResult.getDistanceMm()));
                        RangingInfo.add(String.valueOf(rangingResult.getDistanceStdDevMm()));
                        RangingInfo.add(String.valueOf(rangingResult.getRssi()));
                    }

                    RequestBody RTT_body = new FormBody.Builder()
                            .add("RTT_Timestamp", String.valueOf(RTT_timestamp))
                            .add("RTT_Result", String.valueOf(RangingInfo))
                            .add("IMU_Timestamp",String.valueOf(Closest_IMU_timestamp))
                            .add("Accx", String.valueOf(Synchronised_LastAccReading[0]))
                            .add("Accy", String.valueOf(Synchronised_LastAccReading[1]))
                            .add("Accz", String.valueOf(Synchronised_LastAccReading[2]))
                            .add("Gyrox", String.valueOf(Synchronised_LastGyroReading[0]))
                            .add("Gyroy", String.valueOf(Synchronised_LastGyroReading[1]))
                            .add("Gyroz", String.valueOf(Synchronised_LastGyroReading[2]))
                            .add("Magx", String.valueOf(Synchronised_LastMagReading[0]))
                            .add("Magy",String.valueOf(Synchronised_LastMagReading[1]))
                            .add("Magz",String.valueOf(Synchronised_LastMagReading[2]))
                            .add("Azimuth",String.valueOf(Synchronised_orientationAngles[0]))
                            .add("Pitch",String.valueOf(Synchronised_orientationAngles[1]))
                            .add("Roll",String.valueOf(Synchronised_orientationAngles[2]))
                            .build();

                    Request RTT_request = new Request.Builder()
                            .url(url)
                            .post(RTT_body)
                            .build();

                    //TODO use retrofit rather rolling own requests
                    final Call call = client.newCall(RTT_request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.i("onFailure",e.getMessage());
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response)
                                throws IOException {
                            String result = Objects.requireNonNull(response.body()).string();
                            response.close();
                            Log.i("result",result);
                        }
                    });
                }
            }
        };
        Thread IMU_thread = new Thread(() -> {
            while (Running) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                RequestBody IMU_Body = new FormBody.Builder()
                        .add("Flag","IMU")
                        .add("Timestamp", String.valueOf(SystemClock.elapsedRealtimeNanos()))
                        .add("Accx", String.valueOf(LastAccReading[0]))
                        .add("Accy", String.valueOf(LastAccReading[1]))
                        .add("Accz", String.valueOf(LastAccReading[2]))
                        .add("Gyrox", String.valueOf(LastGyroReading[0]))
                        .add("Gyroy", String.valueOf(LastGyroReading[1]))
                        .add("Gyroz", String.valueOf(LastGyroReading[2]))
                        .add("Magx", String.valueOf(LastMagReading[0]))
                        .add("Magy",String.valueOf(LastMagReading[1]))
                        .add("Magz",String.valueOf(LastMagReading[2]))
                        .add("Azimuth",String.valueOf(orientationAngles[0]))
                        .add("Pitch",String.valueOf(orientationAngles[1]))
                        .add("Roll",String.valueOf(orientationAngles[2]))
                        .build();

                Request IMU_Request = new Request.Builder()
                        .url(url)
                        .post(IMU_Body)
                        .build();

                final Call call = client.newCall(IMU_Request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.i("onFailure",e.getMessage());
                        Snackbar.make(view,"Failed to send data",Snackbar.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response)
                            throws IOException {
                        String result = Objects.requireNonNull(response.body()).string();
                        response.close();
                        Log.i("result",result);
                    }
                });
            }
        });
        //IMU_thread.start();
        LogRTT_Handler.postDelayed(LogRTT_Runnable,1000);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        final float alpha = 0.97f;
        IMU_timestamp = SystemClock.elapsedRealtimeNanos();

        switch (sensorEvent.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:
                LastAccReading[0] = alpha * LastAccReading[0] + (1-alpha) * sensorEvent.values[0];
                LastAccReading[1] = alpha * LastAccReading[1] + (1-alpha) * sensorEvent.values[1];
                LastAccReading[2] = alpha * LastAccReading[2] + (1-alpha) * sensorEvent.values[2];

                String AccX = this.getString(R.string.AccelerometerX,LastAccReading[0]);
                String AccY = this.getString(R.string.AccelerometerY,LastAccReading[1]);
                String AccZ = this.getString(R.string.AccelerometerZ,LastAccReading[2]);
                textAccx.setText(AccX);
                textAccy.setText(AccY);
                textAccz.setText(AccZ);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                LastMagReading[0] = alpha * LastMagReading[0] + (1-alpha) * sensorEvent.values[0];
                LastMagReading[1] = alpha * LastMagReading[1] + (1-alpha) * sensorEvent.values[1];
                LastMagReading[2] = alpha * LastMagReading[2] + (1-alpha) * sensorEvent.values[2];

                String MagX = this.getString(R.string.Magnetic_FieldX,LastMagReading[0]);
                String MagY = this.getString(R.string.Magnetic_FieldY,LastMagReading[1]);
                String MagZ = this.getString(R.string.Magnetic_FieldZ,LastMagReading[2]);
                textMagx.setText(MagX);
                textMagy.setText(MagY);
                textMagz.setText(MagZ);
                break;

            case Sensor.TYPE_GYROSCOPE:
                LastGyroReading[0] = sensorEvent.values[0];
                LastGyroReading[1] = sensorEvent.values[1];
                LastGyroReading[2] = sensorEvent.values[2];
                String GyroX = this.getString(R.string.GyroscopeX,LastGyroReading[0]);
                String GyroY = this.getString(R.string.GyroscopeY,LastGyroReading[1]);
                String GyroZ = this.getString(R.string.GyroscopeZ,LastGyroReading[2]);
                textGrox.setText(GyroX);
                textGroy.setText(GyroY);
                textGroz.setText(GyroZ);
        }

        // Rotation matrix based on current readings from accelerometer and magnetometer.
        SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix,
                LastAccReading, LastMagReading);
        // Express the updated rotation matrix as three orientation angles.
        SensorManager.getOrientation(rotationMatrix, orientationAngles);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        switch (i) {
            case -1:
                Log.d(TAG,"No Contact");
                break;
            case 0:
                Log.d(TAG,"Unreliable");
                break;
            case 1:
                Log.d(TAG,"Low Accuracy");
                break;
            case 2:
                Log.d(TAG,"Medium Accuracy");
                break;
            case 3:
                Log.d(TAG,"High Accuracy");
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (ScanResult scanResult:myWifiManager.getScanResults()){
                if (scanResult.is80211mcResponder() && !APs_MacAddress.contains(scanResult.BSSID)) {
                    RTT_APs.add(scanResult);
                    APs_MacAddress.add((scanResult.BSSID));
                }
            }
            Log.d(TAG,"APs_MacAddress"+"("+APs_MacAddress.size()+")"+": "+APs_MacAddress);
            Log.d(TAG, "RTT_APs"+"("+RTT_APs.size()+")"+": "+RTT_APs);
        }

    }

    private class RTTRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest(){
            RangingRequestDelayHandler.postDelayed(
                    RangingActivity.this::startRangingRequest,RangingDelay);
        }

        @Override
        public void onRangingFailure(int i) {
            Log.d(TAG,"Ranging failed！");
            if (Running) {
                queueNextRangingRequest();
            }
        }

        @SuppressLint("WrongConstant")
        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            List<RangingResult> temp_result = new ArrayList<>();
            for (RangingResult result:list) {
                if (result.getStatus() == 0){
                    temp_result.add(result);
                }
            }
            RTT_timestamp = SystemClock.elapsedRealtimeNanos();
            Synchronised_RTT = temp_result;
            Synchronised_orientationAngles = orientationAngles;
            Synchronised_LastAccReading = LastAccReading;
            Synchronised_LastGyroReading = LastGyroReading;
            Synchronised_LastMagReading = LastMagReading;
            Closest_IMU_timestamp = IMU_timestamp;
            if (Running) {
                if (!temp_result.isEmpty()){
                    rangingActivityAdapter.swapData(temp_result);
                }
                queueNextRangingRequest();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if (keyCode == 85){
            Toast.makeText(getApplicationContext(),"key pressed :)",Toast.LENGTH_SHORT).show();
            Check_Point_Counts ++;
            Counts.setText(String.valueOf(Check_Point_Counts));
        }
        return super.onKeyDown(keyCode,event);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() RangingActivity");
        super.onStop();
        unregisterSensors();
        unregisterReceiver(myWifiScanReceiver);
        Running = false;
    }

    protected void onResume() {
        Log.d(TAG,"onResume() RangingActivity");
        super.onResume();
        registerSensors();
        //registerReceiver(myWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        Running = true;
    }
}