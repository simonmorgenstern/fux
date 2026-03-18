package renderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class AuroraEffect implements Effect {
    private PixelCoordinates coords;
    private double waveSpeed;
    private double waveHeight;
    private int numWaves;
    private double brightness;
    private int fps;
    private double minY;
    private double maxY;
    private double minX;
    private double maxX;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.waveSpeed = params.get("wave_speed").getAsDouble();
        this.waveHeight = params.get("wave_height").getAsDouble();
        this.numWaves = params.get("num_waves").getAsInt();
        this.brightness = params.has("brightness") ? params.get("brightness").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        // Find coordinate bounds
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;
        this.minX = Double.MAX_VALUE;
        this.maxX = Double.MIN_VALUE;
        
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate coord = coords.get(i);
            minY = Math.min(minY, coord.getY());
            maxY = Math.max(maxY, coord.getY());
            minX = Math.min(minX, coord.getX());
            maxX = Math.max(maxX, coord.getX());
        }
        
        System.out.println("AuroraEffect initialized:");
        System.out.println("  Wave speed: " + waveSpeed);
        System.out.println("  Wave height: " + waveHeight);
        System.out.println("  Number of waves: " + numWaves);
        System.out.println("  Y range: " + minY + " - " + maxY);
        System.out.println("  X range: " + minX + " - " + maxX);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        double time = frameNumber / (double)fps;
        
        for (int ledIndex = 0; ledIndex < coords.getCount(); ledIndex++) {
            PixelCoordinate led = coords.get(ledIndex);
            
            double totalIntensity = 0;
            double totalR = 0, totalG = 0, totalB = 0;
            
            // Calculate contribution from each wave
            for (int w = 0; w < numWaves; w++) {
                // Each wave has different frequency and phase
                double frequency = 0.002 + (w * 0.001);
                double phase = w * 2.0;
                double speed = waveSpeed * (1.0 + w * 0.3);
                
                // Calculate wave Y position based on X coordinate
                double waveY = Math.sin(led.getX() * frequency + time * speed + phase) * waveHeight;
                
                // Normalize waveY to be in range [0, maxY-minY]
                double yRange = maxY - minY;
                double centerY = minY + yRange / 2.0;
                double ledRelativeY = led.getY() - centerY;
                
                // Distance from LED to wave
                double distanceToWave = Math.abs(ledRelativeY - waveY);
                
                // If LED is close to this wave (wider reach)
                if (distanceToWave < 100) {
                    double intensity = 1.0 - (distanceToWave / 100.0);
                    intensity = Math.pow(intensity, 1.5); // Gentler falloff for wider coverage
                    
                    // Aurora colors: wave 0=green, 1=purple, 2=blue
                    Color waveColor;
                    if (w % 3 == 0) {
                        // Green aurora (dominant)
                        waveColor = new Color(0, 255, 100);
                    } else if (w % 3 == 1) {
                        // Purple aurora
                        waveColor = new Color(200, 0, 255);
                    } else {
                        // Blue aurora
                        waveColor = new Color(0, 150, 255);
                    }
                    
                    totalR += waveColor.getRed() * intensity;
                    totalG += waveColor.getGreen() * intensity;
                    totalB += waveColor.getBlue() * intensity;
                    totalIntensity += intensity;
                }
            }
            
            // Normalize and apply brightness
            if (totalIntensity > 0) {
                int r = (int)Math.min(255, totalR * brightness);
                int g = (int)Math.min(255, totalG * brightness);
                int b = (int)Math.min(255, totalB * brightness);
                
                if (r > 10 || g > 10 || b > 10) {
                    pixels.put(ledIndex, new Color(r, g, b));
                }
            }
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Aurora Borealis";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
