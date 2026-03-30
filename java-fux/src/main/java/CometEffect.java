import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.*;

/**
 * Comet effect - fast-moving comet with fading trail
 */
public class CometEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;  // LEDs per second
    private int trailLength;
    private List<Color> colors;
    private int fps;
    private Random random;
    
    // State
    private int cometPosition;
    private Color currentColor;
    private boolean movingForward;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 40.0;
        this.trailLength = params.has("trail_length") ? params.get("trail_length").getAsInt() : 15;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 60;
        this.random = new Random();
        
        // Parse colors
        this.colors = new ArrayList<>();
        if (params.has("colors")) {
            JsonArray colorsArray = params.getAsJsonArray("colors");
            for (int i = 0; i < colorsArray.size(); i++) {
                String colorName = colorsArray.get(i).getAsString();
                colors.add(parseColor(colorName));
            }
        } else {
            // Default colors
            colors.add(new Color(255, 255, 255));  // White
            colors.add(new Color(0, 200, 255));    // Cyan
            colors.add(new Color(255, 100, 0));    // Orange
        }
        
        // Initialize state
        this.cometPosition = 0;
        this.currentColor = colors.get(random.nextInt(colors.size()));
        this.movingForward = true;
        
        System.out.println("CometEffect initialized:");
        System.out.println("  Speed: " + speed + " LEDs/sec");
        System.out.println("  Trail length: " + trailLength);
        System.out.println("  Colors: " + colors.size());
    }
    
    private Color parseColor(String name) {
        switch (name.toLowerCase()) {
            case "white": return Color.WHITE;
            case "cyan": return new Color(0, 200, 255);
            case "orange": return new Color(255, 100, 0);
            case "red": return new Color(255, 0, 0);
            case "green": return new Color(0, 255, 0);
            case "blue": return new Color(0, 100, 255);
            case "purple": return new Color(200, 0, 255);
            case "yellow": return new Color(255, 200, 0);
            default: return Color.WHITE;
        }
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Move comet
        int moveAmount = (int)(speed / fps);
        if (moveAmount < 1) moveAmount = 1;
        
        if (frameNumber % (fps / Math.max(1, (int)speed)) == 0) {
            if (movingForward) {
                cometPosition += moveAmount;
                if (cometPosition >= coords.getCount()) {
                    // Reached end, reverse and change color
                    movingForward = false;
                    cometPosition = coords.getCount() - 1;
                    currentColor = colors.get(random.nextInt(colors.size()));
                }
            } else {
                cometPosition -= moveAmount;
                if (cometPosition < 0) {
                    // Reached start, reverse and change color
                    movingForward = true;
                    cometPosition = 0;
                    currentColor = colors.get(random.nextInt(colors.size()));
                }
            }
        }
        
        // Render comet head and trail
        for (int i = 0; i < trailLength; i++) {
            int ledIndex;
            if (movingForward) {
                ledIndex = cometPosition - i;
            } else {
                ledIndex = cometPosition + i;
            }
            
            if (ledIndex >= 0 && ledIndex < coords.getCount()) {
                // Fade trail intensity
                double intensity = 1.0 - ((double)i / trailLength);
                intensity = Math.pow(intensity, 2.0);  // Non-linear fade
                
                Color fadedColor = new Color(
                    (int)(currentColor.getRed() * intensity),
                    (int)(currentColor.getGreen() * intensity),
                    (int)(currentColor.getBlue() * intensity)
                );
                
                pixels.put(ledIndex, fadedColor);
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
        return "Comet";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
