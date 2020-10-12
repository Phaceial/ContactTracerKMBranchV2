package edu.temple.contacttracer;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

/**
 * Container for TraceUUIDs. Sub-optimal due to disk access, which is performed
 * to keep container in sync across various points of access
 * More efficient solution would keep container in memory as singleton (perhaps in application
 * context) and only save to disk on exit...in fact that approach might be
 * employed in future version
 */
public class UUIDContainer implements Serializable {

    private final String TRACER_ID_FILE = "traceridentifiers";

    // "Context" is not serializable, so label it 'transient'
    // so it won't be saved
    transient private Context context;

    private ArrayList<TraceUUID> UUIDs;

    private UUIDContainer (Context context) {
        UUIDs = new ArrayList<>();
        this.context = context;
        UUIDContainer uuidContainer = null;

        FileInputStream fis = null;
        try {
            fis = context.openFileInput(TRACER_ID_FILE);
            ObjectInputStream is = new ObjectInputStream(fis);
            uuidContainer = (UUIDContainer) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (uuidContainer == null)
            UUIDs = new ArrayList<TraceUUID>();
        else
            UUIDs.addAll(uuidContainer.getUUIDs());

        removeExpiredUUIDs();
        Log.d("Total IDs", String.valueOf(UUIDs.size()));
    }

    private ArrayList<TraceUUID> getUUIDs() {
        return UUIDs;
    }

    public TraceUUID getCurrentUUID() {
        return UUIDs.size() > 0 ? UUIDs.get(0) : null;
    }

    public void generateUUID() {
        UUIDs.add(0, new TraceUUID());
        Log.d("Generated UUID", UUIDs.get(0).toString());
        save();
    }

    private void removeExpiredUUIDs(){
        long TWO_WEEKS_IN_MILLIS = 1209600000;
        Date twoWeeks = new Date((new Date()).getTime() - TWO_WEEKS_IN_MILLIS);
        for (TraceUUID traceUUID : UUIDs) {
            if (traceUUID.getDate().before(twoWeeks))
                UUIDs.remove(traceUUID);
        }
        save();
    }

    public static UUIDContainer getUUIDContainer(Context context){
        return new UUIDContainer(context);
    }

    private void save() {
        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput(TRACER_ID_FILE, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(this);
            os.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JSONArray toJsonArray(){
        JSONArray array = new JSONArray();

        for(TraceUUID uuid : UUIDs){
            array.put(uuid.getUuid());
        }

        return array;
    }
}
