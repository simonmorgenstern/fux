package renderer;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class BreathingEffect implements Effect {
    private PixelCoordinates coords;
    private int[] colorRGB;
    private double breathRate; // Breaths per minute
    private double minBrightness;
    private double maxBrightness;
    private int fps;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.breathRate = params.get("breath_rate").getAsDouble();
        this.minBrightness = params.get("min_brightness").getAsDouble();
        this.maxBrightness = params.get("max_brightness").getAsDouble();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        
        // Parse color (RGB array)
        com.google.gson.JsonArray colorArray = params.getAsJsonArray("color");
        this.colorRGB = new int[3];
        for (int i = 0; i < 3; i++) {
            this.colorRGB[i] = colorArray.get(i).getAsInt();
        }
        
        System.out.println("BreathingEffect initialized:");
        System.out.println("  Color: RGB(" + colorRGB[0] + "," + colorRGB[1] + "," + colorRGB[2] + ")");
        System.out.println("  Breath rate: " + breathRate + " breaths/min");
        System.out.println("  Brightness range: " + minBrightness + " - " + maxBrightness);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Calculate breathing cycle (smooth sine wave)
        // breath_rate breaths per minute = breath_rate/60 breaths per second
        double breathsPerSecond = breathRate / 60.0;
        double phase = timeSeconds * breathsPerSecond * 2.0 * Math.PI;
        
        // Use sine wave for smooth in/out breathing
        double brightness = minBrightness + 
            (maxBrightness - minBrightness) * (0.5 + 0.5 * Math.sin(phase));
        
        // Apply to all LEDs
        int r = (int)(colorRGB[0] * brightness);
        int g = (int)(colorRGB[1] * brightness);
        int b = (int)(colorRGB[2] * brightness);
        
        Color color = new Color(r, g, b);
        
        for (int i = 0; i < coords.getCount(); i++) {
            pixels.put(i, color);
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Breathing";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
