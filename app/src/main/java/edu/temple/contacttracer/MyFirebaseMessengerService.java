package edu.temple.contacttracer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;

public class MyFirebaseMessengerService extends FirebaseMessagingService {

    ForegroundInterface app;
    Date date;
    JSONArray array;
    UUIDContainer uuidContainer;
    SharedPreferences preferences;
    ArrayList<SedentaryEvent> receivedEvents;
    Intent messageIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        app = (ForegroundInterface) getApplicationContext();
    }

    @Override
    public void onNewToken(@NonNull String s) {
        Log.d("FCM Token", s);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String json = preferences.getString(Constants.RECEIVED_EVENTS, "");
        Type type = new TypeToken<ArrayList<SedentaryEvent>>() {
        }.getType();
        receivedEvents = new Gson().fromJson(json, type);
        Log.i("Received Loaded", receivedEvents.toString());

        //Only getting data from FCM, don't need filter for in foreground or not
        //Keeping foreground interface if later implementation requires it
        if (remoteMessage.getFrom().equals("/topics/TRACKING")) {
            messageIntent = new Intent(Constants.BROADCAST_MESSAGE);
            messageIntent.putExtra(Constants.MESSSAGE_KEY, remoteMessage.getData().get("payload"));
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        } else if (remoteMessage.getFrom().equals("/topics/TRACING")) {
            try {
                JSONObject tracingObject = new JSONObject(remoteMessage.getData().get("payload"));
                date = new Date(tracingObject.getLong("date"));
                Log.i("Tracing object", tracingObject.toString());
                array = tracingObject.getJSONArray("uuids");
                Log.i("Tracing array", array.toString());

                if (!checkSelf(array)) {
                    Log.i("Tracing", "Not self so perform check");
                    for (int i = 0; i < array.length(); i++) {
                        for(int j = 0; j < receivedEvents.size(); j++){
                            if(array.get(i).toString().equals(receivedEvents.get(j).uuid.toString())){
                                Log.i("Matched", "You got Rona");
                                if(app.isInForeground()) {
                                    Log.i("Foreground", String.valueOf(app.isInForeground()));
                                    messageIntent = new Intent(Constants.BROADCAST_CONTACT);
                                    String json1 = new Gson().toJson(receivedEvents.get(j));
                                    messageIntent.putExtra(Constants.BROADCAST_MESSAGE, json1);
                                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                                } else {
                                    Log.i("Not Foreground", "make notification work");
                                    Intent notificationIntent = new Intent(Constants.BROADCAST_CONTACT);
                                    String json1 = new Gson().toJson(receivedEvents.get(j));
                                    notificationIntent.putExtra(Constants.BROADCAST_MESSAGE, json1);
                                    PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, notificationIntent, PendingIntent.FLAG_ONE_SHOT);


                                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                    NotificationChannel channel = new NotificationChannel(Constants.channelID, "Covid-19 Notification", NotificationManager.IMPORTANCE_HIGH);
                                    nm.createNotificationChannel(channel);

                                    Notification notification = new NotificationCompat.Builder(this, Constants.channelID)
                                            .setSmallIcon(R.drawable.ic_launcher_background)
                                            .setContentTitle("Contact Tracing")
                                            .setContentText("You've come in contact with someone who tested for Covid-19")
                                            .setAutoCancel(true)
                                            .setContentIntent(pendingIntent)
                                            .build();
                                    nm.notify(1, notification);
                                }
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean checkSelf(JSONArray array) throws JSONException {
        uuidContainer = UUIDContainer.getUUIDContainer(this);

        boolean contains = false;
        Log.i("checking uuids", array.get(0).toString());
        Log.i("checking uuids", uuidContainer.getCurrentUUID().getUuid().toString());

        for (int i = 0; i < array.length(); i++) {
            if (array.get(i).toString().equals(uuidContainer.getCurrentUUID().getUuid().toString()))
                contains = true;
        }
        Log.i("boolean", String.valueOf(contains));
        return contains;
    }


}

