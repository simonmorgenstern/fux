import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

public class DiamondPulseEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;
    private int fps;
    private Set<Integer> diamondLEDs;
    private Color baseColor;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        // Base color (can be customized via params)
        if (params.has("color")) {
            JsonArray colorArray = params.getAsJsonArray("color");
            this.baseColor = new Color(
                colorArray.get(0).getAsInt(),
                colorArray.get(1).getAsInt(),
                colorArray.get(2).getAsInt()
            );
        } else {
            // Default: cyan/turquoise
            this.baseColor = new Color(0, 200, 255);
        }
        
        // Load LED groups
        this.diamondLEDs = loadLEDGroup("diamond");
        
        System.out.println("DiamondPulseEffect initialized:");
        System.out.println("  Speed: " + speed);
        System.out.println("  Diamond LEDs: " + diamondLEDs.size());
        System.out.println("  Base color: RGB(" + baseColor.getRed() + ", " + 
                          baseColor.getGreen() + ", " + baseColor.getBlue() + ")");
    }
    
    private Set<Integer> loadLEDGroup(String groupName) {
        // Use centralized loader
        return LEDGroupLoader.loadGroup(groupName);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Calculate breathing intensity using sine wave (0 to 1)
        // Full breathing cycle = 2 seconds at speed=1
        double cycleTime = 2.0 / speed;
        double phase = (timeSeconds % cycleTime) / cycleTime;  // 0 to 1
        
        // Use sine wave for smooth breathing (ease in/out)
        // Maps 0-1 to 0-1-0 smoothly
        double intensity = (Math.sin(phase * 2 * Math.PI - Math.PI / 2) + 1.0) / 2.0;
        
        // Apply minimum brightness to avoid complete darkness
        double minBrightness = 0.1;
        intensity = minBrightness + intensity * (1.0 - minBrightness);
        
        // Apply intensity to base color
        Color pulseColor = new Color(
            (int)(baseColor.getRed() * intensity),
            (int)(baseColor.getGreen() * intensity),
            (int)(baseColor.getBlue() * intensity)
        );
        
        // Set all diamond LEDs to pulse color
        for (int led : diamondLEDs) {
            pixels.put(led, pulseColor);
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Diamond Pulse";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
