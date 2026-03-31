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
        
        if (frameNumber == 0) {
            System.out.println("FireEffect.renderFrame: coords.getCount()=" + coords.getCount() + ", heat.length=" + heat.length);
        }
        
        // Step 1: Cool down every LED (more aggressive cooling)
        for (int i = 0; i < coords.getCount(); i++) {
            // Stronger cooling: 0 to cooling * 50 (instead of * 20)
            int cooldown = (int)(random.nextDouble() * cooling * 50);
            heat[i] = Math.max(0, heat[i] - cooldown);
        }
        
        // Step 2: Heat diffusion using spatial proximity
        // Find nearby LEDs in 3D space (not just array index)
        int[] newHeat = new int[coords.getCount()];
        for (int i = 0; i < coords.getCount(); i++) {
            // Self-weighted heavily (heat tends to stay)
            double sum = heat[i] * 3.0;
            double count = 3.0;
            
            // Find spatially near LEDs (within 50 units)
            PixelCoordinate myCoord = coords.get(i);
            for (int j = 0; j < coords.getCount(); j++) {
                if (i == j) continue;
                
                PixelCoordinate otherCoord = coords.get(j);
                double dx = otherCoord.getX() - myCoord.getX();
                double dy = otherCoord.getY() - myCoord.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance < 50) {
                    // Closer = more weight
                    double weight = 1.0 - (distance / 50.0);
                    sum += heat[j] * weight;
                    count += weight;
                }
            }
            
            newHeat[i] = (int)(sum / count);
        }
        heat = newHeat;
        
        // Step 3: Add random sparks (more aggressive ignition)
        // Ignite bottom LEDs more, but allow sparks anywhere
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            
            // Base spark probability (much higher than before)
            double sparkChance = sparking * 1.5;  // Now ~70% per LED
            
            // Increase ignition chance near the "bottom" (high Y values)
            // Assuming Y ranges from 0-600, bottom is Y > 400
            if (led.getY() > 400) {
                sparkChance *= 2.0;  // Double the chance at the bottom
            }
            
            sparkChance = Math.min(1.0, sparkChance);  // Cap at 100%
            
            if (random.nextDouble() < sparkChance) {
                // Ignite this LED with more intensity
                int spark = 200 + random.nextInt(56);  // 200-255 (higher minimum heat)
                heat[i] = Math.min(255, heat[i] + (int)(spark * intensity * 1.5));
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
        
        if (frameNumber == 0) {
            System.out.println("FireEffect.renderFrame returned " + pixels.size() + " pixels");
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
