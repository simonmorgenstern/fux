import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Box Cascade effect - Wave flowing through the 8 boxes in sequence
 * Creates a cascading waterfall effect using the box layout
 */
public class BoxCascadeEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;  // Boxes per second
    private List<Set<Integer>> boxGroups;
    private List<Color> waveColors;
    private int fps;
    
    // State
    private double cascadePosition;  // 0.0 to 8.0 (which box is lit)
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 2.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.cascadePosition = 0.0;
        
        // Load all 8 box groups
        this.boxGroups = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Set<Integer> boxLEDs = LEDGroupLoader.loadGroup("boxes" + i);
            boxGroups.add(boxLEDs);
            System.out.println("  Box " + i + ": " + boxLEDs.size() + " LEDs");
        }
        
        // Create gradient colors for the cascade
        this.waveColors = new ArrayList<>();
        if (params.has("colors")) {
            JsonArray colorsArray = params.getAsJsonArray("colors");
            for (int i = 0; i < colorsArray.size(); i++) {
                String colorName = colorsArray.get(i).getAsString();
                waveColors.add(parseColor(colorName));
            }
        } else {
            // Default: blue to purple gradient
            waveColors.add(new Color(0, 100, 255));    // Deep blue
            waveColors.add(new Color(50, 150, 255));   // Blue
            waveColors.add(new Color(100, 200, 255));  // Light blue
            waveColors.add(new Color(150, 100, 255));  // Purple-blue
            waveColors.add(new Color(200, 50, 255));   // Purple
        }
        
        System.out.println("BoxCascadeEffect initialized:");
        System.out.println("  Speed: " + speed + " boxes/sec");
        System.out.println("  Total boxes: " + boxGroups.size());
        System.out.println("  Wave colors: " + waveColors.size());
    }
    
    private Color parseColor(String name) {
        switch (name.toLowerCase()) {
            case "blue": return new Color(0, 100, 255);
            case "cyan": return new Color(0, 200, 255);
            case "purple": return new Color(200, 50, 255);
            case "magenta": return new Color(255, 0, 255);
            case "red": return new Color(255, 0, 0);
            case "orange": return new Color(255, 128, 0);
            case "yellow": return new Color(255, 255, 0);
            case "green": return new Color(0, 255, 0);
            case "white": return Color.WHITE;
            default: return new Color(0, 150, 255);
        }
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Update cascade position
        double movement = speed / fps;
        cascadePosition += movement;
        
        // Loop back to start
        if (cascadePosition >= boxGroups.size()) {
            cascadePosition = 0.0;
        }
        
        // Render boxes with cascade wave
        // Each box gets a color based on distance from cascade position
        for (int boxIndex = 0; boxIndex < boxGroups.size(); boxIndex++) {
            Set<Integer> boxLEDs = boxGroups.get(boxIndex);
            
            // Calculate distance from cascade wave
            double distance = Math.abs(boxIndex - cascadePosition);
            
            // Wrap around for circular effect
            if (distance > boxGroups.size() / 2.0) {
                distance = boxGroups.size() - distance;
            }
            
            // If box is within wave range (2 boxes ahead and behind)
            if (distance < 3.0) {
                // Calculate color based on distance
                double colorPosition = distance / 3.0;  // 0.0 to 1.0
                
                // Get color from gradient
                int colorIndex = (int)(colorPosition * (waveColors.size() - 1));
                Color baseColor = waveColors.get(Math.min(colorIndex, waveColors.size() - 1));
                
                // Calculate intensity (brightest at wave center)
                double intensity = 1.0 - (distance / 3.0);
                intensity = Math.pow(intensity, 1.5);  // Non-linear for more punch
                
                // Apply to all LEDs in this box
                Color boxColor = new Color(
                    (int)(baseColor.getRed() * intensity),
                    (int)(baseColor.getGreen() * intensity),
                    (int)(baseColor.getBlue() * intensity)
                );
                
                for (int led : boxLEDs) {
                    pixels.put(led, boxColor);
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
        return "Box Cascade";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
