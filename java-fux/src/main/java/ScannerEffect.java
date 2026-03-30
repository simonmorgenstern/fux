import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Scanner effect - Knight Rider style back and forth with fading trail
 */
public class ScannerEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;
    private int eyeSize;
    private Color scanColor;
    private int fps;
    
    // State
    private double position;
    private boolean movingForward;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 30.0;
        this.eyeSize = params.has("eye_size") ? params.get("eye_size").getAsInt() : 8;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 60;
        
        // Parse color
        if (params.has("color")) {
            JsonArray colorArray = params.getAsJsonArray("color");
            this.scanColor = new Color(
                colorArray.get(0).getAsInt(),
                colorArray.get(1).getAsInt(),
                colorArray.get(2).getAsInt()
            );
        } else {
            this.scanColor = new Color(255, 0, 0);  // Default red
        }
        
        // Initialize state
        this.position = 0.0;
        this.movingForward = true;
        
        System.out.println("ScannerEffect initialized:");
        System.out.println("  Speed: " + speed + " LEDs/sec");
        System.out.println("  Eye size: " + eyeSize);
        System.out.println("  Color: RGB(" + scanColor.getRed() + "," + 
                          scanColor.getGreen() + "," + scanColor.getBlue() + ")");
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update position
        double movement = speed / fps;
        
        if (movingForward) {
            position += movement;
            if (position >= coords.getCount() - 1) {
                position = coords.getCount() - 1;
                movingForward = false;
            }
        } else {
            position -= movement;
            if (position <= 0) {
                position = 0;
                movingForward = true;
            }
        }
        
        int centerPos = (int)position;
        
        // Render scanner eye with fade
        for (int i = 0; i < eyeSize; i++) {
            int offset = i - eyeSize / 2;
            int ledIndex = centerPos + offset;
            
            if (ledIndex >= 0 && ledIndex < coords.getCount()) {
                // Calculate intensity (brightest in center)
                double distance = Math.abs(offset);
                double intensity = 1.0 - (distance / (eyeSize / 2.0));
                intensity = Math.max(0, Math.min(1, intensity));
                intensity = Math.pow(intensity, 2.0);  // Non-linear fade
                
                Color fadedColor = new Color(
                    (int)(scanColor.getRed() * intensity),
                    (int)(scanColor.getGreen() * intensity),
                    (int)(scanColor.getBlue() * intensity)
                );
                
                if (intensity > 0.1) {
                    pixels.put(ledIndex, fadedColor);
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
        return "Scanner";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
