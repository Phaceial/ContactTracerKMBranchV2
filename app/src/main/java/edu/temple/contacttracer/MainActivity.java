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
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements StartupFragment.FragmentInteractionInterface {

    FragmentManager fm;
    Intent serviceIntent;
    UUIDContainer uuidContainer;
    IntentFilter filter;
    ForegroundInterface app;
    ArrayList<SedentaryEvent> selfEvents;
    ArrayList<SedentaryEvent> receivedEvents;
    SedentaryEvent event;
    SharedPreferences preferences;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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
            } catch (Exception e) {
                Log.i("Invalid message received", "Invalid event received");
            }
            if (!checkSelf(event))
                receivedEvents.add(0, event);
            save();

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
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

        filter = new IntentFilter(getPackageName());
        app = (ForegroundInterface) getApplicationContext();

        FirebaseMessaging.getInstance().subscribeToTopic("TRACKING").addOnCompleteListener(new OnCompleteListener<Void>() {
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
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void stopService() {
        stopService(serviceIntent);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void settingsMenu() {
        fm.beginTransaction()
                .replace(R.id.container, new SettingsFragment())
                .addToBackStack(null)
                .commit();
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
    }

    public boolean checkSelf(SedentaryEvent event) {
        boolean contains = false;
        for (SedentaryEvent stored : selfEvents) {
            if (stored.uuid.toString().equals(event.uuid.toString()))
                contains = true;
        }
        return contains;
    }

    public void inRange(SedentaryEvent event) {

    }

    public void save() {
        int TWO_WEEKS = 1209600000;
        Date twoWeeks = new Date((new Date()).getTime() - TWO_WEEKS);

        for (SedentaryEvent saved : selfEvents) {
            if (saved.date.before(twoWeeks)) {
                selfEvents.remove(saved);
            }
        }

        for (SedentaryEvent saved : receivedEvents) {
            if (saved.date.before(twoWeeks)) {
                receivedEvents.remove(saved);
            }
        }

        String json = new Gson().toJson(selfEvents);
        String json1 = new Gson().toJson(receivedEvents);
        preferences.edit().putString(Constants.SEDENTARY_EVENTS, json).apply();
        preferences.edit().putString(Constants.RECEIVED_EVENTS, json1).apply();
    }
}
