package edu.temple.contacttracer;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessengerService extends FirebaseMessagingService {

    ForegroundInterface app;

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

        //Only getting data from FCM, don't need filter for in foreground or not
        //Keeping foreground interface if later implementation requires it
        Intent messageIntent = new Intent(getPackageName());
        messageIntent.putExtra(Constants.MESSSAGE_KEY, remoteMessage.getData().get("payload"));
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
    }

}