package co.earthme.wafkbot.math;

public class Vec3D {
    private double x;
    private double y;
    private double z;
    private double yaw;
    private double pitch;

    public Vec3D(double x, double y, double z,double yaw,double pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public synchronized double getPitch() {
        return this.pitch;
    }

    public synchronized double getYaw() {
        return this.yaw;
    }

    public synchronized void setPitch(double pitch) {
        this.pitch = pitch;
    }

    public synchronized void setYaw(double yaw) {
        this.yaw = yaw;
    }

    public synchronized double getX() {
        return this.x;
    }

    public synchronized double getY() {
        return this.y;
    }

    public synchronized double getZ() {
        return this.z;
    }

    public synchronized void setX(double x){
        this.x = x;
    }

    public synchronized void setY(double y){
        this.y = y;
    }

    public synchronized void setZ(double z){
        this.z = z;
    }

    public synchronized void setRelatively(double delta,Relative target){
        switch (target){
            case X -> this.x+=delta;
            case Y -> this.y+=delta;
            case Z -> this.z+=delta;
            case YAW -> this.yaw+=delta;
            case PITCH -> this.pitch+=delta;
        }
    }

    public synchronized Vec3D copy(){
        return new Vec3D(this.x,this.y,this.z,this.yaw,this.pitch);
    }

    public enum Relative{
        X,Y,Z,YAW,PITCH
    }
}