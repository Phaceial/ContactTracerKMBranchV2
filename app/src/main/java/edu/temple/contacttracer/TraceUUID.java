package edu.temple.contacttracer;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

public class TraceUUID implements Serializable {

    private UUID uuid;
    private Date date;

    public TraceUUID() {
        uuid = UUID.randomUUID();
        date = new Date();
    }

    protected TraceUUID(Parcel in) {
        uuid = UUID.fromString(in.readString());
        date = new Date(in.readLong());
    }

    public UUID getUuid() {
        return uuid;
    }

    public Date getDate() {
        return date;
    }
}
