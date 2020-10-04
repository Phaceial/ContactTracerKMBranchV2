package edu.temple.contacttracer;

import java.util.Date;
import java.util.UUID;

public class SedentaryEvent {
        UUID uuid;
        Date date;
        double latitude;
        double longitude;
        long sedentary_begin;
        long sedentary_end;


    public SedentaryEvent(UUID uuid, double latitude, double longitude, long sedentary_begin, long sedentary_end) {
        this.uuid = uuid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sedentary_begin = sedentary_begin;
        this.sedentary_end = sedentary_end;
    }

    public void setDate(){
        this.date = new Date();
    }
}

