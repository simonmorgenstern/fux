import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.FileReader;
import java.util.*;

public class BoxWaveEffect implements Effect {
    private PixelCoordinates coords;
    private double speed;
    private int fps;
    private List<Set<Integer>> boxGroups;
    private List<Color> waveColors;
    
    @Override
    public void initialize(JsonObject params, PixelCoordinates coords) {
        this.coords = coords;
        this.speed = params.has("speed") ? params.get("speed").getAsDouble() : 1.0;
        this.fps = params.has("fps") ? params.get("fps").getAsInt() : 25;
        
        // Load all box groups (boxes1 through boxes8)
        this.boxGroups = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Set<Integer> boxLEDs = loadLEDGroup("boxes" + i);
            boxGroups.add(boxLEDs);
            System.out.println("  Box " + i + ": " + boxLEDs.size() + " LEDs");
        }
        
        // Create a rainbow wave of colors
        this.waveColors = new ArrayList<>();
        waveColors.add(new Color(255, 0, 0));      // Red
        waveColors.add(new Color(255, 128, 0));    // Orange
        waveColors.add(new Color(255, 255, 0));    // Yellow
        waveColors.add(new Color(0, 255, 0));      // Green
        waveColors.add(new Color(0, 255, 255));    // Cyan
        waveColors.add(new Color(0, 128, 255));    // Light Blue
        waveColors.add(new Color(0, 0, 255));      // Blue
        waveColors.add(new Color(128, 0, 255));    // Purple
        waveColors.add(new Color(255, 0, 255));    // Magenta
        
        System.out.println("BoxWaveEffect initialized:");
        System.out.println("  Speed: " + speed);
        System.out.println("  Total boxes: " + boxGroups.size());
        System.out.println("  Wave colors: " + waveColors.size());
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
        
        // Calculate wave position
        // Wave moves through all 8 boxes, cycling continuously
        double cycleTime = 3.0 / speed;  // 3 seconds per full wave at speed=1
        double phase = (timeSeconds % cycleTime) / cycleTime;  // 0 to 1
        
        // Calculate which box should be lit (only ONE at a time)
        double wavePosition = phase * boxGroups.size();  // 0 to 8
        int currentBox = (int)Math.floor(wavePosition) % boxGroups.size();
        
        // Select color based on current box
        int colorIndex = currentBox % waveColors.size();
        Color boxColor = waveColors.get(colorIndex);
        
        // Set all LEDs in the current box
        Set<Integer> boxLEDs = boxGroups.get(currentBox);
        for (int led : boxLEDs) {
            pixels.put(led, boxColor);
        }
        
        return pixels;
    }
    
    @Override
    public void dispose() {
        // Nothing to clean up
    }
    
    @Override
    public String getName() {
        return "Box Wave";
    }
    
    @Override
    public int getFPS() {
        return fps;
    }
}
