import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SparkleEffect implements Effect {
    private PixelCoordinates coords;
    private int density;
    private String colorMode;
    private int fadeFrames;
    private double brightness;
    private int fps;
    private Random random;
    
    // State: LED index -> remaining fade frames
    private Map<Integer, Integer> fadingLEDs;
    private Map<Integer, Color> ledColors;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.density = params.get("density").getAsInt();
        this.colorMode = params.get("color_mode").getAsString();
        this.fadeFrames = params.get("fade_frames").getAsInt();
        this.brightness = params.get("brightness").getAsDouble();
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 30;
        this.random = new Random();
        this.fadingLEDs = new HashMap<>();
        this.ledColors = new HashMap<>();
        
        System.out.println("SparkleEffect initialized:");
        System.out.println("  Density: " + density + " sparkles");
        System.out.println("  Color mode: " + colorMode);
        System.out.println("  Fade frames: " + fadeFrames);
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Add new sparkles randomly
        for (int i = 0; i < density; i++) {
            int ledIndex = random.nextInt(coords.getCount());
            
            // Choose color based on mode
            Color sparkleColor;
            if ("white".equals(colorMode)) {
                sparkleColor = Color.WHITE;
            } else if ("rainbow".equals(colorMode)) {
                float hue = random.nextFloat();
                sparkleColor = Color.getHSBColor(hue, 1.0f, (float)brightness);
            } else {
                // Single color mode (assume white as default)
                sparkleColor = Color.WHITE;
            }
            
            // Add to fading LEDs
            fadingLEDs.put(ledIndex, fadeFrames);
            ledColors.put(ledIndex, sparkleColor);
        }
        
        // Render all fading LEDs
        Map<Integer, Integer> updatedFading = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : fadingLEDs.entrySet()) {
            int ledIndex = entry.getKey();
            int remainingFrames = entry.getValue();
            
            if (remainingFrames > 0) {
                // Calculate fade intensity
                double fadeIntensity = (double)remainingFrames / fadeFrames;
                Color baseColor = ledColors.get(ledIndex);
                
                // Apply fade
                int r = (int)(baseColor.getRed() * fadeIntensity);
                int g = (int)(baseColor.getGreen() * fadeIntensity);
                int b = (int)(baseColor.getBlue() * fadeIntensity);
                
                if (r > 5 || g > 5 || b > 5) {
                    pixels.put(ledIndex, new Color(r, g, b));
                    updatedFading.put(ledIndex, remainingFrames - 1);
                }
            }
        }
        
        fadingLEDs = updatedFading;
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        fadingLEDs.clear();
        ledColors.clear();
    }
    
    @Override
    public String getName() {
        return "Sparkle";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
