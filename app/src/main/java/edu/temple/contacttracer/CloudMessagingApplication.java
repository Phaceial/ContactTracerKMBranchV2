package edu.temple.contacttracer;

import android.app.Application;

public class CloudMessagingApplication extends Application implements ForegroundInterface{

    boolean isInForeground;


    @Override
    public void setForeground(boolean isInForeground) {
        this.isInForeground = isInForeground;
    }

    @Override
    public boolean isInForeground() {
        return isInForeground;
    }
}
