package edu.temple.contacttracer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.content.ContentValues.TAG;


public class ContactTracingService extends Service {
    public static final String channelID = "Tracing Service";
    LocationManager lm;
    LocationListener ll;

    Location lastLocation;
    SharedPreferences sharedPreferences;
    ArrayList<SedentaryEvent> sedentaryEvents;
    UUIDContainer uuidContainer;
    Intent locationIntent;

    private int tracingTime;
    private final int LOCATION_UPDATE_DISTANCE = 10;

    public ContactTracingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // SharedPreferences that is object automatically
        // used by Settings framework
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        tracingTime = Integer.parseInt(sharedPreferences.getString(Constants.PREF_KEY_CONTACT_TIME, Constants.CONTACT_TIME_DEFAULT));
        System.out.println("this is tracing time " + tracingTime);
        if (sharedPreferences.contains(Constants.SEDENTARY_EVENTS)) {
            String json = sharedPreferences.getString(Constants.SEDENTARY_EVENTS, "");
            Type type = new TypeToken<ArrayList<SedentaryEvent>>() {}.getType();
            sedentaryEvents = new Gson().fromJson(json, type);
        } else
            sedentaryEvents = new ArrayList<>();

        Log.i("Saved self Events", sedentaryEvents.toString());

        uuidContainer = UUIDContainer.getUUIDContainer(this);;


        // If the preferences are updated from settings, grab the new values
        sharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                tracingTime = Integer.parseInt(sharedPreferences.getString(Constants.PREF_KEY_CONTACT_TIME, Constants.CONTACT_TIME_DEFAULT));
            }
        });


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lm = getSystemService(LocationManager.class);
        Intent notificationIntent = new Intent(ContactTracingService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(ContactTracingService.this, 0, notificationIntent, 0);


        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(channelID, "Tracing Notifications", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, channelID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Contact Tracing")
                .setContentText("Currently Tracing location")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        ll = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.i(TAG, "lat updated " + location.getLatitude());
                Log.i(TAG, "long updated " + location.getLongitude());
                if (lastLocation != null) {
                    if (location.getTime() - lastLocation.getTime() >= (tracingTime * 1000 * 60)) {
                        Log.i("Event ", " has been triggered");
                        tracePointDetected(location);
                    }
                }
                locationIntent = new Intent(Constants.BROADCAST_LOCATION);
                locationIntent.putExtra(Constants.LOCATION_KEY, location);
                LocalBroadcastManager.getInstance(ContactTracingService.this).sendBroadcast(locationIntent);

                lastLocation = location;
            }


            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, LOCATION_UPDATE_DISTANCE, ll);
        }

        return START_STICKY;
    }

    /** Should be called when a user has loitered in a location for
     * longer than the designated time.
     */
    private void tracePointDetected(Location location) {
        SedentaryEvent sedentaryEvent = new SedentaryEvent(uuidContainer.getCurrentUUID().getUuid(),lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getTime(), location.getTime());
        sedentaryEvents.add(0,sedentaryEvent);
        sedentaryEvents.get(0).setDate();
        save();
        sendPost(sedentaryEvent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Memory leaks are bad, m'kay?
        lm.removeUpdates(ll);
    }

    private void sendPost(final SedentaryEvent sedentaryEvent) {
            String url = "https://kamorris.com/lab/ct_tracking.php";
            RequestQueue queue = Volley.newRequestQueue(this);
            StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.i("Volley Works", response.toString());
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.i("Volley Failure", error.toString());
                }
            }) {
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> params = new HashMap<>();
                    params.put("uuid", sedentaryEvent.uuid.toString());
                    params.put("latitude", String.valueOf(sedentaryEvent.latitude));
                    params.put("longitude", String.valueOf(sedentaryEvent.longitude));
                    params.put("sedentary_begin", String.valueOf(sedentaryEvent.sedentary_begin));
                    params.put("sedentary_end", String.valueOf(sedentaryEvent.sedentary_end));
                    Log.i("Data Sent", params.toString());
                    return params;
                }
            };
        queue.add(stringRequest);
    }

    public void save(){
        String json = new Gson().toJson(sedentaryEvents);
        sharedPreferences.edit().putString(Constants.SEDENTARY_EVENTS, json).apply();
    }
}
