package com.example.csresearchaccelerometerkey;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Calendar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivitiy";
    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    final DatabaseReference myRef = database.getReference("AccelerometerData/");
    private SensorManager sensorManager;
    private Button isKeyButton;
    private Button isCarButton;
    private EditText sendMessage;
    private TextView xAccelTextView;
    private TextView yAccelTextView;
    private TextView zAccelTextView;
    private TextView xAccelTextViewFirebase;
    private TextView yAccelTextViewFirebase;
    private TextView zAccelTextViewFirebase;
    Sensor accelerometer;
    private HashMap<Integer, AccelerometerEntry> averages = new HashMap<>();
    private HashMap<Integer, AccelerometerEntry> averagesFireBase = new HashMap<>();
    private int lastSecond = 0;
    //0 == not initialized
    //1 == key
    //2 == car
    private int whatType;
    private int continuedImbalance = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: Initializing Sensor Services");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(MainActivity.this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        //initialize screen widgets
        isKeyButton = findViewById(R.id.key);
        isCarButton = findViewById(R.id.car);
        sendMessage = findViewById(R.id.input);
        xAccelTextView = findViewById(R.id.xAccel);
        yAccelTextView = findViewById(R.id.yAccel);
        zAccelTextView = findViewById(R.id.zAccel);
        xAccelTextViewFirebase = findViewById(R.id.xAccelFirebase);
        yAccelTextViewFirebase = findViewById(R.id.yAccelFirebase);
        zAccelTextViewFirebase = findViewById(R.id.zAccelFirebase);

        Log.d(TAG, "onCreate: Registered accelerometer listener");

        //initialize averages
        for(int i = 0; i < 60; i++) {
            averages.put(i, new AccelerometerEntry(
                    0,
                    0,
                    0,
                    0,
                    0
            ));
        }

        isKeyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "key button clicked");
                whatType = 1;
            }
        });

        isCarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "car button clicked");
                whatType = 2;
            }
        });

        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                AccelerometerEntry value = dataSnapshot.getValue(AccelerometerEntry.class);
                if(value == null) {
                    Log.d(TAG, "FIREBASE VALUE WAS NULL");
                    return;
                }

                averagesFireBase.put(value.second, value);
                //Log.d(TAG, "\nValue is: " + value.toString());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    /**
     * Pu
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

        float xAcceleration = event.values[0];
        float yAcceleration = event.values[1];
        float zAcceleration = event.values[2];

        this.xAccelTextView.setText(Float.toString(xAcceleration));
        this.yAccelTextView.setText(Float.toString(yAcceleration));
        this.zAccelTextView.setText(Float.toString(zAcceleration));

        int curSecond = Calendar.getInstance().get(Calendar.SECOND);

        //if the last entry exists and is within the same second increase the entries
        // Log.d(TAG, Integer.toString(curSecond));
        AccelerometerEntry stored = this.averages.get(curSecond);

        AccelerometerEntry newEntry;
        if(this.lastSecond == curSecond) {
            int entries = stored.entries;
            newEntry = new AccelerometerEntry(
                    curSecond,
                    ((xAcceleration + stored.xAccel * entries)/(entries+1)),
                    ((yAcceleration+ stored.yAccel * entries)/(entries+1)),
                    ((zAcceleration + stored.zAccel * entries)/(entries+1)),
                    (entries+1)
            );

        } else { //reset number of entries to 1
            int prevSecond = this.lastSecond;
            this.lastSecond = curSecond;
            newEntry = new AccelerometerEntry(
                    curSecond,
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    1
            );
            AccelerometerEntry prevSecondEntry = this.averages.get(prevSecond);
            AccelerometerEntry prevSecondEntryFirebase = this.averagesFireBase.get(prevSecond);

            if(prevSecondEntryFirebase != null) {
                xAccelTextViewFirebase.setText(Float.toString(prevSecondEntryFirebase.xAccel));
                yAccelTextViewFirebase.setText(Float.toString(prevSecondEntryFirebase.yAccel));
                zAccelTextViewFirebase.setText(Float.toString(prevSecondEntryFirebase.zAccel));
            }
            if(prevSecondEntry == null) {
                Log.d(TAG, "NULL ENTRY FOUND AT " + Integer.toString(prevSecond));
            }
            if(this.whatType == 1) {
               // myRef.setValue(prevSecondEntry);
            } else if (this.whatType == 2) {
                if(this.averagesFireBase.containsKey(prevSecond) &&
                        this.notWithinAcceptableBounds(prevSecondEntry,
                                this.averagesFireBase.get(prevSecond))) {
                    this.continuedImbalance += 1;
                    if(continuedImbalance > 3) {
                        findViewById(android.R.id.content).setBackgroundColor(Color.argb(255, 255, 0, 0));
                    }
                    Log.d(TAG, "Inequality at second " + Integer.toString(curSecond-1));
                } else if(!this.averagesFireBase.containsKey(prevSecond)) {
                    Log.d(TAG, "UNCONTAINED SECOND FOR FIREBASE");
                    //this.averagesFireBase.remove(prevSecond);
                } else if(!this.notWithinAcceptableBounds(prevSecondEntry,
                        this.averagesFireBase.get(prevSecond))) {
                    findViewById(android.R.id.content).setBackgroundColor(Color.argb(255, 0, 255, 0));
                    this.continuedImbalance = 0;
                    Log.d(TAG, "Within bounds");
                }
            }
        }

        this.averages.remove(curSecond);
        this.averages.put(curSecond, newEntry);
        if(this.whatType == 1) {
            myRef.setValue(newEntry);
        }

    }

    private boolean notWithinAcceptableBounds(AccelerometerEntry ae1, AccelerometerEntry ae2) {
        return !(this.floatWithinBounds(ae1.xAccel, ae2.xAccel)
                && this.floatWithinBounds(ae1.yAccel, ae2.yAccel)
                && this.floatWithinBounds(ae1.zAccel, ae2.zAccel));
    }

    private boolean floatWithinBounds(float f1, float f2) {
        Log.d(TAG, "\nlocalVal = " + Float.toString(f1));
        Log.d(TAG, "\nFireVal = " + Float.toString(f2));
        return (Math.abs(f1 - f2) < 4.5f);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public static class AccelerometerEntry {
        public int second;
        public float xAccel;
        public float yAccel;
        public float zAccel;
        public int entries;

        public AccelerometerEntry(int second, float xAccel, float yAccel, float zAccel, int entries) {
            this.second = second;
            this.xAccel = xAccel;
            this.yAccel = yAccel;
            this.zAccel = zAccel;
            this.entries = entries;
        }

        public AccelerometerEntry() {

        }

        @Override
        public String toString() {
            return    " x: " + Float.toString(this.xAccel) + "\n"
                    + " y: " + Float.toString(this.yAccel) + "\n"
                    + " z: " + Float.toString(this.zAccel) + "\n"
                    + " entries: " + Integer.toString(this.entries) + "\n";

        }
    }

}
