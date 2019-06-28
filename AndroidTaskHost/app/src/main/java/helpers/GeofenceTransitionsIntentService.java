package helpers;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.kereji.fog.taskhost.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class GeofenceTransitionsIntentService extends IntentService {
    protected static final String TAG = "GeofenceTransitionsIS";

    public GeofenceTransitionsIntentService() {
        super(TAG);  // use TAG to name the IntentService worker thread
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event.hasError()) {
            Log.e(TAG, "GeofencingEvent Error: " + event.getErrorCode());
            return;
        }

        //1 Set task host's zone to equal the id of the geofence (1 to 10)
        for (Geofence geofence : event.getTriggeringGeofences()) {
            MqttHelper.setZone(Integer.parseInt(geofence.getRequestId()));
        }

        // Get the transition type.
        int geofenceTransition = event.getGeofenceTransition();

        //2 Set task host's direction depending on event exit or enter
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ) {
            MqttHelper.setDirection(1);
        }else if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT){
            MqttHelper.setDirection(-1);
        }else{
            Log.w("GEOFENCE", "Event not handled"+geofenceTransition);
        }
    }
}