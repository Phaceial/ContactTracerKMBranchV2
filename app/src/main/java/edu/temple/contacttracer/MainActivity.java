package edu.temple.contacttracer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements StartupFragment.FragmentInteractionInterface, DatePickerFragment.DateInterface {

    FragmentManager fm;
    Intent serviceIntent;
    UUIDContainer uuidContainer;
    IntentFilter broadcastFilter;
    ForegroundInterface app;
    ArrayList<SedentaryEvent> selfEvents;
    ArrayList<SedentaryEvent> receivedEvents;
    SedentaryEvent event;
    SharedPreferences preferences;
    Location location;


    private final String TRACKING_TOPIC = "TRACKING";
    private final String TRACING_TOPIC = "TRACING";

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("Broadcast", intent.getAction());
            Log.i("Broadcast", getPackageName());
            if (intent.getAction().equals(Constants.BROADCAST_MESSAGE)) {
                try {
                    String json = intent.getStringExtra(Constants.MESSSAGE_KEY);

                    Log.i("Payload", json);
                    Type type = new TypeToken<SedentaryEvent>() {
                    }.getType();
                    event = new Gson().fromJson(json, type);
                    event.setDate();
                    Log.i("Message to object", event.uuid.toString());
                    Log.i("Message to object", String.valueOf(event.latitude));
                    Log.i("Message to object", String.valueOf(event.longitude));
                    Log.i("Message to object", String.valueOf(event.sedentary_begin));
                    Log.i("Message to object", String.valueOf(event.sedentary_end));
                    Log.i("Message to object", event.date.toString());

                    if (!checkSelf(event) && inRange(event)) {
                        receivedEvents.add(0, event);
                        Log.i("Other User Event", "saved to list");
                        save(receivedEvents, Constants.RECEIVED_EVENTS);
                    }
                } catch (Exception e) {
                    Log.i("Invalid message received", "Invalid event received");
                }

            } else if (intent.getAction().equals(Constants.BROADCAST_LOCATION)) {
                location = intent.getParcelableExtra(Constants.LOCATION_KEY);
                Log.i("Broadcast", "Location Updated");
            } else if (intent.getAction().equals(Constants.BROADCAST_CONTACT)) {
                String json = intent.getStringExtra(Constants.BROADCAST_MESSAGE);
                Log.i("Payload", "json");
                Type type = new TypeToken<SedentaryEvent>() {
                }.getType();
                event = new Gson().fromJson(json, type);
                Log.i("Message to object", String.valueOf(event.latitude));
                Log.i("Message to object", String.valueOf(event.longitude));
                TraceFragment mapFragment = new TraceFragment();
                LatLng loc = new LatLng(event.latitude, event.longitude);
                fm.beginTransaction()
                        .replace(R.id.container, mapFragment)
                        .addToBackStack(null)
                        .commit();

                mapFragment.setLocation(loc, event.date);
            }

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        app.setForeground(true);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.contains(Constants.SEDENTARY_EVENTS)) {
            String json = preferences.getString(Constants.SEDENTARY_EVENTS, "");
            Type type = new TypeToken<ArrayList<SedentaryEvent>>() {
            }.getType();
            selfEvents = new Gson().fromJson(json, type);
        } else
            selfEvents = new ArrayList<>();

        if (preferences.contains(Constants.RECEIVED_EVENTS)) {
            String json = preferences.getString(Constants.RECEIVED_EVENTS, "");
            Type type = new TypeToken<ArrayList<SedentaryEvent>>() {
            }.getType();
            receivedEvents = new Gson().fromJson(json, type);
        } else
            receivedEvents = new ArrayList<>();

        app.setForeground(true);
        Log.i("Self Events", selfEvents.toString());
        Log.i("Received Events", receivedEvents.toString());
        Log.i("Foreground", String.valueOf(app.isInForeground()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve UUID container from storage
        uuidContainer = UUIDContainer.getUUIDContainer(this);

        // Get today's date with the time set to 12:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If no IDs generated today or at all, generate new ID
        if (uuidContainer.getCurrentUUID() == null || uuidContainer.getCurrentUUID().getDate().before(calendar.getTime()))
            uuidContainer.generateUUID();


        // Notification channel created for foreground service
        NotificationChannel defaultChannel = new NotificationChannel("default",
                "Default",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        getSystemService(NotificationManager.class).createNotificationChannel(defaultChannel);


        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        serviceIntent = new Intent(this, ContactTracingService.class);

        fm = getSupportFragmentManager();

        if (fm.findFragmentById(R.id.container) == null)
            fm.beginTransaction()
                    .add(R.id.container, new StartupFragment())
                    .commit();

        broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(Constants.BROADCAST_MESSAGE);
        broadcastFilter.addAction(Constants.BROADCAST_LOCATION);
        broadcastFilter.addAction(Constants.BROADCAST_CONTACT);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(broadcastReceiver, broadcastFilter);

        app = (ForegroundInterface) getApplicationContext();

        FirebaseMessaging.getInstance().subscribeToTopic(TRACKING_TOPIC).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                String msg = "Successfully Subscribed";
                if (!task.isSuccessful())
                    msg = "Subscribing Failed";
                Log.i("Subscription", msg);
            }
        });

        FirebaseMessaging.getInstance().subscribeToTopic(TRACING_TOPIC).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                String msg = "Successfully Subscribed";
                if (!task.isSuccessful())
                    msg = "Subscribing Failed";
                Log.i("Subscription", msg);
            }
        });

    }

    @Override
    public void startService() {
        startService(serviceIntent);
    }

    @Override
    public void stopService() {
        stopService(serviceIntent);
    }

    @Override
    public void settingsMenu() {
        fm.beginTransaction()
                .replace(R.id.container, new SettingsFragment())
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void reportPositive() {
        DatePickerFragment newFragment = new DatePickerFragment();
        newFragment.show(fm, null);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "You must grant Location permission", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        app.setForeground(false);
        Log.i("Foreground", String.valueOf(app.isInForeground()));
    }

    public boolean checkSelf(SedentaryEvent event) {
        boolean contains = false;
        try {
            for (SedentaryEvent stored : selfEvents) {
                if (stored.uuid.toString().equals(event.uuid.toString()))
                    contains = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contains;
    }

    public boolean inRange(SedentaryEvent event) {
        boolean inRange = false;
        Location eventLocation = new Location("");
        eventLocation.setLatitude(event.latitude);
        eventLocation.setLongitude(event.longitude);
        if (location.distanceTo(eventLocation) <= 10)
            inRange = true;

        return inRange;
    }

    public void save(ArrayList<SedentaryEvent> list, String string) {
        int TWO_WEEKS = 1209600000;
        Date twoWeeks = new Date((new Date()).getTime() - TWO_WEEKS);

        for (SedentaryEvent saved : list) {
            if (saved.date.before(twoWeeks)) {
                list.remove(saved);
            }
        }

        String json = new Gson().toJson(list);
        preferences.edit().putString(string, json).apply();
    }

    public void reportPositiveDate(final Date date) {
        Log.i("Processing list", date.toString());
        String url = "https://kamorris.com/lab/ct_tracing.php";
        RequestQueue queue = Volley.newRequestQueue(this);

        final JSONArray uuids = uuidContainer.toJsonArray();
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i("Volley Tracing Works", response.toString());
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i("Volley Tracing Failure", error.toString());
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("date", String.valueOf(date.getTime()));
                params.put("uuids", uuids.toString());
                return params;
            }
        };

        queue.add(stringRequest);

    }

    public void launchMap() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

}
