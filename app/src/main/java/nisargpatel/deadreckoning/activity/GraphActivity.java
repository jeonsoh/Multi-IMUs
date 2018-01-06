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
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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
//FireBase 에 저장하기 위한 경로와 가틍ㄴ 부분은 본인에게 맞게 바꾸어 줄 필요가 있을 수도 있음. PDR알고리즘 구현을 위한 수식은 다른 사람의 코드를 인용한 코드이므로 어느정도 해석과 이해가 필요하다.

    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private FirebaseDatabase mFirebaseDatabase;
    private int thisDeviceNumber = 0;  //0번은 수신용 디바이스
    //private int thisDeviceNumber = 1;//1번은 송신용 디바이스.

    //tt
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
    private TextView statusText;
    private Button sendInitialBtn;
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

    private String gravity_values = "";
    private String mag_heading = "";
    private String mag_values = "";
    private String gyro_heading = "";
    private String gyro_values = "";
    private String Linear_values = "";
    private String comp_heading = "";
    private float average_comp;
    private int stepCount = 0;
    float compHeading = 0;
    boolean setting = false;
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
        sendInitialBtn = (Button) findViewById(R.id.sendInitialValue_Btn);
        buttonStart = (Button) findViewById(R.id.buttonGraphStart);
        buttonStop = (Button) findViewById(R.id.buttonGraphStop);
        buttonAddPoint = (Button) findViewById(R.id.buttonGraphClear);
        statusText = (TextView) findViewById(R.id.status);
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
        buttonStart.setOnClickListener(new View.OnClickListener() {// START버튼 클릭시 동작하는 메소드들
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

        buttonStop.setOnClickListener(new View.OnClickListener() {//STOP버튼 누를시 동작하는 메소드
            @Override
            public void onClick(View v) {

                firstRun = true;
                isRunning = false;

                buttonStart.setEnabled(true);
                buttonAddPoint.setEnabled(true);
                buttonStop.setEnabled(false);
            }
        });

        buttonAddPoint.setOnClickListener(new View.OnClickListener() {// UI상에 존재 하지 않음. 수동으로 좌표 찍음. 본 개발자가 필요없다고 판단하여 삭제
            @Override
            public void onClick(View v) {

                //complimentary filter
                float compHeading = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);

                Log.d("comp_heading", "" + compHeading);
                comp_heading = compHeading + "";

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
                    mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid() + "/data")
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

        sendInitialBtn.setOnClickListener(new View.OnClickListener() {// START버튼 클릭 후 다른기기에 대해 초기센서값 전송을 위한 버튼. 두 디바이스의 초기 방향값이 일치되어있지 않기 때문에 일치시키기 위한 작업.
                                                                        //이 때 두 디바이스 중에 하나는 송신 디바이스, 하나는 수신 디바이스로 설정되어 있어야함. (CHANGESETTING 버튼을 통해 번경)
                                                                        // 송신디바이스가 초기값 보내기버튼을 통해 초기값 전송. 수신 디바이스의 초기값 받기 버튼을 통해 초기값 갱신.
                                                                        //해당 버튼의 이름은 1번 디바이스인 송신 디바이스의 경우엔 "초기값보내기" , 0번 디바이스인 수신 디바이스의 경우엔 "초기값 받기" 이다.
            @Override
            public void onClick(View v) {
                setting = true;
                if (thisDeviceNumber == 1) {//송신 디바이스는 해당 메소드 실행
                    final float compHeadingtemp = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);
                    mFirebaseDatabase.getReference("dead/") // 송신 디바이스가 파이어베이스에 초기값 전송하는 부분,
                                                            // compHeading은 현재 계산된 방향 값을 나타내고 intialHeading은 제일 초기에 자기장센서로만 얻어진 방향이다.
                            .child("initialcomp")
                            .setValue(compHeadingtemp + "")
                            .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    compHeading = compHeadingtemp;
                                    //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                    Toast.makeText(GraphActivity.this, "보내기완료", Toast.LENGTH_SHORT).show();
                                }
                            });
                    mFirebaseDatabase.getReference("dead/")// 송신 디바이스가 파이어베이스에 초기값 전송하는 부분
                            .child("initialvalue")
                            .setValue(initialHeading + "")
                            .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                    Toast.makeText(GraphActivity.this, "보내기완료", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {//수신 디바이스는 해당 메소드 실행
                    mFirebaseDatabase.getReference("dead/")//수신 디바이스는 초기값이 전송되면 다음과 같이 초기값이 갱신되어짐.
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    compHeading = Float.parseFloat((String) dataSnapshot.child("initialcomp").getValue());
                                    Toast.makeText(GraphActivity.this, "받기완료" + compHeading, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                    mFirebaseDatabase.getReference("dead/")//수신 디바이스는 초기값이 전송되면 다음과 같이 초기값이 갱신되어짐.
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    initialHeading = Float.parseFloat((String) dataSnapshot.child("initialvalue").getValue());
                                    Toast.makeText(GraphActivity.this, "받기완료" + compHeading, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                }
            }
        });

        if (thisDeviceNumber == 0) {// 수신 디바이스인 0번 디바이스는 두 개의 디바이스의 좌표값을 출력함. 실시간으로 좌표값 업데이트를 위해 파이어베이스의 데이터를 불러오는 메소드 호출.
            displayXY();
            //displayAverage();
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
            // Log.d("gravity_values", Arrays.toString(event.values));
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD ||
                event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            currMag = event.values;
            //Log.d("mag_values", Arrays.toString(event.values));
        }

        if (isRunning) {
            if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
                dataValues.add(0, (float) (event.timestamp - startTime));
                dataFileWriter.writeToFile("Gravity", dataValues);
                Log.e("gravity_values", Arrays.toString(event.values));
                gravity_values = Arrays.toString(event.values);

            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD || event.sensor.getType() ==
                    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {

                magHeading = MagneticFieldOrientation.getHeading(currGravity, currMag, magBias);

                Log.d("mag_heading", "" + magHeading);
                mag_heading = magHeading + "";

                //saving magnetic field data
                ArrayList<Float> dataValues = ExtraFunctions.createList(
                        event.values[0], event.values[1], event.values[2],
                        magBias[0], magBias[1], magBias[2]
                );
                dataValues.add(0, (float) (event.timestamp - startTime));
                dataValues.add(magHeading);
                dataFileWriter.writeToFile("Magnetic_Field_Uncalibrated", dataValues);

                Log.e("mag_values", Arrays.toString(event.values));
                mag_values = Arrays.toString(event.values);

            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE ||
                    event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {

                float[] deltaOrientation = gyroscopeDeltaOrientation.calcDeltaOrientation(event.timestamp, event.values);

                gyroHeading = gyroscopeEulerOrientation.getHeading(deltaOrientation);
                gyroHeading += initialHeading;

                Log.d("gyro_heading", "" + gyroHeading);
                gyro_heading = gyroHeading + "";

                //saving gyroscope data
                ArrayList<Float> dataValues = ExtraFunctions.createList(
                        event.values[0], event.values[1], event.values[2],
                        gyroBias[0], gyroBias[1], gyroBias[2]
                );
                dataValues.add(0, (float) (event.timestamp - startTime));
                dataValues.add(gyroHeading);
                dataFileWriter.writeToFile("Gyroscope_Uncalibrated", dataValues);

                Log.e("gyro_values", Arrays.toString(event.values));
                gyro_values = Arrays.toString(event.values);

            } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {//걸음이 검출되었다면

                float norm = ExtraFunctions.calcNorm(
                        event.values[0] +
                                event.values[1] +
                                event.values[2]
                );

                //if step is found, findStep == true
                boolean stepFound = dynamicStepCounter.findStep(norm);

                if (stepFound) {
                    if (stepCount == 0 && setting == true) {
                        stepCount++;
                    } else {
                        compHeading = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);// 새로운 방향값을 계산.
                        stepCount++;

                        //saving linear acceleration data
                        ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
                        dataValues.add(0, (float) (event.timestamp - startTime));
                        dataValues.add(1f);
                        dataFileWriter.writeToFile("Linear_Acceleration", dataValues);

                        Log.e("Linear_values", Arrays.toString(event.values));
                        Linear_values = Arrays.toString(event.values);

                        //complimentary filter


                        Log.d("comp_heading", "" + compHeading);
                        comp_heading = compHeading + "";

                        mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid()) // 해당 방향값을 파이어베이스에 지속적으로 쌓아놓음. 추후 분석을 위한 행위.
                                .child("comp")
                                .setValue(comp_heading)
                                .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                        // Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                    }
                                });


                        //getting and rotating the previous XY points so North 0 on unit circle
                        float oPointX = scatterPlot.getLastYPoint();
                        float oPointY = -scatterPlot.getLastXPoint();

                        //calculating XY points from heading and stride_length
                        oPointX += ExtraFunctions.getXFromPolar(strideLength, compHeading);
                        oPointY += ExtraFunctions.getYFromPolar(strideLength, compHeading);

                        //rotating points by 90 degrees, so north is up
                        float rPointX = -oPointY; // 상대적인 x 위치 값 계산.
                        float rPointY = oPointX;// 상대적인 y 위치 값 계산.
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
                        scatterPlot.addPoint(rPointX, rPointY); // 화면에 표시를 위해 메소드를 통해 구한 좌표값을 추가. (걸음이 검출될 때마다 실시간으로 찍히게 됨)

                        Log.e("rX, rY---------------", rPointX + "," + rPointY);

          /*          float AVR_X = scatterPlot.getLastYPoint();
                    float AVR_Y = -scatterPlot.getLastXPoint();

                    //calculating XY points from heading and stride_length
                    AVR_X += ExtraFunctions.getXFromPolar(strideLength, average_comp);
                    AVR_Y += ExtraFunctions.getYFromPolar(strideLength, average_comp);

                    //rotating points by 90 degrees, so north is up
                    float AVR_PointX = -AVR_X;
                    float AVR_PointY = AVR_Y;
                    Log.e("average_XY----", AVR_PointX+","+AVR_PointY);

                    scatterPlot.add_ReceivedPoint(AVR_PointX, AVR_PointY);
*/
                        if (thisDeviceNumber == 0) {// 0 번 디아비스의 좌표값 및 센서값들을 파이어베이스에 전송하여 데이터 쌓음, 추후 분석을 위한 데이터 축적 행동.
                            XYItem xyItem = new XYItem();
                            xyItem.setX(rPointX);
                            xyItem.setY(rPointY);
                            xyItem.setGravity_values(gravity_values);
                            xyItem.setGyro_heading(gyro_heading);
                            xyItem.setGyro_values(gyro_values);
                            xyItem.setLinear_values(Linear_values);
                            xyItem.setMag_heading(mag_heading);
                            xyItem.setMag_values(mag_values);
                            xyItem.setComp_heading(comp_heading);
                            mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid() + "/data1")
                                    .push()
                                    .setValue(xyItem)
                                    .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                            // Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                        if (thisDeviceNumber == 1) {// 1 번 디아비스의 좌표값 및 센서값들을 파이어베이스에 전송하여 데이터 쌓음, 추후 분석을 위한 데이터 축적 행동.
                            XYItem xyItem = new XYItem();
                            xyItem.setX(rPointX);
                            xyItem.setY(rPointY);
                            xyItem.setGravity_values(gravity_values);
                            xyItem.setGyro_heading(gyro_heading);
                            xyItem.setGyro_values(gyro_values);
                            xyItem.setLinear_values(Linear_values);
                            xyItem.setMag_heading(mag_heading);
                            xyItem.setMag_values(mag_values);
                            xyItem.setComp_heading(comp_heading);
                            mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid() + "/data2")
                                    .push()
                                    .setValue(xyItem)
                                    .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                            // Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
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


                    }
                } else {
                    //saving linear acceleration data
                    ArrayList<Float> dataValues = ExtraFunctions.arrayToList(event.values);
                    dataValues.add(0, (float) event.timestamp);
                    dataValues.add(0f);
                    dataFileWriter.writeToFile("Linear_Acceleration", dataValues);

                    Log.e("Linear_values", Arrays.toString(event.values));
                    Linear_values = Arrays.toString(event.values);

                }

            } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {// 걸음이 검출되었다면, 안드로이드에는 여러가지 센서 메소드가 존재. 걸음 검출에도 여러가지 방법이 존재.

                boolean stepFound = (event.values[0] == 1);
                Log.e("Linear_values", Arrays.toString(event.values));
                Linear_values = Arrays.toString(event.values);

                if (stepFound) {
                    if (stepCount == 0) {
                        stepCount++;
                    } else {
                        compHeading = ExtraFunctions.calcCompHeading(magHeading, gyroHeading);
                        stepCount++;
                        //complimentary filter

                        Log.d("comp_heading", "" + compHeading);
                        comp_heading = compHeading + "";

                        mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid())
                                .child("comp")
                                .setValue(comp_heading)
                                .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                        // Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                    }
                                });


                        //getting and rotating the previous XY points so North 0 on unit circle
                        float oPointX = scatterPlot.getLastYPoint();
                        float oPointY = -scatterPlot.getLastXPoint();

                        //calculating XY points from heading and stride_length
                        oPointX += ExtraFunctions.getXFromPolar(strideLength, compHeading);//x좌표 이동값 계산
                        oPointY += ExtraFunctions.getYFromPolar(strideLength, compHeading);//y좌표 이동값 계산

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
                        Log.e("rX, rY---------------", rPointX + "," + rPointY);


         /*           float AVR_X = scatterPlot.getLastYPoint();
                    float AVR_Y = -scatterPlot.getLastXPoint();

                    //calculating XY points from heading and stride_length
                    AVR_X += ExtraFunctions.getXFromPolar(strideLength, average_comp);
                    AVR_Y += ExtraFunctions.getYFromPolar(strideLength, average_comp);

                    //rotating points by 90 degrees, so north is up
                    float AVR_PointX = -AVR_X;
                    float AVR_PointY = AVR_Y;
                    Log.e("average_XY----", AVR_PointX+","+AVR_PointY);

                    scatterPlot.add_ReceivedPoint(AVR_PointX, AVR_PointY);
*/
                        if (thisDeviceNumber == 0) {// 0 번 디아비스의 좌표값 및 센서값들을 파이어베이스에 전송하여 데이터 쌓음, 추후 분석을 위한 데이터 축적 행동.
                            XYItem xyItem = new XYItem();
                            xyItem.setX(rPointX);
                            xyItem.setY(rPointY);
                            xyItem.setGravity_values(gravity_values);
                            xyItem.setGyro_heading(gyro_heading);
                            xyItem.setGyro_values(gyro_values);
                            xyItem.setLinear_values(Linear_values);
                            xyItem.setMag_heading(mag_heading);
                            xyItem.setMag_values(mag_values);
                            xyItem.setComp_heading(comp_heading);
                            mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid() + "/data1")
                                    .push()
                                    .setValue(xyItem)
                                    .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                            // Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                        if (thisDeviceNumber == 1) {// 1 번 디아비스의 좌표값 및 센서값들을 파이어베이스에 전송하여 데이터 쌓음, 추후 분석을 위한 데이터 축적 행동.
                            XYItem xyItem = new XYItem();
                            xyItem.setX(rPointX);
                            xyItem.setY(rPointY);
                            xyItem.setGravity_values(gravity_values);
                            xyItem.setGyro_heading(gyro_heading);
                            xyItem.setGyro_values(gyro_values);
                            xyItem.setLinear_values(Linear_values);
                            xyItem.setMag_heading(mag_heading);
                            xyItem.setMag_values(mag_values);
                            xyItem.setComp_heading(comp_heading);

                            mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid() + "/data2")
                                    .push()
                                    .setValue(xyItem)
                                    .addOnSuccessListener(GraphActivity.this, new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            //Snackbar.make(memoEditText, " 메모 저장됨", Snackbar.LENGTH_SHORT).show();
                                            //  Toast.makeText(GraphActivity.this,"추가됨",Toast.LENGTH_SHORT).show();
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

    public void displayXY() {// 수신디바이스의 경우에만 호출되는 메소드이다. 송신 디바이스가 보낸 x,y 좌표를 화면에 출력해줌,
                            // 또한 ScatterPlot 클래스의 조작을 통해 두 다바이스의 평균값을 계산하여 동시 출력
        mFirebaseDatabase.getReference("dead/" + mFirebaseUser.getUid() + "/data2")// data2 경로에 저장되어진 값은 송신 디바이스가 축적해놓은 데이터에 해당됨. 따라서 그 경로에서 x,y좌표값만 따오는 작업을 위한 행동.
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {//데이터 추가된 경우
                        XYItem xyitem = dataSnapshot.getValue(XYItem.class);
                        scatterPlot.add_ReceivedPoint(xyitem.getX(), xyitem.getY());// Scatter에 송신 디바이스로부터 받은 x,y좌표 추가
                        mLinearLayout.removeAllViews();// GraphActivity상단에 표시되는 화면을 비워두고, 새로 갱신된 Scatter로 바꾸어줌.
                        mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));// scatterPlot의 getGraphView 함수 조작을 통해 송신디바이스의 x,y값 그리고 두 디바이스의 평균값까지 출력.
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

    public void displayAverage() {

        mFirebaseDatabase.getReference("dead")

                .addValueEventListener(new ValueEventListener() {
                                           //ArrayList comp_value;
                                           float sum = 0;

                                           @Override
                                           public void onDataChange(DataSnapshot dataSnapshot) {
                                               Log.e("Count ", "" + dataSnapshot.getChildrenCount());
                                               //comp_value = new float[(int)dataSnapshot.getChildrenCount()];
                                               for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                                                   String post = (String) postSnapshot.child("comp").getValue();
                                                   Log.e("Get Data----", post);
                                                   //comp_value.add(post);
                                                   sum += Float.parseFloat(post);
                                               }

                                               average_comp = sum / dataSnapshot.getChildrenCount();
                                               Log.e("average_comp----", average_comp + "");

                                               //getting and rotating the previous XY points so North 0 on unit circle
                   /*     float AVR_X = scatterPlot.getLastYPoint();
                        float AVR_Y = -scatterPlot.getLastXPoint();

                        //calculating XY points from heading and stride_length
                        AVR_X += ExtraFunctions.getXFromPolar(strideLength, average_comp);
                        AVR_Y += ExtraFunctions.getYFromPolar(strideLength, average_comp);

                        //rotating points by 90 degrees, so north is up
                        float AVR_PointX = -AVR_X;
                        float AVR_PointY = AVR_Y;
                        Log.e("average_XY----", AVR_PointX+","+AVR_PointY);

                        scatterPlot.add_ReceivedPoint(AVR_PointX, AVR_PointY);*/
                                               //   mLinearLayout.removeAllViews();
                                               //    mLinearLayout.addView(scatterPlot.getGraphView(getApplicationContext()));

                                           }

                                           @Override
                                           public void onCancelled(DatabaseError databaseError) {
                                               Log.e("The read failed: ", databaseError.getMessage());
                                           }


                                       }
                );

    }

    public void removeAllValue(View v) {//모든 데이터를 지우는 작업.
        mFirebaseDatabase.getReference("dead/"+mFirebaseUser.getUid()).removeValue();
    }

    public void sendDevice(View v) {//CHANGESETTING버튼을 눌렀을 때, 송신디바이스 or 수신디바이스를 설정.
        if(thisDeviceNumber == 0) {
            thisDeviceNumber = 1;
            statusText.setText("SendMD");
            sendInitialBtn.setText("초기값 보내기");
        }else{
            thisDeviceNumber = 0;
            statusText.setText("ReceiveMD");
            sendInitialBtn.setText("초기값 받기");
        }
    }
}

