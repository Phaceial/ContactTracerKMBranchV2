package edu.temple.contacttracer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
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
import java.util.Objects;

public class MyFirebaseMessengerService extends FirebaseMessagingService {

    ForegroundInterface app;
    Date date;
    JSONArray array;
    UUIDContainer uuidContainer;
    SharedPreferences preferences;
    ArrayList<SedentaryEvent> receivedEvents;
    Intent messageIntent;
    int index;
    boolean matched;

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
                                    String json1 = new Gson().toJson(receivedEvents.get(index));
                                    Log.i("Payload", json);
                                    messageIntent.putExtra(Constants.BROADCAST_MESSAGE, json1);
                                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                                } else {
                                    Log.i("Not Foreground", "need to handle this");
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

