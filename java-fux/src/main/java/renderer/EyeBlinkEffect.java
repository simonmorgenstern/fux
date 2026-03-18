package renderer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.*;

public class EyeBlinkEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;
    private int fps;
    private Set<Integer> eyeLEDs;
    private List<Color> colors;
    private int currentColorIndex;
    private boolean isOn;
    private long lastBlinkFrame;
    private int blinkOnDuration;
    private int blinkOffDuration;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 20;
        
        // Define blink colors
        this.colors = new ArrayList<>();
        colors.add(new Color(255, 0, 0));      // Red
        colors.add(new Color(0, 255, 0));      // Green
        colors.add(new Color(0, 0, 255));      // Blue
        colors.add(new Color(255, 255, 0));    // Yellow
        colors.add(new Color(255, 0, 255));    // Magenta
        colors.add(new Color(0, 255, 255));    // Cyan
        colors.add(new Color(255, 128, 0));    // Orange
        
        this.currentColorIndex = 0;
        this.isOn = true;
        this.lastBlinkFrame = 0;
        
        // Calculate blink durations based on speed (frames)
        this.blinkOnDuration = (int)(fps * 0.8 / speed);   // On for 0.8s at speed=1
        this.blinkOffDuration = (int)(fps * 0.3 / speed);  // Off for 0.3s at speed=1
        
        // Load LED groups
        this.eyeLEDs = loadLEDGroup("eyes");
        
        System.out.println("EyeBlinkEffect initialized:");
        System.out.println("  Speed: " + speed);
        System.out.println("  Eye LEDs: " + eyeLEDs.size());
        System.out.println("  Colors: " + colors.size());
        System.out.println("  Blink on: " + blinkOnDuration + " frames");
        System.out.println("  Blink off: " + blinkOffDuration + " frames");
    }
    
    private Set<Integer> loadLEDGroup(String groupName) {
        Set<Integer> leds = new HashSet<>();
        try {
            // Read LED groups file
            Gson gson = new Gson();
            FileReader reader = new FileReader("../led-namer/led-groups.json");
            JsonArray groups = gson.fromJson(reader, JsonArray.class);
            reader.close();
            
            // Find the group
            for (int i = 0; i < groups.size(); i++) {
                JsonObject group = groups.get(i).getAsJsonObject();
                if (group.get("name").getAsString().equals(groupName)) {
                    JsonArray ranges = group.getAsJsonArray("ranges");
                    
                    // Parse ranges
                    for (int j = 0; j < ranges.size(); j++) {
                        JsonObject range = ranges.get(j).getAsJsonObject();
                        int start = range.get("start").getAsInt();
                        int end = range.get("end").getAsInt();
                        
                        for (int led = start; led <= end; led++) {
                            leds.add(led);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading LED group '" + groupName + "': " + e.getMessage());
        }
        return leds;
    }
    
    @Override
    public Map<Integer, Color> renderFrame(long frameNumber, double timeSeconds) {
        Map<Integer, Color> pixels = new HashMap<>();
        
        // Determine blink state
        long frameSinceLastBlink = frameNumber - lastBlinkFrame;
        int cycleDuration = blinkOnDuration + blinkOffDuration;
        
        if (frameSinceLastBlink >= cycleDuration) {
            // Start new blink cycle
            lastBlinkFrame = frameNumber;
            frameSinceLastBlink = 0;
            
            // Change color
            currentColorIndex = (currentColorIndex + 1) % colors.size();
        }
        
        // Update blink state
        isOn = frameSinceLastBlink < blinkOnDuration;
        
        // Render eyes
        if (isOn) {
            Color currentColor = colors.get(currentColorIndex);
            for (int led : eyeLEDs) {
                pixels.put(led, currentColor);
            }
        }
        // When off, return empty map (LEDs stay black)
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Eye Blink";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
