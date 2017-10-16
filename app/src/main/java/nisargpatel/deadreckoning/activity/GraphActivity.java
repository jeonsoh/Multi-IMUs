package nisargpatel.deadreckoning.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import nisargpatel.deadreckoning.R;
import nisargpatel.deadreckoning.extra.ExtraFunctions;
import nisargpatel.deadreckoning.filewriting.DataFileWriter;
import nisargpatel.deadreckoning.graph.ScatterPlot;
import nisargpatel.deadreckoning.orientation.GyroscopeDeltaOrientation;
import nisargpatel.deadreckoning.orientation.GyroscopeEulerOrientation;
import nisargpatel.deadreckoning.orientation.MagneticFieldOrientation;
import nisargpatel.deadreckoning.stepcounting.DynamicStepCounter;

public class GraphActivity extends Activity implements SensorEventListener, LocationListener {
//

    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private FirebaseDatabase mFirebaseDatabase;
    private int thisDeviceNumber = 0;  //0번은 두개를 한화면에 표시하는 디바이스
    //private int thisDeviceNumber = 1;//1번은 송신만 하는 디바이스.

    //
    private static final long GPS_SECONDS_PER_WEEK = 511200L;

    private static final float GYROSCOPE_INTEGRATION_SENSITIVITY = 0.0025f;

    private static final String FOLDER_NAME = "Dead_Reckoning/Graph_Activity";
    private static final String[] DATA_FILE_NAMES = {
            "Initial_Orientation",
            "Linear_Acceleration",
            "Gyroscope_Uncalibrated",
            "Magnetic_Field_Uncalibrated",
            "Gravity",
            "XY_Data_Set"
    };
    private static final String[] DATA_FILE_HEADINGS = {
            "Initial_Orientation",
            "Linear_Acceleration" + "\n" + "t;Ax;Ay;Az;findStep",
            "Gyroscope_Uncalibrated" + "\n" + "t;uGx;uGy;uGz;xBias;yBias;zBias;heading",
            "Magnetic_Field_Uncalibrated" + "\n" + "t;uMx;uMy;uMz;xBias;yBias;zBias;heading",
            "Gravity" + "\n" + "t;gx;gy;gz",
            "XY_Data_Set" + "\n" + "weekGPS;secGPS;t;strideLength;magHeading;gyroHeading;originalPointX;originalPointY;rotatedPointX;rotatedPointY"
    };


    private DynamicStepCounter dynamicStepCounter;
    private GyroscopeDeltaOrientation gyroscopeDeltaOrientation;
    private GyroscopeEulerOrientation gyroscopeEulerOrientation;
    private DataFileWriter dataFileWriter;
    private ScatterPlot scatterPlot;

    private Button buttonStart;
    private Button buttonStop;
    private Button buttonAddPoint;
    private Button sendbutton;
    private LinearLayout mLinearLayout;

    private SensorManager sensorManager;
    private LocationManager locationManager;

    float[] gyroBias;
    float[] magBias;
    float[] currGravity; //current gravity
    float[] currMag; //current magnetic field

    private boolean isRunning;
    private boolean isCalibrated;
    private boolean usingDefaultCounter;
    private boolean areFilesCreated;
    private float strideLength;
    private float gyroHeading;
    private float magHeading;
    private float weeksGPS;
    private float secondsGPS;

    private long startTime;
    private boolean firstRun;

    private float initialHeading;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        //

        mFirebaseAuth = mFirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        mFirebaseDatabase = FirebaseDatabase.getInstance();

        //


        //defining needed variables
        gyroBias = null;
        magBias = null;
        currGravity = null;
        currMag = null;

        String counterSensitivity;

        isRunning = isCalibrated = usingDefaultCounter = areFilesCreated = false;
        firstRun = true;
        strideLength = 0;
        initialHeading = gyroHeading = magHeading = 0;
        weeksGPS = secondsGPS = 0;
        startTime = 0;

        //getting global settings
        strideLength = getIntent().getFloatExtra("stride_length", 2.5f);
        isCalibrated = getIntent().getBooleanExtra("is_calibrated", false);
        gyroBias = getIntent().getFloatArrayExtra("gyro_bias");
        magBias = getIntent().getFloatArrayExtra("mag_bias");

        //using user_name to get index of user in userList, which is also the index of the user's stride_length
        counterSensitivity = UserListActivity.preferredStepCounterList
                .get(UserListActivity.userList.indexOf(getIntent().getStringExtra("user_name")));

        //usingDefaultCounter is counterSensitivity = "default" and sensor is available
        usingDefaultCounter = counterSensitivity.equals("default") &&
                getIntent().getBooleanExtra("step_detector", false);

        //initializing needed classes
        gyroscopeDeltaOrientation = new GyroscopeDeltaOrientation(GYROSCOPE_INTEGRATION_SENSITIVITY, gyroBias);
        if (usingDefaultCounter) //if using default TYPE_STEP_DETECTOR, don't need DynamicStepCounter
            dynamicStepCounter = null;
        else if (!counterSensitivity.equals("default"))
            dynamicStepCounter = new DynamicStepCounter(Double.parseDouble(counterSensitivity));
        else //if cannot use TYPE_STEP_DETECTOR but sensitivity = "default", use 1.0 sensitivity until user calibrates
            dynamicStepCounter = new DynamicStepCounter(1.0);

        //defining views
        buttonStart = (Button) findViewById(R.id.buttonGraphStart);
        buttonStop = (Button) findViewById(R.id.buttonGraphStop);
        buttonAddPoint = (Button) findViewById(R.id.buttonGraphClear);
        sendbutton = (Button) findViewById(R.id.deviceCheck);
        mLinearLayout = (LinearLayout) findViewById(R.id.linearLayoutGraph);

        //setting up graph with origin
        scatterPlot = new ScatterPlot("Position");
        scatterPlot.addPoint(0, 0);
        mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));

        //message user w/ user_name and stride_length info
        Toast.makeText(GraphActivity.this, "Stride Length: " + strideLength, Toast.LENGTH_SHORT).show();

        //starting GPS location tracking
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, GraphActivity.this);

        //starting sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(GraphActivity.this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_FASTEST);

        if (isCalibrated) {
            sensorManager.registerListener(GraphActivity.this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                    SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(GraphActivity.this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED),
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            sensorManager.registerListener(GraphActivity.this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                    SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(GraphActivity.this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                    SensorManager.SENSOR_DELAY_FASTEST);
        }

        if (usingDefaultCounter) {
            sensorManager.registerListener(GraphActivity.this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            sensorManager.registerListener(GraphActivity.this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                    SensorManager.SENSOR_DELAY_FASTEST);
        }

        //setting up buttons
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                isRunning = true;

                createFiles();

                if (usingDefaultCounter)
                    dataFileWriter.writeToFile("Linear_Acceleration",
                            "TYPE_LINEAR_ACCELERATION will not be recorded, since the TYPE_STEP_DETECTOR is being used instead."
                    );

                float[][] initialOrientation = MagneticFieldOrientation.getOrientationMatrix(currGravity, currMag, magBias);
                initialHeading = MagneticFieldOrientation.getHeading(currGravity, currMag, magBias);

                //saving initial orientation data
                dataFileWriter.writeToFile("Initial_Orientation", "init_Gravity: " + Arrays.toString(currGravity));
                dataFileWriter.writeToFile("Initial_Orientation", "init_Mag: " + Arrays.toString(currMag));
                dataFileWriter.writeToFile("Initial_Orientation", "mag_Bias: " + Arrays.toString(magBias));
                dataFileWriter.writeToFile("Initial_Orientation", "gyro_Bias: " + Arrays.toString(gyroBias));
                dataFileWriter.writeToFile("Initial_Orientation", "init_Orientation: " + Arrays.deepToString(initialOrientation));
                dataFileWriter.writeToFile("Initial_Orientation", "init_Heading: " + initialHeading);

                Log.d("init_heading", "" + initialHeading);

                //TODO: fix rotation matrix
                //gyroscopeEulerOrientation = new GyroscopeEulerOrientation(initialOrientation);

                gyroscopeEulerOrientation = new GyroscopeEulerOrientation(ExtraFunctions.IDENTITY_MATRIX);

                dataFileWriter.writeToFile("XY_Data_Set", "Initial_orientation: " +
                        Arrays.deepToString(initialOrientation));
                dataFileWriter.writeToFile("Gyroscope_Uncalibrated", "Gyroscope_bias: " +
                        Arrays.toString(gyroBias));
                dataFileWriter.writeToFile("Magnetic_Field_Uncalibrated", "Magnetic_field_bias:" +
                        Arrays.toString(magBias));

                buttonStart.setEnabled(false);
                buttonAddPoint.setEnabled(true);
                buttonStop.setEnabled(true);

            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                firstRun = true;
                isRunning = false;

                buttonStart.setEnabled(true);
                buttonAddPoint.setEnabled(true);
                buttonStop.setEnabled(false);
            }
        });

        buttonAddPoint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //complimentary filter
                float compHeading = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);

                Log.d("comp_heading", "" + compHeading);

                //getting and rotating the previous XY points so North 0 on unit circle
                float oPointX = scatterPlot.getLastYPoint();
                float oPointY = -scatterPlot.getLastXPoint();

                //calculating XY points from heading and stride_length
                oPointX += ExtraFunctions.getXFromPolar(strideLength, compHeading);
                oPointY += ExtraFunctions.getYFromPolar(strideLength, compHeading);

                //rotating points by 90 degrees, so north is up
                float rPointX = -oPointY;
                float rPointY = oPointX;

                scatterPlot.addPoint(rPointX, rPointY);

                if (thisDeviceNumber == 1) {
                    XYItem xyItem = new XYItem();
                    xyItem.setX(rPointX);
                    xyItem.setY(rPointY);
                    mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid())
                            .push()
                            .setValue(xyItem)
                            .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                    //Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                }
                            });
                }

                mLinearLayout.removeAllViews();
                mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));

            }
        });

        if (thisDeviceNumber == 0) {
            displayXY();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isRunning) {

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, GraphActivity.this);

            if (isCalibrated) {
                sensorManager.registerListener(GraphActivity.this,
                        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                        SensorManager.SENSOR_DELAY_FASTEST);
                sensorManager.registerListener(GraphActivity.this,
                        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED),
                        SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                sensorManager.registerListener(GraphActivity.this,
                        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                        SensorManager.SENSOR_DELAY_FASTEST);
                sensorManager.registerListener(GraphActivity.this,
                        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                        SensorManager.SENSOR_DELAY_FASTEST);
            }

            if (usingDefaultCounter) {
                sensorManager.registerListener(GraphActivity.this,
                        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
                        SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                sensorManager.registerListener(GraphActivity.this,
                        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                        SensorManager.SENSOR_DELAY_FASTEST);
            }

            buttonStart.setEnabled(false);
            buttonAddPoint.setEnabled(true);
            buttonStop.setEnabled(true);

        } else {

            buttonStart.setEnabled(true);
            buttonAddPoint.setEnabled(true);
            buttonStop.setEnabled(false);

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (firstRun) {
            startTime = event.timestamp;
            firstRun = false;
        }

        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            currGravity = event.values;
            Log.d("gravity_values", Arrays.toString(event.values));
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD ||
                event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            currMag = event.values;
            Log.d("mag_values", Arrays.toString(event.values));
        }

        if (isRunning) {
            if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
                dataValues.add(0, (float) (event.timestamp - startTime));
                dataFileWriter.writeToFile("Gravity", dataValues);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD || event.sensor.getType() ==
                    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {

                magHeading = MagneticFieldOrientation.getHeading(currGravity, currMag, magBias);

                Log.d("mag_heading", "" + magHeading);

                //saving magnetic field data
                ArrayList<Float> dataValues = ExtraFunctions.createList(
                        event.values[0], event.values[1], event.values[2],
                        magBias[0], magBias[1], magBias[2]
                );
                dataValues.add(0, (float) (event.timestamp - startTime));
                dataValues.add(magHeading);
                dataFileWriter.writeToFile("Magnetic_Field_Uncalibrated", dataValues);

            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE ||
                    event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {

                float[] deltaOrientation = gyroscopeDeltaOrientation.calcDeltaOrientation(event.timestamp, event.values);

                gyroHeading = gyroscopeEulerOrientation.getHeading(deltaOrientation);
                gyroHeading += initialHeading;

                Log.d("gyro_heading", "" + gyroHeading);

                //saving gyroscope data
                ArrayList<Float> dataValues = ExtraFunctions.createList(
                        event.values[0], event.values[1], event.values[2],
                        gyroBias[0], gyroBias[1], gyroBias[2]
                );
                dataValues.add(0, (float) (event.timestamp - startTime));
                dataValues.add(gyroHeading);
                dataFileWriter.writeToFile("Gyroscope_Uncalibrated", dataValues);

            } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

                float norm = ExtraFunctions.calcNorm(
                        event.values[0] +
                                event.values[1] +
                                event.values[2]
                );

                //if step is found, findStep == true
                boolean stepFound = dynamicStepCounter.findStep(norm);

                if (stepFound) {

                    //saving linear acceleration data
                    ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
                    dataValues.add(0, (float) (event.timestamp - startTime));
                    dataValues.add(1f);
                    dataFileWriter.writeToFile("Linear_Acceleration", dataValues);

                    //complimentary filter
                    float compHeading = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);

                    //Log.d("comp_heading", "" + compHeading);

                    //getting and rotating the previous XY points so North 0 on unit circle
                    float oPointX = scatterPlot.getLastYPoint();
                    float oPointY = -scatterPlot.getLastXPoint();

                    //calculating XY points from heading and stride_length
                    oPointX += ExtraFunctions.getXFromPolar(strideLength, gyroHeading);
                    oPointY += ExtraFunctions.getYFromPolar(strideLength, gyroHeading);

                    //rotating points by 90 degrees, so north is up
                    float rPointX = -oPointY;
                    float rPointY = oPointX;
                    /*if(rPointX > 300){
                        rPointX = 300;
                    }else if(rPointX < -300){
                        rPointX = -300;
                    }
                    if(rPointY > 300){
                        rPointY = 300;
                    }else if(rPointY < -300){
                        rPointY = -300;
                    }*/
                    scatterPlot.addPoint(rPointX, rPointY);
                    if (thisDeviceNumber == 1) {
                        XYItem xyItem = new XYItem();
                        xyItem.setX(rPointX);
                        xyItem.setY(rPointY);
                        mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid())
                                .push()
                                .setValue(xyItem)
                                .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                        //Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    //saving XY location data
                    dataFileWriter.writeToFile("XY_Data_Set",
                            weeksGPS,
                            secondsGPS,
                            (event.timestamp - startTime),
                            strideLength,
                            magHeading,
                            gyroHeading,
                            oPointX,
                            oPointY,
                            rPointX,
                            rPointY);

                    mLinearLayout.removeAllViews();
                    mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));

                    //if step is not found
                } else {
                    //saving linear acceleration data
                    ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
                    dataValues.add(0, (float) event.timestamp);
                    dataValues.add(0f);
                    dataFileWriter.writeToFile("Linear_Acceleration", dataValues);
                }

            } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {

                boolean stepFound = (event.values[0] == 1);

                if (stepFound) {

                    //complimentary filter
                    float compHeading = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);

                    //Log.d("comp_heading", "" + compHeading);

                    //getting and rotating the previous XY points so North 0 on unit circle
                    float oPointX = scatterPlot.getLastYPoint();
                    float oPointY = -scatterPlot.getLastXPoint();

                    //calculating XY points from heading and stride_length
                    oPointX += ExtraFunctions.getXFromPolar(strideLength, gyroHeading);//x좌표 이동값 계산
                    oPointY += ExtraFunctions.getYFromPolar(strideLength, gyroHeading);//y좌표 이동값 계산

                    //rotating points by 90 degrees, so north is up
                    float rPointX = -oPointY;
                    float rPointY = oPointX;

                    /*if(rPointX > 300){
                        rPointX = 300;
                    }else if(rPointX < -300){
                        rPointX = -300;
                    }
                    if(rPointY > 300){
                        rPointY = 300;
                    }else if(rPointY < -300){
                        rPointY = -300;
                    }*/
                    scatterPlot.addPoint(rPointX, rPointY);//점찍는거.
                    if (thisDeviceNumber == 1) {
                        XYItem xyItem = new XYItem();
                        xyItem.setX(rPointX);
                        xyItem.setY(rPointY);
                        mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid())
                                .push()
                                .setValue(xyItem)
                                .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                        //Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }


                    //saving XY location data
                    dataFileWriter.writeToFile("XY_Data_Set",
                            weeksGPS,
                            secondsGPS,
                            (event.timestamp - startTime),
                            strideLength,
                            magHeading,
                            gyroHeading,
                            oPointX,
                            oPointY,
                            rPointX,
                            rPointY);

                    mLinearLayout.removeAllViews();
                    mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext())); //그리는거
                }

            }
        }

    }

    @Override
    public void onLocationChanged(Location location) {
        long GPSTimeSec = location.getTime() / 1000;
        weeksGPS = GPSTimeSec / GPS_SECONDS_PER_WEEK;
        secondsGPS = GPSTimeSec % GPS_SECONDS_PER_WEEK;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private void createFiles() {
        if (!areFilesCreated) {
            try {
                dataFileWriter = new DataFileWriter(FOLDER_NAME, DATA_FILE_NAMES, DATA_FILE_HEADINGS);
            } catch (IOException e) {
                e.printStackTrace();
            }
            areFilesCreated = true;
        }
    }

    public void displayXY() {
        mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid())
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {//데이터 추가된 경우
                        XYItem xyitem = dataSnapshot.getValue(XYItem.class);
                        scatterPlot.add_ReceivedPoint(xyitem.getX(), xyitem.getY());
                        mLinearLayout.removeAllViews();
                        mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));
                        /*Memo memo = dataSnapshot.getValue(Memo.class);
                        memo.setKey(dataSnapshot.getKey());
                        mMemoAdapter.add(memo);
                        mRecyclerView.scrollToPosition(mMemoAdapter.getItemCount()-1);*/
                    }

                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {//데이터 수정된 경우
                        XYItem xyitem = dataSnapshot.getValue(XYItem.class);
                        scatterPlot.add_ReceivedPoint(xyitem.getX(), xyitem.getY());
                        mLinearLayout.removeAllViews();
                        mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));
                        /*Memo memo = dataSnapshot.getValue(Memo.class);
                        memo.setKey(dataSnapshot.getKey());
                        mMemoAdapter.update(memo);*/
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {//데이터 삭제된 경우
                        scatterPlot.clearSet();
                        scatterPlot.addPoint(0, 0);
                        mLinearLayout.removeAllViews();
                        mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));
                    }

                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }

    public void removeAllValue(View v) {
        mFirebaseDatabase.getReference("dead/").removeValue();
    }
    public void sendDevice(View v){
        thisDeviceNumber = 1;
        sendbutton.setText("설정완료");
    }
}

