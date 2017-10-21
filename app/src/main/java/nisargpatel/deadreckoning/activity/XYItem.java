package nisargpatel.deadreckoning.activity;

/**
 * Created by Taek on 2017. 10. 14..
 */

public class XYItem {
    double x;
    double y;
    String gravity_values="";
    String mag_heading="";
    String mag_values="";
    String gyro_heading="";
    String gyro_values="";
    String Linear_values="";

    public String getGravity_values() {
        return gravity_values;
    }

    public void setGravity_values(String gravity_values) {
        this.gravity_values = gravity_values;
    }

    public String getMag_heading() {
        return mag_heading;
    }

    public void setMag_heading(String mag_heading) {
        this.mag_heading = mag_heading;
    }

    public String getMag_values() {
        return mag_values;
    }

    public void setMag_values(String mag_values) {
        this.mag_values = mag_values;
    }

    public String getGyro_heading() {
        return gyro_heading;
    }

    public void setGyro_heading(String gyro_heading) {
        this.gyro_heading = gyro_heading;
    }

    public String getGyro_values() {
        return gyro_values;
    }

    public void setGyro_values(String gyro_values) {
        this.gyro_values = gyro_values;
    }

    public String getLinear_values() {
        return Linear_values;
    }

    public void setLinear_values(String linear_values) {
        Linear_values = linear_values;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }
}
