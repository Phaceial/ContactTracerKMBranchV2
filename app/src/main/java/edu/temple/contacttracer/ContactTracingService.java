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

public class ContactTracingService extends Service {

    LocationManager locationManager;
    LocationListener locationListener;

    Location lastLocation;
    SharedPreferences sharedPreferences;

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
                    if (location.getTime() - lastLocation.getTime() >= (tracingTime * 1000)) {
                        tracePointDetected();
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
    private void tracePointDetected() {
        String message = lastLocation.getLatitude() + " - " + lastLocation.getLongitude() + " at " + lastLocation.getTime();
        Log.i("Trace Data", message);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Memory leaks are bad, m'kay?
        locationManager.removeUpdates(locationListener);
    }
}
