package renderer;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FireEffect implements Effect {
    private PixelCoordinates coords;
    private double intensity;
    private double cooling;
    private double sparking;
    private int fps;
    private Random random;
    
    // Heat map for each LED (0-255)
    private int[] heat;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.intensity = params.get("intensity").getAsDouble();
        this.cooling = params.get("cooling").getAsDouble();
        this.sparking = params.get("sparking").getAsDouble();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.random = new Random();
        
        // Initialize heat map
        this.heat = new int[coords.getCount()];
        for (int i = 0; i < heat.length; i++) {
            heat[i] = 0;
        }
        
        System.out.println("FireEffect initialized:");
        System.out.println("  Intensity: " + intensity);
        System.out.println("  Cooling: " + cooling);
        System.out.println("  Sparking: " + sparking);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Step 1: Cool down every LED
        for (int i = 0; i < coords.getCount(); i++) {
            int cooldown = (int)(random.nextDouble() * cooling * 10);
            heat[i] = Math.max(0, heat[i] - cooldown);
        }
        
        // Step 2: Heat from bottom rises (diffusion)
        // For simplicity, we'll heat LEDs based on Y-coordinate
        // LEDs with higher Y (bottom of fox) are hotter
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            
            // Calculate base heat based on Y position
            // Y=600 (bottom) = hot, Y=0 (top) = cold
            double yFactor = led.getY() / 600.0; // 0.0 to 1.0
            
            // Add random sparking at the bottom (more controlled)
            if (yFactor > 0.7 && random.nextDouble() < sparking) {
                int spark = 80 + random.nextInt(60); // 80-140 (not too hot!)
                heat[i] = Math.min(180, heat[i] + (int)(spark * intensity));
            }
            
            // Add base heat based on Y position (much lower values)
            if (yFactor > 0.5) {
                // Bottom area: moderate heat
                int baseHeat = (int)((yFactor - 0.5) * 80 * intensity);
                heat[i] = Math.min(160, heat[i] + baseHeat);
            }
        }
        
        // Step 3: Convert heat to color
        for (int i = 0; i < coords.getCount(); i++) {
            int h = heat[i];
            
            if (h < 10) {
                // Almost black (no fire here)
                continue;
            }
            
            Color color;
            if (h < 85) {
                // Dark red to red (0-85)
                int r = (h * 3);
                color = new Color(r, 0, 0);
            } else if (h < 170) {
                // Red to orange (85-170)
                int g = ((h - 85) * 3);
                color = new Color(255, g, 0);
            } else {
                // Orange to yellow/white (170-255)
                int b = ((h - 170) * 3);
                color = new Color(255, 255, b);
            }
            
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
        return "Fire";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
