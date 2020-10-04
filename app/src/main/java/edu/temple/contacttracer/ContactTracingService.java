package edu.temple.contacttracer;

import android.Manifest;
import android.app.Notification;
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

import androidx.preference.PreferenceManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ContactTracingService extends Service {

    LocationManager locationManager;
    LocationListener locationListener;

    Location lastLocation;
    SharedPreferences sharedPreferences;
    ArrayList<SedentaryEvent> sedentaryEvents;
    JsonObjectRequest jsonObjectRequest;
    UUIDContainer uuidContainer;

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

        uuidContainer = UUIDContainer.getUUIDContainer(this);;


        // If the preferences are updated from settings, grab the new values
        sharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                tracingTime = Integer.parseInt(sharedPreferences.getString(Constants.PREF_KEY_CONTACT_TIME, Constants.CONTACT_TIME_DEFAULT));
            }
        });

        locationManager = getSystemService(LocationManager.class);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (lastLocation != null) {
                    if (location.getTime() - lastLocation.getTime() >= (tracingTime * 1000 * 60)) {
                        Log.i("Event ", " has been triggered");
                        tracePointDetected(location);
                    }
                }
                lastLocation = location;
            }

            public void onStatusChanged(String provider, int status, Bundle extras) { }
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // GPS is the only really useful provider here, since we need
            // high fidelity meter-level accuracy
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    0,
                    LOCATION_UPDATE_DISTANCE,
                    locationListener);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this, "default")
                .setContentTitle("Contact Tracing Active")
                .setContentText("Click to change app settings")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        return super.onStartCommand(intent, flags, startId);
    }

    /** Should be called when a user has loitered in a location for
     * longer than the designated time.
     */
    private void tracePointDetected(Location location) {
        SedentaryEvent sedentaryEvent = new SedentaryEvent(uuidContainer.getCurrentUUID().getUuid(),lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getTime(), location.getTime());
        sedentaryEvents.add(sedentaryEvent);
        save();
        sendPost(sedentaryEvent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Memory leaks are bad, m'kay?
        locationManager.removeUpdates(locationListener);
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
