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
        
        // Convert parameters from 0-255 range to usable values
        // cooling: 0-255 (FastLED style) -> scale down to reasonable range
        double rawCooling = params.get("cooling").getAsDouble();
        this.cooling = rawCooling / 255.0;  // Now 0.0 to 1.0
        
        // sparking: 0-255 (FastLED style) -> convert to probability
        double rawSparking = params.get("sparking").getAsDouble();
        this.sparking = rawSparking / 255.0;  // Now 0.0 to 1.0
        
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.random = new Random();
        
        // Initialize heat map
        this.heat = new int[coords.getCount()];
        for (int i = 0; i < heat.length; i++) {
            heat[i] = 0;
        }
        
        System.out.println("FireEffect initialized:");
        System.out.println("  Intensity: " + intensity);
        System.out.println("  Cooling: " + cooling + " (raw: " + rawCooling + ")");
        System.out.println("  Sparking: " + sparking + " (raw: " + rawSparking + ")");
        System.out.println("  LED count: " + coords.getCount());
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Step 1: Cool down every LED
        for (int i = 0; i < coords.getCount(); i++) {
            // Random cooling amount (0 to cooling * 20)
            int cooldown = (int)(random.nextDouble() * cooling * 20);
            heat[i] = Math.max(0, heat[i] - cooldown);
        }
        
        // Step 2: Heat diffusion (heat rises)
        // Simplified: just blur heat slightly
        int[] newHeat = new int[coords.getCount()];
        for (int i = 0; i < coords.getCount(); i++) {
            // Average with neighbors (simplified - just +/-1 LED)
            int sum = heat[i] * 2;  // Weight center more
            int count = 2;
            
            if (i > 0) {
                sum += heat[i-1];
                count++;
            }
            if (i < coords.getCount() - 1) {
                sum += heat[i+1];
                count++;
            }
            
            newHeat[i] = sum / count;
        }
        heat = newHeat;
        
        // Step 3: Add random sparks
        // Randomly ignite LEDs across entire display
        for (int i = 0; i < coords.getCount(); i++) {
            // Use sparking probability for all pixels
            double sparkChance = sparking * 0.5;  // Scale to reasonable ignition rate
            
            if (random.nextDouble() < sparkChance) {
                // Ignite this LED
                int spark = 160 + random.nextInt(96);  // 160-255
                heat[i] = Math.min(255, heat[i] + (int)(spark * intensity));
            }
        }
        
        // Step 4: Convert heat to color
        for (int i = 0; i < coords.getCount(); i++) {
            int h = heat[i];
            
            Color color;
            if (h < 85) {
                // Black to dark red (0-85)
                int r = h * 3;
                color = new Color(Math.min(255, r), 0, 0);
            } else if (h < 170) {
                // Red to orange-yellow (85-170)
                int g = (h - 85) * 3;
                color = new Color(255, Math.min(255, g), 0);
            } else {
                // Orange-yellow to yellow-white (170-255)
                int b = (h - 170) * 3;
                color = new Color(255, 255, Math.min(255, b));
            }
            
            // Always render all pixels (including dark ones)
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
