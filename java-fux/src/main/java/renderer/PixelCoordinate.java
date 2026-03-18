package renderer;
public class PixelCoordinate {
    private int index;
    private double x;
    private double y;
    private double distanceFromCenter;
    
    public PixelCoordinate(int index, double x, double y) {
        this.index = index;
        this.x = x;
        this.y = y;
    }
    
    public int getIndex() {
        return index;
    }
    
    public double getX() {
        return x;
    }
    
    public double getY() {
        return y;
    }
    
    public double getDistanceFromCenter() {
        return distanceFromCenter;
    }
    
    public void setDistanceFromCenter(double distance) {
        this.distanceFromCenter = distance;
    }
}
