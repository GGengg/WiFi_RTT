package com.example.rtttest1;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocalizationActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "LocalizationActivity";

    /**
     * For RTT service
     */
    private WifiRttManager myWifiRTTManager;
    private WifiManager myWifiManager;
    private RTTRangingResultCallback myRTTRangingResultCallback;
    private WifiScanReceiver myWifiScanReceiver;

    List<ScanResult> RTT_APs = new ArrayList<>();
    List<RangingResult> Ranging_Results = new ArrayList<>();
    List<String> APs_MacAddress = new ArrayList<>();

    final Handler RangingRequestDelayHandler = new Handler();

    /**
     * For IMU service
     */
    private SensorManager sensorManager;
    private final HashMap<String, Sensor> sensors = new HashMap<>();
    private float accx, accy, accz, gyrox, gyroy, gyroz, magx, magy, magz;
    private long IMU_timestamp;

    /**
     * For Localization service
     */
    private Paint paint;
    private Path path;
    private Bitmap temp_bitmap;
    private Canvas temp_canvas;
    
    private ImageView floor_plan, location_pin,
            AP1_ImageView, AP2_ImageView, AP3_ImageView, AP4_ImageView, AP5_ImageView, AP6_ImageView;
    int[] floor_plan_location = new int[2];
    int[] AP_location = new int[2];
    int[] pin_location = new int[2];
    double meter2pixel = 32.5; // 1 meter <--> 32.5 pixels for THIS PARTICULAR FLOOR PLAN!
    double bitmap2floorplan = 2.994;
    double screen_offsetX = 241; //in pixels
    int testing_i, testing_j, path_y;

    AccessPoints AP1 = new AccessPoints("b0:e4:d5:39:26:89",40.91,13.15);
    AccessPoints AP2 = new AccessPoints("cc:f4:11:8b:29:4d",34.86,11.45);
    AccessPoints AP3 = new AccessPoints("b0:e4:d5:01:26:f5",48.12,11.45);
    AccessPoints AP4 = new AccessPoints("b0:e4:d5:5f:f2:ad",28.92,12.91);
    AccessPoints AP5 = new AccessPoints("b0:e4:d5:96:3b:95",22.04,13.80);
    AccessPoints AP6 = new AccessPoints("b0:e4:d5:91:ba:5d",18.94,11.45);

    //flag for leaving the activity
    Boolean Running = true;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate() LocalizationActivity");
        Objects.requireNonNull(getSupportActionBar()).hide();

        //receive RTT_APs from main activity
        Intent intent = getIntent();
        RTT_APs = intent.getParcelableArrayListExtra("SCAN_RESULT");

        if (RTT_APs == null || RTT_APs.isEmpty()) {
            Log.d(TAG, "RTT_APs null");
            Toast.makeText(getApplicationContext(),
                    "Please scan for available APs first",
                    Toast.LENGTH_SHORT).show();
            finish();
        } else {
            setContentView(R.layout.activity_localization);

            //RTT Initiation
            myWifiRTTManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
            myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            myRTTRangingResultCallback = new RTTRangingResultCallback();
            myWifiScanReceiver = new WifiScanReceiver();

            registerReceiver(myWifiScanReceiver,
                    new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            for (ScanResult AP:RTT_APs){
                APs_MacAddress.add(AP.BSSID);
            }

            //IMU Initiation
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            sensors.put("Accelerometer", sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensors.put("Gyroscope", sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
            sensors.put("Magnetic", sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

            //Localization initiation
            floor_plan = findViewById(R.id.imageViewFloorplan);
            location_pin = findViewById(R.id.imageViewLocationPin);
            AP1_ImageView = findViewById(R.id.imageViewAP1);
            AP2_ImageView = findViewById(R.id.imageViewAP2);
            AP3_ImageView = findViewById(R.id.imageViewAP3);
            AP4_ImageView = findViewById(R.id.imageViewAP4);
            AP5_ImageView = findViewById(R.id.imageViewAP5);
            AP6_ImageView = findViewById(R.id.imageViewAP6);
            Bitmap bitmap_floor_plan = BitmapFactory.decodeResource(getResources(), R.drawable.floor_plan);

            paint = new Paint();
            path = new Path();

            temp_bitmap = Bitmap.createBitmap(bitmap_floor_plan.getWidth(),
                    bitmap_floor_plan.getHeight(),Bitmap.Config.RGB_565);
            
            temp_canvas = new Canvas(temp_bitmap);
            temp_canvas.drawBitmap(bitmap_floor_plan,0,0,null);

            paint.setAntiAlias(true);
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10);
            paint.setPathEffect(new DashPathEffect(new float[] {20,10,10,10},1));

            setup_pin_location();
            //registerSensors();
            //startRangingRequest();
            //startLoggingData();
            //startScanInBackground();
            update_location_pin();
            Log.d(TAG,"Start localization");
        }
    }

    /**
     * The following method is used to determine the dimension of floor plan
     * in aid of constructing an coordinate plane.
     */
      public void onWindowFocusChanged(boolean hasFocus) {
          super.onWindowFocusChanged(hasFocus);
          if (hasFocus) {
              //left top coordinate
              floor_plan.getLocationOnScreen(floor_plan_location);
              location_pin.getLocationOnScreen(pin_location);
              AP6_ImageView.getLocationOnScreen(AP_location);

              //floor_plan.getLayoutParams();
              Log.i(TAG, "Floorplan" + floor_plan_location[0] + ", " + floor_plan_location[1]);
              Log.i(TAG, "Pin" + pin_location[0] + ", " + pin_location[1]);
              Log.i(TAG, "AP6" + AP_location[0] + ", " + AP_location[1]);
              Log.i(TAG, "Image Width: " + floor_plan.getWidth());
              Log.i(TAG, "Image Height: " + floor_plan.getHeight());
          }
      }

    /** To calculate coordinates
     * top left corner of the screen (55,145), top left corner of the floor plan (241,145)
     * SetY(-26) > left edge of the floor plan
     * width of floor plan (597), height of floor plan (2151)
     * width of bitmap (1788), height of bitmap (6438)
     *
     * FOR PIN LOCATION:
     * setX = y*<meter2pixel>(32.533)+<screen_offsetX>(241), setY = x*<meter2pixel>(32.533)
     *
     * FOR PATH EFFECT:
     * path.moveTo/lineTo( (y*32.533*bitmap2floorplan), ((x*32.533+26)*bitmap2floorplan) )
     */

    private void setup_pin_location(){
        Log.d(TAG,"set_AP_pins");
        AP1_ImageView.setX((float) (AP1.getY()*meter2pixel+screen_offsetX));
        AP1_ImageView.setY((float) (AP1.getX()*meter2pixel));
        AP2_ImageView.setX((float) (AP2.getY()*meter2pixel+screen_offsetX));
        AP2_ImageView.setY((float) (AP2.getX()*meter2pixel));
        AP3_ImageView.setX((float) (AP3.getY()*meter2pixel+screen_offsetX));
        AP3_ImageView.setY((float) (AP3.getX()*meter2pixel));
        AP4_ImageView.setX((float) (AP4.getY()*meter2pixel+screen_offsetX));
        AP4_ImageView.setY((float) (AP4.getX()*meter2pixel));
        AP5_ImageView.setX((float) (AP5.getY()*meter2pixel+screen_offsetX));
        AP5_ImageView.setY((float) (AP5.getX()*meter2pixel));
        AP6_ImageView.setX((float) (AP6.getY()*meter2pixel+screen_offsetX));
        AP6_ImageView.setY((float) (AP6.getX()*meter2pixel));

        location_pin.setX((float) (392+screen_offsetX));
        location_pin.setY(570);
    }

    //TODO animated drawable?
    private void update_location_pin(){
        //TODO better coordinate system?
        //TODO onReceive from backend
        testing_i = 1500;
        testing_j = 570;
        path_y = (int) ((570+26)*bitmap2floorplan);

        location_pin.getLocationOnScreen(pin_location);

        Handler Update_location_Handler = new Handler();
        Runnable Update_location_Runnable = new Runnable() {
            @Override
            public void run() {
                if (Running && (pin_location[1] < testing_i)){
                    Update_location_Handler.postDelayed(this,500);

                    path.moveTo(1174, path_y);
                    testing_j += 20;
                    path_y += 75;

                    location_pin.setY(testing_j);
                    location_pin.getLocationOnScreen(pin_location);

                    Log.d(TAG,"Current location: "+pin_location[0]+", "+pin_location[1]);
                    path.lineTo(1174, path_y);
                    temp_canvas.drawPath(path,paint);
                    floor_plan.setImageBitmap(temp_bitmap);
                } else {
                    Update_location_Handler.removeCallbacks(this);
                }
            }
        };
        Update_location_Handler.postDelayed(Update_location_Runnable,1000);
    }

    @SuppressLint("MissingPermission")
    private void startRangingRequest() {
        RangingRequest rangingRequest =
                new RangingRequest.Builder().addAccessPoints(RTT_APs).build();

        myWifiRTTManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), myRTTRangingResultCallback);
    }

    private void startLoggingData(){
        Log.d(TAG,"StartLoggingData() LocalizationActivity");

        String url = "http://192.168.86.47:5000/server";
        final OkHttpClient client = new OkHttpClient();

        Handler LogRTT_Handler = new Handler();
        Runnable LogRTT_Runnable = new Runnable() {
            @Override
            public void run() {
                if (Running){
                    LogRTT_Handler.postDelayed(this,200);

                    List<String> RangingInfo = new ArrayList<>();
                    for (RangingResult result:Ranging_Results){
                        RangingInfo.add(String.valueOf(result.getMacAddress()));
                        RangingInfo.add(String.valueOf(result.getDistanceMm()));
                        RangingInfo.add(String.valueOf(result.getDistanceStdDevMm()));
                        RangingInfo.add(String.valueOf(result.getRssi()));
                    }

                    RequestBody RTT_body = new FormBody.Builder()
                            .add("Flag","RTT")
                            .add("Timestamp", String.valueOf(SystemClock.elapsedRealtime()))
                            .add("RTT_Result", String.valueOf(RangingInfo))
                            .build();

                    Request RTT_request = new Request.Builder()
                            .url(url)
                            .post(RTT_body)
                            .build();

                    final Call call = client.newCall(RTT_request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.i("onFailure",e.getMessage());
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response)
                                throws IOException{
                            String result = Objects.requireNonNull(response.body()).string();
                            response.close();
                            Log.i("result",result);
                        }
                    });
                } else {
                    LogRTT_Handler.removeCallbacks(this);
                }
            }
        };

        Handler LogIMU_Handler = new Handler();
        Runnable LogIMU_Runnable = new Runnable() {
            @Override
            public void run() {
                if (Running) {
                    LogIMU_Handler.postDelayed(this,50);
                    RequestBody IMU_Body = new FormBody.Builder()
                            .add("Flag","IMU")
                            .add("Timestamp",String.valueOf(IMU_timestamp))
                            .add("accx", String.valueOf(accx))
                            .add("accy", String.valueOf(accy))
                            .add("accz", String.valueOf(accz))
                            .add("gyrox", String.valueOf(gyrox))
                            .add("gyroy", String.valueOf(gyroy))
                            .add("gyroz", String.valueOf(gyroz))
                            .add("magx", String.valueOf(magx))
                            .add("magy", String.valueOf(magy))
                            .add("magz", String.valueOf(magz))
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
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response)
                                throws IOException{
                            String result = Objects.requireNonNull(response.body()).string();
                            response.close();
                            Log.i("result",result);
                        }
                    });
                } else {
                    LogIMU_Handler.removeCallbacks(this);
                }
            }
        };
        //wait x ms (only once) before running
        LogIMU_Handler.postDelayed(LogIMU_Runnable,1000);
        LogRTT_Handler.postDelayed(LogRTT_Runnable,1000);
    }

    private void ScanInBackground(){
        Handler BackgroundScan_Handler = new Handler();
        Runnable BackgroundScan_Runnable = new Runnable() {
            @Override
            public void run() {
                if (Running) {
                    BackgroundScan_Handler.postDelayed(this,3000);
                    myWifiManager.startScan();
                } else {
                    BackgroundScan_Handler.removeCallbacks(this);
                }
            }
        };
        BackgroundScan_Handler.postDelayed(BackgroundScan_Runnable,1000);
    }

    private void registerSensors(){
        for (Sensor eachSensor:sensors.values()){
            sensorManager.registerListener(this,
                    eachSensor,SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    public void unregisterSensors(){
        for (Sensor eachSensor:sensors.values()){
            sensorManager.unregisterListener(this,eachSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        IMU_timestamp = SystemClock.elapsedRealtime();
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accx = sensorEvent.values[0];
                accy = sensorEvent.values[1];
                accz = sensorEvent.values[2];
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magx = sensorEvent.values[0];
                magy = sensorEvent.values[1];
                magz = sensorEvent.values[2];
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyrox = sensorEvent.values[0];
                gyroy = sensorEvent.values[1];
                gyroz = sensorEvent.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private class WifiScanReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            for (ScanResult scanResult:myWifiManager.getScanResults()){
                if (scanResult.is80211mcResponder()) {
                    if (!APs_MacAddress.contains(scanResult.BSSID)) {
                        RTT_APs.add(scanResult);
                    }
                }
            }
            //TODO Handle getmaxpeers
        }
    }

    private class RTTRangingResultCallback extends RangingResultCallback {

        //Start next request
        private void queueNextRangingRequest() {
            RangingRequestDelayHandler.postDelayed(
                    LocalizationActivity.this::startRangingRequest, 100);
        }

        @Override
        public void onRangingFailure(int i) {
            queueNextRangingRequest();
        }

        @SuppressLint("WrongConstant")
        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            Ranging_Results.clear();
            for (RangingResult result:list) {
                if (result.getStatus() == 0){
                    Ranging_Results.add(result);
                }
            }
            queueNextRangingRequest();
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() LocalizationActivity");
        super.onStop();
        unregisterSensors();
        unregisterReceiver(myWifiScanReceiver);
        Running = false;
    }

    protected void onResume() {
        Log.d(TAG,"onResume() LocalizationActivity");
        super.onResume();
        Running = true;
    }
}
