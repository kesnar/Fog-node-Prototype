package com.kereji.fog.taskhost;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.EditText;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.Map;

import helpers.GeofenceTransitionsIntentService;
import helpers.MqttHelper;

public class MainActivity extends AppCompatActivity
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Status>{
    private static Location currentLocation;
    MqttHelper mqttHelper;

    TextView dataReceived;

    protected ArrayList<Geofence> mGeofenceList;
    protected GoogleApiClient mGoogleApiClient;

    String brokerIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //1 Create google api client to connect to google play services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

        //2 Show the UI layout to the screen
        setContentView(R.layout.activity_main);

        dataReceived = (TextView) findViewById(R.id.dataReceived);


        mGeofenceList = new ArrayList<Geofence>();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //3 on destroy disconnect from MQTT Broker
        try {
            mqttHelper.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }


    }

    //4 Read Broker IP from UI
    public void newBrokerIP(View view) {
        Log.w("Debug","onclick");
        EditText editText = (EditText) findViewById(R.id.brokerIP);
        brokerIP = editText.getText().toString();


        dataReceived.setText("Connecting to " + brokerIP);
        startMqtt();
    }

    //5 Build the Geofences
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling addgeoFences()
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //6 create geofence list centered on broker ip
    public void populateGeofenceList(double a, double b) {
        for (int i =1; i<=10;i++) {
            mGeofenceList.add(new Geofence.Builder()
                    .setRequestId(""+i)
                    .setCircularRegion(
                            a,
                            b,
                            i*50
                    )
                    .setExpirationDuration(0)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                            Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build());
        }
    }

    private void startMqtt() {

        //7 get device ID to be used as client name
        String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.w("Debug", "THIS IS THE DEVICE ID!!!! " + deviceId);

        //8 define subscription topics
        ArrayList<String> subList = new ArrayList<String>();

        subList.add("fromBroker/" + deviceId);
        subList.add("stats/call");
        subList.add("stats/broker");

        mqttHelper = new MqttHelper(getApplicationContext(), brokerIP, deviceId, subList, this);
        /*mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                Log.w("Debug", s);
                dataReceived.setText(s);
            }

            @Override
            public void connectionLost(Throwable throwable) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.w("Debug", mqttMessage.toString() + " from " + topic);
                dataReceived.setText(mqttMessage.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });*/
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mGoogleApiClient.isConnecting() || !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnecting() || mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        //9 when connected to google play services , find current location and set task host's initial location on the geofencing grid
        Log.w("Location", "Inside");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

            double lat = lastLocation.getLatitude(), lon = lastLocation.getLongitude();
            Log.w("Location", "Current location is: "+ lat + "," + lon);

            currentLocation = lastLocation;
            Log.w("Location", "Current location is: "+ currentLocation.toString());

            MqttHelper.setLocation(currentLocation);

        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Do something with result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onResult(Status status) {

    }

    //10 Called when we have broker position
    public void setGeoFence(float a, float b) {
        // BROKER LAT~LNG
        populateGeofenceList(a,b);

        // Kick off the request to build GoogleApiClient.
        //buildGoogleApiClient();

        //11 Define GeofenceTransitionsIntentService as geofencing intent
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(this); // Result processed in onResult().
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
        }

        Log.w("newBrokerIP", "GEOFENCES SET");

        for (Geofence i : mGeofenceList) {
            Log.w("newBrokerIP", i.getRequestId());
        }

    }

    //12 Set the text on UI to s
    public void setText(String s) {
        dataReceived.setText(s);
    }

/*    final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d("Status Changed", String.valueOf(status));
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d("Provider Enabled", provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d("Provider Disabled", provider);
        }
    };*/
}